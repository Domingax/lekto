package app.lekto.testkit

import app.lekto.core.guide.GuideProgress
import app.lekto.core.guide.GuideProgressStore

/**
 * The specification of the [GuideProgressStore] seam, as executable cases
 * (ADR-0023's pattern; issue #277). Framework-free like the other settings-store
 * contracts, so the in-memory fake runs it in `core/commonTest` and the JSON
 * document store runs the same cases there and over a real directory in
 * `core/jvmTest` — the two implementations cannot silently differ
 * (docs/development.md, "Test levels"). Each case builds a fresh store from
 * [newStore], so cases cannot leak state.
 */
class GuideProgressStoreContract(private val newStore: () -> GuideProgressStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unset store has not seen the guide") { loadsNotSeen() },
        ContractCase("seeing the guide round-trips") { roundTripsSeen() },
        ContractCase("a later save replaces the previous progress") { replaces() },
    )

    private fun loadsNotSeen() {
        expectEquals(GuideProgress.NOT_SEEN, newStore().load(), "an unset store")
    }

    private fun roundTripsSeen() {
        val store = newStore()
        store.save(GuideProgress.SEEN)
        expectEquals(GuideProgress.SEEN, store.load(), "a seen guide")
    }

    private fun replaces() {
        val store = newStore()
        store.save(GuideProgress.SEEN)
        store.save(GuideProgress.NOT_SEEN)
        expectEquals(GuideProgress.NOT_SEEN, store.load(), "replaced progress")
    }
}
