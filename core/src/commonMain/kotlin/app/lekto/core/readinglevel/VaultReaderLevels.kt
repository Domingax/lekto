package app.lekto.core.readinglevel

import app.lekto.core.CefrLevel
import app.lekto.core.Seams
import app.lekto.core.sync.TombstoneStore
import app.lekto.core.text.baseLanguage
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.Tombstone
import app.lekto.core.vault.VaultStore

/**
 * The [ReaderLevels] over a [VaultStore] (ADR-0030): a declared level is a vault
 * record of kind [ReaderLevelRecord.KIND] and survives a restart because the
 * vault is on disk, and it travels in an export and a sync like any record.
 *
 * The composition root supplies the installation's stable [deviceId] and the
 * [Seams] every record is stamped with (ADR-0003). A record that cannot be read
 * is skipped rather than bringing the list down, exactly as the vocabulary
 * treats a bad record.
 *
 * A [clearLevel] removes the record **and** leaves a [Tombstone] in the engine's
 * own store (ADR-0015), exactly as a vocabulary delete does, so a cleared level
 * is synchronised rather than copied back from a device that has not seen the
 * change. The store is the same one the sync engine reads, so it is the
 * composition root's job to point both at it.
 */
class VaultReaderLevels(
    private val vault: VaultStore,
    private val tombstones: TombstoneStore,
    private val seams: Seams,
    private val deviceId: DeviceId,
) : ReaderLevels {

    override fun levelFor(language: String?): CefrLevel? = language?.takeIf { it.isNotBlank() }
        ?.let { code -> ReaderLevelRecord.idOf(code) }
        ?.let(vault::get)
        ?.let { record -> ReaderLevelRecord.read(record)?.level }

    override fun levels(): Map<String, CefrLevel> = vault.all()
        .filter { record -> record.kind == ReaderLevelRecord.KIND }
        .mapNotNull(ReaderLevelRecord::read)
        .associate { level -> baseLanguage(level.language) to level.level }

    override fun setLevel(language: String, level: CefrLevel) {
        if (language.isBlank()) return
        vault.put(ReaderLevelRecord.of(language, level, seams.clock.now(), deviceId))
    }

    override fun clearLevel(language: String) {
        if (language.isBlank()) return
        val id = ReaderLevelRecord.idOf(language)
        if (vault.get(id) == null) return
        vault.remove(id)
        tombstones.put(Tombstone(id = id, updatedAt = seams.clock.now(), deviceId = deviceId))
    }
}
