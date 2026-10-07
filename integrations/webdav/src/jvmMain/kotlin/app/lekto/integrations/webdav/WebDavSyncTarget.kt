package app.lekto.integrations.webdav

import app.lekto.core.sync.Revision
import app.lekto.core.sync.SyncCapabilities
import app.lekto.core.sync.SyncItem
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
 * live record or a tombstone), and `<root>/attachments/<base64url(id)>.bin`
 * holds one book original.
 *
 * Capabilities are reported honestly: WebDAV `PUT` honours `If-Match` and
 * `If-None-Match` (RFC 4918), so **conditional writes are supported**; RFC 6578
 * `sync-collection` is server-dependent, so **no change cursor** is claimed and
 * the engine lists the whole collection instead. A driver that claimed a
 * capability it lacked fails the shared [SyncTarget] contract, which only adds
 * the cases a claimed capability demands.
 */
class WebDavSyncTarget(baseUrl: String, username: String, password: String) : SyncTarget {

    private val client = WebDavClient(WebDavConnection(baseUrl, username, password))

    override fun capabilities(): SyncCapabilities = SyncCapabilities(conditionalWrites = true, changeCursor = false)

    override suspend fun list(): List<SyncItem> =
        client.propfind(WebDavNames.RECORDS).filter(WebDavNames::isRecord).mapNotNull(::readItem)

    override suspend fun get(id: String): SyncItem? = readItem(WebDavNames.recordPath(id))

    override suspend fun put(version: VersionedRecord, expected: Revision?): WriteOutcome {
        client.ensureCollection(WebDavNames.RECORDS)
        val condition = expected?.let { WriteCondition.MatchesRevision(it.value) } ?: WriteCondition.CreateOnly
        val path = WebDavNames.recordPath(version.id)
        return when (val result = client.write(path, WebDavRecords.encode(version), condition)) {
            is WebDavWriteResult.Written -> {
                if (version is VersionedRecord.Deleted) client.delete(WebDavNames.attachmentPath(version.id))
                WriteOutcome.Written(Revision(result.etag))
            }

            WebDavWriteResult.PreconditionFailed -> WriteOutcome.Conflicted(get(version.id))
        }
    }

    override suspend fun attachmentIds(): Set<String> =
        client.propfind(WebDavNames.ATTACHMENTS).mapNotNull(WebDavNames::idFromAttachment).toSet()

    override suspend fun attachment(id: String): ByteArray? = client.read(WebDavNames.attachmentPath(id))?.bytes

    override suspend fun putAttachment(id: String, bytes: ByteArray) {
        client.ensureCollection(WebDavNames.ATTACHMENTS)
        client.write(WebDavNames.attachmentPath(id), bytes, WriteCondition.Unconditional)
    }

    private fun readItem(path: String): SyncItem? {
        val blob = client.read(path) ?: return null
        return SyncItem(decode(blob.bytes, path), Revision(blob.etag))
    }

    private fun decode(bytes: ByteArray, path: String): VersionedRecord = try {
        WebDavRecords.decode(bytes)
    } catch (e: SerializationException) {
        throw SyncTargetException("could not read '$path': the remote item is not a vault record", e)
    }
}
