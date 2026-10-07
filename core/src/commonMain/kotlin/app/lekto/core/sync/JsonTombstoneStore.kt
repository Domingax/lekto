package app.lekto.core.sync

import app.lekto.core.vault.Tombstone
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.Serializable

/** The one document a [JsonTombstoneStore] carries, so the format has a name. */
@Serializable
private data class TombstoneDocument(val tombstones: List<Tombstone> = emptyList())

/**
 * The default [TombstoneStore]: one JSON document over any [VaultFileSystem], so
 * the engine's deletion memory is written atomically and portably behind the
 * same byte seam as the vault (ADR-0010).
 *
 * It is rooted at its **own** store, not the vault's: a vault holds one file per
 * record (ADR-0003) and would read a sibling `tombstones.json` as a record, so
 * the composition root gives this store its own directory. The document is
 * rewritten whole on every change, which is cheap because a tombstone is only an
 * id and an envelope.
 */
class JsonTombstoneStore(private val files: VaultFileSystem) : TombstoneStore {

    override fun put(tombstone: Tombstone) {
        mutate { byId -> byId + (tombstone.id to tombstone) }
    }

    override fun remove(id: String) {
        mutate { byId -> byId - id }
    }

    override fun get(id: String): Tombstone? = read().firstOrNull { it.id == id }

    override fun all(): List<Tombstone> = read()

    /** Applies [change] to the id-keyed tombstones and rewrites the document. */
    private fun mutate(change: (Map<String, Tombstone>) -> Map<String, Tombstone>) {
        write(change(read().associateBy { it.id }))
    }

    private fun read(): List<Tombstone> {
        val bytes = files.read(FILE) ?: return emptyList()
        val document = VaultCodec.json.decodeFromString(TombstoneDocument.serializer(), bytes.decodeToString())
        return document.tombstones
    }

    private fun write(byId: Map<String, Tombstone>) {
        val document = TombstoneDocument(byId.values.sortedBy { it.id })
        val text = VaultCodec.json.encodeToString(TombstoneDocument.serializer(), document)
        files.writeAtomically(FILE, text.encodeToByteArray())
    }

    private companion object {
        const val FILE = "tombstones.json"
    }
}
