package app.lekto.testkit

import app.lekto.core.book.SampleDismissed
import app.lekto.core.book.SampleDismissedStore

/**
 * The specification of the [SampleDismissedStore] seam, as executable cases
 * (issue #278; ADR-0023's pattern). Framework-free like
 * [ReadingPreferencesStoreContract], so the in-memory fake runs it in
 * `core/commonTest` and the JSON document store runs the same cases over an
 * in-memory filesystem there and over a real directory in `core/jvmTest` — the
 * two implementations cannot silently differ (docs/development.md, "Test levels").
 * Each case builds a fresh store from [newStore], so cases cannot leak state.
 */
class SampleDismissedStoreContract(private val newStore: () -> SampleDismissedStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unset store holds no dismissal") { loadsNone() },
        ContractCase("a dismissal round-trips") { roundTrips() },
        ContractCase("a later save replaces the earlier dismissal") { replaces() },
    )

    private fun loadsNone() {
        expectEquals(SampleDismissed.NONE, newStore().load(), "an unset store")
    }

    private fun roundTrips() {
        val store = newStore()
        store.save(SampleDismissed("fr"))
        expectEquals(SampleDismissed("fr"), store.load(), "a dismissal")
    }

    private fun replaces() {
        val store = newStore()
        store.save(SampleDismissed("de"))
        store.save(SampleDismissed("ja"))
        expectEquals(SampleDismissed("ja"), store.load(), "replaced dismissal")
    }
}
