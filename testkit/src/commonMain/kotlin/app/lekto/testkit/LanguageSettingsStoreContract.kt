package app.lekto.testkit

import app.lekto.core.language.LanguageSettings
import app.lekto.core.language.LanguageSettingsStore

/**
 * The specification of the [LanguageSettingsStore] seam, as executable cases
 * (ADR-0034; ADR-0023's pattern). Framework-free like [LlmSettingsStoreContract],
 * so the in-memory fake runs it in `core/commonTest` and the JSON document store
 * runs the same cases over an in-memory filesystem there and over a real directory
 * in `core/jvmTest` — the two implementations cannot silently differ
 * (docs/testing.md, "Test levels"). Each case builds a fresh store from
 * [newStore], so cases cannot leak state.
 */
class LanguageSettingsStoreContract(private val newStore: () -> LanguageSettingsStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unset store holds no language") { loadsNone() },
        ContractCase("a chosen app language round-trips") { roundTripsAppLanguage() },
        ContractCase("a chosen translation language round-trips") { roundTripsTranslationLanguage() },
        ContractCase("both choices round-trip together") { roundTripsBoth() },
        ContractCase("a language is collapsed to its base tag") { collapses() },
        ContractCase("a later save replaces the previous settings") { replaces() },
    )

    private fun loadsNone() {
        expectEquals(LanguageSettings.NONE, newStore().load(), "an unset store")
    }

    private fun roundTripsAppLanguage() {
        val store = newStore()
        store.save(LanguageSettings(appLanguage = "fr"))
        expectEquals(LanguageSettings(appLanguage = "fr"), store.load(), "a chosen app language")
    }

    private fun roundTripsTranslationLanguage() {
        val store = newStore()
        store.save(LanguageSettings(translationLanguage = "es"))
        expectEquals(LanguageSettings(translationLanguage = "es"), store.load(), "a chosen translation language")
    }

    private fun roundTripsBoth() {
        val store = newStore()
        store.save(LanguageSettings(appLanguage = "fr", translationLanguage = "es"))
        expectEquals(
            LanguageSettings(appLanguage = "fr", translationLanguage = "es"),
            store.load(),
            "both choices",
        )
    }

    private fun collapses() {
        val store = newStore()
        store.save(LanguageSettings(appLanguage = "fr-CA", translationLanguage = "es-419"))
        expectEquals(LanguageSettings(appLanguage = "fr", translationLanguage = "es"), store.load(), "regional tags")
    }

    private fun replaces() {
        val store = newStore()
        store.save(LanguageSettings(appLanguage = "fr", translationLanguage = "es"))
        store.save(LanguageSettings(appLanguage = "en", translationLanguage = "fr"))
        expectEquals(
            LanguageSettings(appLanguage = "en", translationLanguage = "fr"),
            store.load(),
            "replaced settings",
        )
    }
}
