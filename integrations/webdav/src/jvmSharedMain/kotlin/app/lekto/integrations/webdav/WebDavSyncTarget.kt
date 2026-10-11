package app.lekto.integrations.webdav

import app.lekto.core.sync.Revision
import app.lekto.core.sync.SyncCapabilities
import app.lekto.core.sync.SyncItem
import app.lekto.core.sync.SyncListing
import app.lekto.core.sync.SyncTarget
import app.lekto.core.sync.SyncTargetException
import app.lekto.core.sync.WriteOutcome
import app.lekto.core.vault.VersionedRecord
import kotlinx.serialization.SerializationException

/**
 * The first real [SyncTarget] driver (ADR-0009; ticket #27): it maps the vault's
 * items onto a WebDAV collection, so a self-hosted Nextcloud, ownCloud, Synology
 * or Apache `mod_dav` server carries the vault with no Lekto server in between.
 *
 * The remote layout is the driver's (ADR-0024), and lives in [WebDavNames]:
 * `<root>/records/<base64url(id)>.json` holds one [VersionedRecord]'s JSON (a
 * live record or a tombstone), and `<root>/attachments/<base64url(id)>.data`
 * holds one book original.
 *
 * Capabilities are reported honestly: a create conditions with HTTP
 * `If-None-Match: *` and an update with WebDAV's `If` header (RFC 4918), falling
 * back to HTTP `If-Match` when a refused DAV `If` leaves the revision unmoved —
 * a server that does not implement the DAV `If` (Koofr answers 412 to it however
 * correct the revision is — issue #133) — so **conditional writes are
 * supported**; RFC 6578 `sync-collection` is
 * server-dependent, so **no change cursor** is claimed and the engine lists the
 * whole collection — but reads the body only of items whose ETag moved, so a
 * steady-state sync is one `PROPFIND` plus the changed records (ADR-0027). A
 * driver that claimed a capability it lacked fails the shared [SyncTarget]
 * contract, which only adds the cases a claimed capability demands.
 */
@Suppress("TooManyFunctions") // One method per WebDAV operation plus its small write helper; the seam is the protocol.
class WebDavSyncTarget(baseUrl: String, username: String, password: String) : SyncTarget {

    private val client = WebDavClient(WebDavConnection(baseUrl, username, password))

    override fun capabilities(): SyncCapabilities = SyncCapabilities(conditionalWrites = true, changeCursor = false)

    /**
     * Lists the records collection, reading the body only of items that are new
     * or whose ETag moved since [known] (ADR-0027): an unchanged item costs a
     * `PROPFIND` line, not a `GET`. A body that is read is tagged with the ETag
     * the listing reported, so a later listing compares like with like.
     */
    override suspend fun list(known: Map<String, Revision>): SyncListing {
        val items = mutableListOf<SyncItem>()
        val unchanged = mutableSetOf<String>()
        client.propfind(WebDavNames.RECORDS)
            .filter { WebDavNames.isRecord(it.path) }
            .forEach { resource ->
                val id = WebDavNames.idFromRecord(resource.path) ?: return@forEach
                val revision = resource.etag?.takeIf { it.isNotBlank() }?.let(::Revision)
                if (revision != null && known[id] == revision) {
                    unchanged += id
                } else {
                    readItem(resource.path, revision)?.let { items += it }
                }
            }
        return SyncListing(items, unchanged)
    }

    override suspend fun get(id: String): SyncItem? = readItem(WebDavNames.recordPath(id), null)

    override suspend fun delete(id: String) {
        client.delete(WebDavNames.recordPath(id))
        client.delete(WebDavNames.attachmentPath(id))
    }

    override suspend fun put(version: VersionedRecord, expected: Revision?): WriteOutcome {
        client.ensureCollection(WebDavNames.RECORDS)
        val path = WebDavNames.recordPath(version.id)
        val bytes = WebDavRecords.encode(version)
        // A create is `If-None-Match: *`, honoured by every server including Koofr.
        val written = expected?.let { revision -> update(path, version.id, bytes, revision) }
            ?: client.write(path, bytes, WriteCondition.CreateOnly)
        return when (written) {
            is WebDavWriteResult.Written -> {
                // A tombstone says the id is gone, so its original must not linger (ADR-0016).
                if (version is VersionedRecord.Deleted) client.delete(WebDavNames.attachmentPath(version.id))
                WriteOutcome.Written(Revision(written.etag))
            }

            WebDavWriteResult.PreconditionFailed -> WriteOutcome.Conflicted(get(version.id))
        }
    }

    /**
     * A conditional update: the DAV `If` first, whose weak comparison tolerates an
     * Apache fresh ETag. A refusal is a lost race only when the revision moved —
     * when it did not, the server does not honour the DAV `If` (Koofr answers 412
     * however correct the revision is, issue #133) — so the write is retried with
     * HTTP `If-Match`. That is a strong comparison, so a revision that really moved
     * fails it too: the fallback can never turn a lost race into a blind overwrite.
     */
    private suspend fun update(path: String, id: String, bytes: ByteArray, expected: Revision): WebDavWriteResult {
        val davIf = client.write(path, bytes, WriteCondition.WeakMatch(expected.value))
        return when {
            davIf is WebDavWriteResult.Written -> davIf

            // The DAV `If` was refused. A revision that moved is a lost race, settled
            // as a conflict; only a revision that did not move means the server does
            // not honour the DAV `If` (Koofr), so the write is retried with `If-Match`.
            get(id)?.revision != expected -> WebDavWriteResult.PreconditionFailed

            else -> client.write(path, bytes, WriteCondition.StrongMatch(expected.value))
        }
    }

    override suspend fun attachmentIds(): Set<String> =
        client.propfind(WebDavNames.ATTACHMENTS).mapNotNull { attachmentId(it.path) }.toSet()

    /**
     * The id a listed attachment names, migrating a legacy `.bin` file to `.data`
     * first (issue #116). Koofr refuses to serve `.bin`, so an original a
     * pre-`.data` client uploaded could never be read; a `MOVE` renames it in
     * place, recovering the original without re-uploading it. A server that
     * refuses the `MOVE` leaves the file ignored, exactly as it was before.
     */
    private fun attachmentId(path: String): String? = WebDavNames.idFromAttachment(path)
        ?: WebDavNames.idFromLegacyAttachment(path)?.takeIf { id ->
            runCatching { client.move(path, WebDavNames.attachmentPath(id)) }.isSuccess
        }

    override suspend fun attachment(id: String): ByteArray? = client.read(WebDavNames.attachmentPath(id))?.bytes

    override suspend fun putAttachment(id: String, bytes: ByteArray) {
        client.ensureCollection(WebDavNames.ATTACHMENTS)
        client.write(WebDavNames.attachmentPath(id), bytes, WriteCondition.Unconditional)
    }

    private fun readItem(path: String, revision: Revision?): SyncItem? {
        val blob = client.read(path) ?: return null
        return SyncItem(decode(blob.bytes, path), revision ?: Revision(blob.etag))
    }

    private fun decode(bytes: ByteArray, path: String): VersionedRecord = try {
        WebDavRecords.decode(bytes)
    } catch (e: SerializationException) {
        throw SyncTargetException("could not read '$path': the remote item is not a vault record", e)
    }
}
