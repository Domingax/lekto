package app.lekto.core.vocabulary

import app.lekto.core.Seams
import app.lekto.core.sync.TombstoneStore
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.Tombstone
import app.lekto.core.vault.VaultStore

/**
 * The [Vocabulary] over a [VaultStore] (issue #22): a saved word is a vault
 * record of kind [VocabularyRecord.KIND] and survives a restart because the
 * vault is on disk.
 *
 * The composition root supplies the installation's stable [deviceId] and the
 * [Seams] every record is stamped with (ADR-0003); the domain reaches for
 * neither a platform nor a global. A record that cannot be read is skipped
 * rather than bringing the list down, exactly as the library treats a bad book
 * record.
 *
 * A [delete] removes the record **and** leaves a [Tombstone] in the engine's own
 * store (ADR-0015), so the deletion survives a sync instead of being copied back
 * from a device that has not seen it. The store is the same one the sync engine
 * reads, so it is the composition root's job to point both at it.
 */
class VaultVocabulary(
    private val vault: VaultStore,
    private val tombstones: TombstoneStore,
    private val seams: Seams,
    private val deviceId: DeviceId,
) : Vocabulary {

    override fun all(): List<VocabularyEntry> = vault.all()
        .filter { record -> record.kind == VocabularyRecord.KIND }
        .mapNotNull { record -> runCatching { VocabularyRecord.entryOf(record) }.getOrNull() }

    override fun entryFor(key: WordKey): VocabularyEntry? = vault.get(VocabularyRecord.idOf(key))
        ?.let { record -> runCatching { VocabularyRecord.entryOf(record) }.getOrNull() }

    override fun save(entry: VocabularyEntry) {
        vault.put(VocabularyRecord.of(entry, seams.clock.now(), deviceId))
    }

    override fun delete(key: WordKey) {
        val id = VocabularyRecord.idOf(key)
        if (vault.get(id) == null) return
        vault.remove(id)
        tombstones.put(Tombstone(id = id, updatedAt = seams.clock.now(), deviceId = deviceId))
    }
}
