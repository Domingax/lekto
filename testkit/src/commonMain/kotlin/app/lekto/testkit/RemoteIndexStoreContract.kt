package app.lekto.testkit

import app.lekto.core.sync.RemoteIndexStore
import app.lekto.core.sync.Revision
import app.lekto.core.sync.SyncItem
import app.lekto.core.vault.VersionedRecord

/**
 * The specification of the [RemoteIndexStore] seam, as executable cases
 * (ADR-0027). It is framework-free, so the in-memory fake runs it in
 * `core/commonTest` and the JSON document store runs the same cases over an
 * in-memory filesystem — the same behaviours, so the two cannot drift
 * (docs/testing.md, "Test levels").
 *
 * Each case builds a fresh store from [newStore], so cases cannot leak state.
 */
class RemoteIndexStoreContract(private val newStore: () -> RemoteIndexStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an empty store remembers nothing") {
            expectTrue(newStore().all().isEmpty(), "an empty store must hold no item")
        },
        ContractCase("a replaced index reads back unchanged") {
            val store = newStore()
            val items = listOf(
                SyncItem(VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 1)), Revision("rev-a")),
                SyncItem(VersionedRecord.of(testTombstone("b", updatedAtMillis = 2)), Revision("rev-b")),
            )
            store.replaceAll(items)
            expectEquals(items.sortedBy { it.id }, store.all().sortedBy { it.id }, "the remembered items")
        },
        ContractCase("a later replace drops what it no longer names") {
            val store = newStore()
            store.replaceAll(listOf(SyncItem(VersionedRecord.of(testVaultRecord("a")), Revision("rev-a"))))
            val only = SyncItem(VersionedRecord.of(testVaultRecord("b")), Revision("rev-b"))
            store.replaceAll(listOf(only))
            expectEquals(listOf("b"), store.all().map { it.id }, "the surviving items")
        },
        ContractCase("a replaced index keeps the revision and the body together") {
            val store = newStore()
            val record = testVaultRecord("a", updatedAtMillis = 7)
            store.replaceAll(listOf(SyncItem(VersionedRecord.of(record), Revision("the-revision"))))
            val item = store.all().single()
            expectEquals(Revision("the-revision"), item.revision, "the remembered revision")
            expectEquals(VersionedRecord.of(record), item.version, "the remembered body")
        },
    )
}
