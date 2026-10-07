package app.lekto.testkit

import app.lekto.core.sync.TombstoneStore

/**
 * The specification of the [TombstoneStore] seam, as executable cases (ticket
 * #26; ADR-0015). It is framework-free, so the in-memory store runs it in
 * `core/commonTest` and the JSON store runs the same cases over an in-memory
 * filesystem — the same behaviours, so the two cannot drift (docs/testing.md,
 * "Test levels").
 *
 * Each case builds a fresh store from [newStore], so cases cannot leak state.
 */
class TombstoneStoreContract(private val newStore: () -> TombstoneStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unknown id reads as null") {
            expectTrue(newStore().get("absent") == null, "an absent tombstone must read as null")
        },
        ContractCase("a written tombstone reads back unchanged") {
            val store = newStore()
            val tombstone = testTombstone("a", updatedAtMillis = 1, device = "device-b")
            store.put(tombstone)
            expectEquals(tombstone, store.get("a"), "the tombstone read back")
        },
        ContractCase("put replaces an existing tombstone for the same id") {
            val store = newStore()
            store.put(testTombstone("a", updatedAtMillis = 1))
            val updated = testTombstone("a", updatedAtMillis = 2)
            store.put(updated)
            expectEquals(updated, store.get("a"), "the replaced tombstone")
            expectEquals(1, store.all().size, "the number of tombstones after a replace")
        },
        ContractCase("all returns every tombstone") {
            val store = newStore()
            store.put(testTombstone("a"))
            store.put(testTombstone("b"))
            expectEquals(listOf("a", "b"), store.all().map { it.id }.sorted(), "the tombstone ids")
        },
        ContractCase("remove deletes a tombstone") {
            val store = newStore()
            store.put(testTombstone("a"))
            store.put(testTombstone("b"))
            store.remove("a")
            expectTrue(store.get("a") == null, "a removed tombstone must read as null")
            expectEquals(listOf("b"), store.all().map { it.id }, "the surviving tombstones")
        },
        ContractCase("removing an absent id is not an error") {
            newStore().remove("absent")
        },
    )
}
