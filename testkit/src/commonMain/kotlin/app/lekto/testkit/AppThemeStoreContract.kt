package app.lekto.testkit

import app.lekto.core.theme.AppTheme
import app.lekto.core.theme.AppThemeStore

/**
 * The specification of the [AppThemeStore] seam, as executable cases (ADR-0036;
 * ADR-0023's pattern). Framework-free like [ReadingPreferencesStoreContract], so
 * the in-memory fake runs it in `core/commonTest` and the JSON document store runs
 * the same cases over an in-memory filesystem there and over a real directory in
 * `core/jvmTest` — the two implementations cannot silently differ
 * (docs/testing.md, "Test levels").
 */
class AppThemeStoreContract(private val newStore: () -> AppThemeStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unset store follows the platform") { loadsSystem() },
        ContractCase("a chosen paper round-trips") { roundTripsEveryChoice() },
        ContractCase("a later choice replaces the previous one") { replaces() },
    )

    private fun loadsSystem() {
        expectEquals(AppTheme.SYSTEM, newStore().load(), "an unset store")
    }

    private fun roundTripsEveryChoice() {
        AppTheme.entries.forEach { choice ->
            val store = newStore()
            store.save(choice)
            expectEquals(choice, store.load(), "the choice $choice")
        }
    }

    private fun replaces() {
        val store = newStore()
        store.save(AppTheme.DARK)
        store.save(AppTheme.SEPIA)
        expectEquals(AppTheme.SEPIA, store.load(), "the last choice")
    }
}
