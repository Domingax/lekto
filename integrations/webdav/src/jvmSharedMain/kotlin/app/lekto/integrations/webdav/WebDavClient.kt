@file:Suppress("MagicNumber") // HTTP status codes are the protocol's own vocabulary; naming each adds nothing.

package app.lekto.integrations.webdav

import app.lekto.core.sync.SyncTargetException
import java.io.IOException

/** How a write is conditioned on an item's current revision. */
internal sealed interface WriteCondition {
    /** Fail unless the target holds no item at the path. */
    data object CreateOnly : WriteCondition

    /** Fail unless the item's revision is [revision]. */
    data class MatchesRevision(val revision: String) : WriteCondition

    /** Write regardless of the current revision. */
    data object Unconditional : WriteCondition
}

/** A remote resource's bytes together with the revision it was read at. */
internal data class WebDavBlob(val etag: String, val bytes: ByteArray)

/** The outcome of a conditional write. */
internal sealed interface WebDavWriteResult {
    /** The write landed; [etag] is the item's new revision. */
    data class Written(val etag: String) : WebDavWriteResult

    /** The item's revision moved since the write was conditioned on; nothing was written. */
    data object PreconditionFailed : WebDavWriteResult
}

/**
 * The WebDAV verbs the driver speaks, over the platform's [WebDavTransport]
 * (ADR-0026). The transport is the one platform call — `java.net.http` on the
 * JVM, OkHttp on Android — chosen because `HttpURLConnection` rejects the
 * `PROPFIND` and `MKCOL` methods (`ProtocolException: Invalid HTTP method`), the
 * two verbs WebDAV listings and collection creation need.
 *
 * Every failure is a [SyncTargetException], and every method leaves a resource
 * untouched when it cannot complete, so a caller retries rather than seeing a
 * partial result. Credentials travel in the `Authorization` header only and are
 * never named in an error.
 */
@Suppress("TooManyFunctions") // One method per WebDAV verb and its small helpers; the seam is the protocol.
internal class WebDavClient(connection: WebDavConnection) {

    private val urls = WebDavUrls(connection.baseUrl)
    private val authorization = connection.authorization
    private val transport: WebDavTransport = defaultWebDavTransport()
    private val ensuredCollections = mutableSetOf<String>()
    private var baseEnsured = false

    /**
     * Creates the collection [relative] names, and the directory chain above it,
     * if it does not already exist. A collection that is already there is success.
     */
    fun ensureCollection(relative: String) {
        if (baseEnsured.not()) {
            requireBase()
            baseEnsured = true
        }
        createCollection(relative)
    }

    /**
     * Ensures the collection the user points at exists, creating it only when the
     * server read it as absent. A server that will not let it be created is
     * reported as an address that is not a usable collection — the honest message
     * for a wrong URL or an account whose plan has WebDAV disabled — rather than
     * as a failed `MKCOL`.
     */
    private fun requireBase() {
        if (exists("")) return
        val status = send(WebDavRequests.mkcol(urls.collection("")), CREATE_COLLECTION, "").status
        if (status !in COLLECTION_SUCCESS) {
            throw SyncTargetException("the WebDAV server did not recognise the address as a collection (HTTP $status)")
        }
    }

    /** The paths of the files directly under the collection [relative]. */
    fun propfind(relative: String): List<String> {
        ensureCollection(relative)
        val response = send(WebDavRequests.propfind(urls.collection(relative)), "list", relative)
        if (response.status != 207) throw failure("list", relative, response.status)
        return WebDavMultistatus.hrefs(response.body.decodeToString())
            .mapNotNull { href -> urls.childOf(href, relative) }
    }

    /** Reads [relative], or `null` when the target holds no such resource. */
    fun read(relative: String): WebDavBlob? {
        val response = send(WebDavRequests.get(urls.resource(relative)), "read", relative)
        return when (response.status) {
            200 -> WebDavBlob(requireEtag(response, "read", relative), response.body)
            404 -> null
            else -> throw failure("read", relative, response.status)
        }
    }

    /**
     * Writes [bytes] to [relative] under [condition]. A lost precondition is a
     * [WebDavWriteResult.PreconditionFailed], not an error: it is the outcome the
     * engine settles.
     */
    fun write(relative: String, bytes: ByteArray, condition: WriteCondition): WebDavWriteResult {
        val request = WebDavRequests.put(urls.resource(relative), bytes)
        val conditioned = request.copy(headers = request.headers + WebDavRequests.conditions(condition))
        val response = send(conditioned, "write", relative)
        return when (response.status) {
            in 200..299 -> WebDavWriteResult.Written(response.etag ?: etagFromHead(relative))
            412 -> WebDavWriteResult.PreconditionFailed
            else -> throw failure("write", relative, response.status)
        }
    }

    /** Removes [relative]; a resource that is already absent is success. */
    fun delete(relative: String) {
        val status = send(WebDavRequests.delete(urls.resource(relative)), "delete", relative).status
        if (status !in 200..299 && status != 404) throw failure("delete", relative, status)
    }

    /**
     * Renames [from] to [to] with a `MOVE`; a resource that is already gone is
     * success, so a migration can be re-run without failing.
     */
    fun move(from: String, to: String) {
        val request = WebDavRequests.move(urls.resource(from), urls.resource(to))
        val status = send(request, "rename", from).status
        if (status !in 200..299 && status != 404) throw failure("rename", from, status)
    }

    /** Creates [relative]'s collection chain, if it is not already there. */
    private fun createCollection(relative: String) {
        if (relative in ensuredCollections) return
        val parent = relative.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) createCollection(parent)
        if (!exists(relative)) {
            val status = send(WebDavRequests.mkcol(urls.collection(relative)), CREATE_COLLECTION, relative).status
            if (status !in COLLECTION_SUCCESS) throw failure(CREATE_COLLECTION, relative, status)
        }
        ensuredCollections += relative
    }

    /**
     * Whether the collection [relative] already exists. It is read with a
     * zero-depth `PROPFIND` rather than inferred from a `MKCOL`: servers answer
     * different statuses for a collection that already exists — RFC 4918 says
     * `405`, but Infomaniak kDrive answers `404` even for the drive root — so a
     * `MKCOL` is only ever sent for a collection read as absent.
     */
    private fun exists(relative: String): Boolean {
        val response = send(WebDavRequests.propfind(urls.collection(relative), depth = 0), "check", relative)
        return when (response.status) {
            207 -> true
            404 -> false
            else -> throw failure("check", relative, response.status)
        }
    }

    /** Reads a written item's revision when the server did not return it on the `PUT`. */
    private fun etagFromHead(relative: String): String {
        val response = send(WebDavRequests.head(urls.resource(relative)), READ_REVISION, relative)
        if (response.status != 200) throw failure(READ_REVISION, relative, response.status)
        return requireEtag(response, READ_REVISION, relative)
    }

    /** Runs [request] through the transport, mapping an I/O failure to a [SyncTargetException]. */
    private fun send(request: WebDavRequest, what: String, path: String): WebDavResponse = try {
        transport.execute(request.copy(headers = request.headers + ("Authorization" to authorization)))
    } catch (e: IOException) {
        throw SyncTargetException("could not $what '$path': the WebDAV server is unreachable", e)
    }

    private fun requireEtag(response: WebDavResponse, what: String, path: String): String =
        response.etag ?: throw SyncTargetException("could not $what '$path': the server returned no ETag")

    private fun failure(what: String, path: String, status: Int): SyncTargetException =
        SyncTargetException("could not $what '$path': the WebDAV server answered HTTP $status")

    private companion object {
        /** The action label a collection-creation error carries. */
        const val CREATE_COLLECTION: String = "create collection"

        /**
         * A collection that races into existence between the existence read and
         * the `MKCOL` answers 405 (or 200 on some servers); creating it answers
         * 201.
         */
        val COLLECTION_SUCCESS: Set<Int> = setOf(200, 201, 405)

        /** The action label an error carries when the driver reads back a written revision. */
        const val READ_REVISION: String = "read the revision"
    }
}
