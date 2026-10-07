package app.lekto.core.sync

import app.lekto.core.vault.Tombstone

/**
 * The engine's own store of tombstones (ADR-0015): the deletions this device
 * must propagate, kept past the life of the record so a sync does not resurrect
 * what the user removed.
 *
 * It is deliberately separate from the [VaultStore][app.lekto.core.vault.VaultStore]:
 * the vault holds only live user records and is exported whole (ADR-0003), while
 * a tombstone is a comparison key the engine carries and the vault must never
 * treat as content. The layout belongs to sync, not to the on-disk record format
 * (ADR-0015, "the engine's own store, whose layout … a later engine ticket
 * decides").
 */
interface TombstoneStore {

    /** Records [tombstone], replacing any earlier tombstone for the same id. */
    fun put(tombstone: Tombstone)

    /** Drops [id]'s tombstone; an absent one is not an error. */
    fun remove(id: String)

    /** [id]'s tombstone, or `null` when this device holds none. */
    fun get(id: String): Tombstone?

    /** Every tombstone this device holds. */
    fun all(): List<Tombstone>
}
