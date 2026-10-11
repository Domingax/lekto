package app.lekto.core.sync

import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultFileSystem
import app.lekto.core.vault.VersionedRecord
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One item as it is written down: its id, the driver's revision, and its body. */
@Serializable
private data class RemoteIndexEntry(val id: String, val revision: String, val version: VersionedRecord)

/**
 * The one document a [JsonRemoteIndexStore] carries, so the format has a name.
 * The last sync time is written as its ISO-8601 string, the form the rest of the
 * vault's JSON uses (ADR-0003).
 */
@Serializable
private data class RemoteIndexDocument(
    val entries: List<RemoteIndexEntry> = emptyList(),
    val lastSyncedAt: String? = null,
)

/**
 * The default [RemoteIndexStore] (ADR-0027): the last item seen per id, and when
 * this device last completed a sync, as one JSON document over any
 * [VaultFileSystem], written atomically and portably behind the same byte seam as
 * the vault and the tombstones (ADR-0010).
 *
 * It is rooted at its **own** store beside `tombstones.json`, never the vault
 * (ADR-0005): the memory is sync bookkeeping the engine can rebuild from one
 * listing, not user-authored data. A document that cannot be read — absent,
 * corrupt, or written by a shape this build does not know — reads as empty, so
 * the engine falls back to a full listing rather than failing a sync over a
 * cache.
 */
class JsonRemoteIndexStore(private val files: VaultFileSystem) : RemoteIndexStore {

    override fun all(): List<SyncItem> = read().entries.map { SyncItem(it.version, Revision(it.revision)) }

    override fun replaceAll(items: List<SyncItem>) {
        write(
            read().copy(
                entries = items.sortedBy { it.id }.map { RemoteIndexEntry(it.id, it.revision.value, it.version) },
            ),
        )
    }

    override fun lastSyncedAt(): Instant? = read().lastSyncedAt?.let { text ->
        runCatching { Instant.parse(text) }.getOrNull()
    }

    override fun markSynced(at: Instant) {
        write(read().copy(lastSyncedAt = at.toString()))
    }

    /** The document, or an empty one when it is absent or unreadable. */
    private fun read(): RemoteIndexDocument {
        val bytes = files.read(FILE) ?: return RemoteIndexDocument()
        return runCatching {
            VaultCodec.json.decodeFromString(RemoteIndexDocument.serializer(), bytes.decodeToString())
        }.getOrDefault(RemoteIndexDocument())
    }

    private fun write(document: RemoteIndexDocument) {
        val text = VaultCodec.json.encodeToString(RemoteIndexDocument.serializer(), document)
        files.writeAtomically(FILE, text.encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val FILE: String = "remote-index.json"
    }
}
