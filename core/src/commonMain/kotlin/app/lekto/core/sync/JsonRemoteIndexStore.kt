package app.lekto.core.sync

import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultFileSystem
import app.lekto.core.vault.VersionedRecord
import kotlinx.serialization.Serializable

/** One item as it is written down: its id, the driver's revision, and its body. */
@Serializable
private data class RemoteIndexEntry(val id: String, val revision: String, val version: VersionedRecord)

/** The one document a [JsonRemoteIndexStore] carries, so the format has a name. */
@Serializable
private data class RemoteIndexDocument(val entries: List<RemoteIndexEntry> = emptyList())

/**
 * The default [RemoteIndexStore] (ADR-0027): the last item seen per id as one
 * JSON document over any [VaultFileSystem], written atomically and portably
 * behind the same byte seam as the vault and the tombstones (ADR-0010).
 *
 * It is rooted at its **own** store beside `tombstones.json`, never the vault
 * (ADR-0005): the memory is sync bookkeeping the engine can rebuild from one
 * listing, not user-authored data. A document that cannot be read — absent,
 * corrupt, or written by a shape this build does not know — reads as empty, so
 * the engine falls back to a full listing rather than failing a sync over a
 * cache.
 */
class JsonRemoteIndexStore(private val files: VaultFileSystem) : RemoteIndexStore {

    override fun all(): List<SyncItem> {
        val bytes = files.read(FILE) ?: return emptyList()
        return runCatching {
            val document = VaultCodec.json.decodeFromString(RemoteIndexDocument.serializer(), bytes.decodeToString())
            document.entries.map { SyncItem(it.version, Revision(it.revision)) }
        }.getOrDefault(emptyList())
    }

    override fun replaceAll(items: List<SyncItem>) {
        val document = RemoteIndexDocument(
            items.sortedBy { it.id }.map { RemoteIndexEntry(it.id, it.revision.value, it.version) },
        )
        val text = VaultCodec.json.encodeToString(RemoteIndexDocument.serializer(), document)
        files.writeAtomically(FILE, text.encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val FILE: String = "remote-index.json"
    }
}
