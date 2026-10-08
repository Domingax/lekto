@file:Suppress("MagicNumber") // HTTP status codes are the protocol's own vocabulary; naming each adds nothing.

package app.lekto.integrations.webdav

import app.lekto.core.sync.SyncTargetException
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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
 * The WebDAV verbs the driver speaks, over `java.net.http` (JDK 11+) rather than
 * `HttpURLConnection`, which rejects the `PROPFIND` and `MKCOL` methods
 * (`ProtocolException: Invalid HTTP method`) — the two verbs WebDAV listings and
 * collection creation need.
 *
 * Every failure is a [SyncTargetException], and every method leaves a resource
 * untouched when it cannot complete, so a caller retries rather than seeing a
 * partial result. Credentials travel in the `Authorization` header only and are
 * never named in an error.
 */
internal class WebDavClient(connection: WebDavConnection, private val http: HttpClient = defaultClient()) {

    private val urls = WebDavUrls(connection.baseUrl)
    private val authorization = connection.authorization
    private val ensuredCollections = mutableSetOf<String>()
    private var baseEnsured = false

    /**
     * Creates the collection [relative] names, and the directory chain above it,
     * if it does not already exist. A collection that is already there is success.
     */
    fun ensureCollection(relative: String) {
        if (baseEnsured.not()) {
            createCollection("")
            baseEnsured = true
        }
        createCollection(relative)
    }

    /** The paths of the files directly under the collection [relative]. */
    fun propfind(relative: String): List<String> {
        ensureCollection(relative)
        val response = send(WebDavRequests.propfind(urls.collection(relative)), "list", relative)
        if (response.statusCode() != 207) throw failure("list", relative, response.statusCode())
        return WebDavMultistatus.hrefs(response.body().decodeToString())
            .mapNotNull { href -> urls.childOf(href, relative) }
    }

    /** Reads [relative], or `null` when the target holds no such resource. */
    fun read(relative: String): WebDavBlob? {
        val response = send(WebDavRequests.get(urls.resource(relative)), "read", relative)
        return when (response.statusCode()) {
            200 -> WebDavBlob(requireEtag(response, "read", relative), response.body())
            404 -> null
            else -> throw failure("read", relative, response.statusCode())
        }
    }

    /**
     * Writes [bytes] to [relative] under [condition]. A lost precondition is a
     * [WebDavWriteResult.PreconditionFailed], not an error: it is the outcome the
     * engine settles.
     */
    fun write(relative: String, bytes: ByteArray, condition: WriteCondition): WebDavWriteResult {
        val request = WebDavRequests.put(urls.resource(relative), bytes)
        WebDavRequests.conditions(condition).forEach(request::header)
        val response = send(request, "write", relative)
        return when (response.statusCode()) {
            in 200..299 -> WebDavWriteResult.Written(etag(response) ?: etagFromHead(relative))
            412 -> WebDavWriteResult.PreconditionFailed
            else -> throw failure("write", relative, response.statusCode())
        }
    }

    /** Removes [relative]; a resource that is already absent is success. */
    fun delete(relative: String) {
        val response = send(WebDavRequests.delete(urls.resource(relative)), "delete", relative)
        val status = response.statusCode()
        if (status !in 200..299 && status != 404) throw failure("delete", relative, status)
    }

    /** Creates [relative]'s collection chain, tolerating one that already exists. */
    private fun createCollection(relative: String) {
        if (relative in ensuredCollections) return
        val parent = relative.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) createCollection(parent)
        val status = send(WebDavRequests.mkcol(urls.collection(relative)), "create collection", relative).statusCode()
        if (status !in COLLECTION_SUCCESS) throw failure("create collection", relative, status)
        ensuredCollections += relative
    }

    /** Reads a written item's revision when the server did not return it on the `PUT`. */
    private fun etagFromHead(relative: String): String {
        val response = send(WebDavRequests.head(urls.resource(relative)), READ_REVISION, relative)
        if (response.statusCode() != 200) throw failure(READ_REVISION, relative, response.statusCode())
        return requireEtag(response, READ_REVISION, relative)
    }

    /** Runs [request], mapping a transport failure to a [SyncTargetException]. */
    private fun send(request: HttpRequest.Builder, what: String, path: String): HttpResponse<ByteArray> = try {
        http.send(
            request.timeout(TIMEOUT).header("Authorization", authorization).build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
    } catch (e: IOException) {
        throw SyncTargetException("could not $what '$path': the WebDAV server is unreachable", e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw SyncTargetException("could not $what '$path': the WebDAV request was interrupted", e)
    }

    private fun etag(response: HttpResponse<ByteArray>): String? =
        response.headers().firstValue("ETag").orElse(null)?.takeIf { it.isNotBlank() }

    private fun requireEtag(response: HttpResponse<ByteArray>, what: String, path: String): String =
        etag(response) ?: throw SyncTargetException("could not $what '$path': the server returned no ETag")

    private fun failure(what: String, path: String, status: Int): SyncTargetException =
        SyncTargetException("could not $what '$path': the WebDAV server answered HTTP $status")

    private companion object {
        /** A collection that already exists answers 405 (or 200 on some servers), not 201. */
        val COLLECTION_SUCCESS: Set<Int> = setOf(200, 201, 405)

        /** The action label an error carries when the driver reads back a written revision. */
        const val READ_REVISION: String = "read the revision"

        val TIMEOUT: Duration = Duration.ofSeconds(30)

        fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }
}
