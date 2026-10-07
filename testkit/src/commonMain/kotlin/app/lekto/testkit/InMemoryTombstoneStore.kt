package app.lekto.testkit

import app.lekto.core.sync.TombstoneStore
import app.lekto.core.vault.Tombstone

/**
 * The in-memory [TombstoneStore]: the fake the engine's deletion tests run
 * against, so a deletion can be propagated without a disk (docs/testing.md,
 * "Test levels"). The real [app.lekto.core.sync.JsonTombstoneStore] runs the
 * same contract in `core/commonTest`, so the two cannot drift.
 */
class InMemoryTombstoneStore : TombstoneStore {

    private val byId = linkedMapOf<String, Tombstone>()

    override fun put(tombstone: Tombstone) {
        byId[tombstone.id] = tombstone
    }

    override fun remove(id: String) {
        byId.remove(id)
    }

    override fun get(id: String): Tombstone? = byId[id]

    override fun all(): List<Tombstone> = byId.values.sortedBy { it.id }
}
