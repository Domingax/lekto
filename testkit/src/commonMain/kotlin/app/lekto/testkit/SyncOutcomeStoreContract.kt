package app.lekto.testkit

import app.lekto.core.sync.SyncOutcome
import app.lekto.core.sync.SyncOutcomeStore

/**
 * The specification of the [SyncOutcomeStore] seam, as executable cases (issue
 * #121; ADR-0028). It is framework-free like [SyncSettingsStoreContract], so the
 * in-memory fake runs it in `core/commonTest` and the JSON document store runs
 * the same cases over an in-memory filesystem there and over a real directory in
 * `core/jvmTest` — the two implementations cannot silently differ
 * (docs/testing.md, "Test levels"). Each case builds a fresh store from
 * [newStore], so cases cannot leak state.
 */
class SyncOutcomeStoreContract(private val newStore: () -> SyncOutcomeStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an empty store holds no outcome") { loadsNothing() },
        ContractCase("a saved success round-trips") { roundTripsSuccess() },
        ContractCase("a later save replaces the previous outcome") { replaces() },
        ContractCase("clearing forgets the outcome") { clears() },
    )

    private fun loadsNothing() {
        expectEquals(null, newStore().load(), "an empty store")
    }

    private fun roundTripsSuccess() {
        val store = newStore()
        val outcome = SyncOutcome(succeeded = true, message = "Synced: uploaded 1.")
        store.save(outcome)
        expectEquals(outcome, store.load(), "a saved outcome")
    }

    private fun replaces() {
        val store = newStore()
        store.save(SyncOutcome(succeeded = false, message = "The last sync didn't finish: the server is unreachable."))
        val second = SyncOutcome(succeeded = true, message = "Everything was already up to date.")
        store.save(second)
        expectEquals(second, store.load(), "a replaced outcome")
    }

    private fun clears() {
        val store = newStore()
        store.save(SyncOutcome(succeeded = true, message = "Synced: downloaded 2."))
        store.clear()
        expectEquals(null, store.load(), "a cleared store")
    }
}
