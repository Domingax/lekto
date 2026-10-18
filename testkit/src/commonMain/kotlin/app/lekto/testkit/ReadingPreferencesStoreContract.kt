package app.lekto.testkit

import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingMargin
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.reading.ReadingPreferencesStore

/**
 * The specification of the [ReadingPreferencesStore] seam, as executable cases
 * (ADR-0035; ADR-0023's pattern). Framework-free like [LanguageSettingsStoreContract],
 * so the in-memory fake runs it in `core/commonTest` and the JSON document store
 * runs the same cases over an in-memory filesystem there and over a real directory
 * in `core/jvmTest` — the two implementations cannot silently differ
 * (docs/testing.md, "Test levels"). Each case builds a fresh store from
 * [newStore], so cases cannot leak state.
 */
@Suppress("MagicNumber") // The chosen sizes are the cases' parameters.
class ReadingPreferencesStoreContract(private val newStore: () -> ReadingPreferencesStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unset store holds the defaults") { loadsDefaults() },
        ContractCase("a chosen face round-trips") { roundTripsFace() },
        ContractCase("a chosen size round-trips") { roundTripsSize() },
        ContractCase("a chosen margin round-trips") { roundTripsMargin() },
        ContractCase("every choice round-trips together") { roundTripsAll() },
        ContractCase("a later save replaces the previous preferences") { replaces() },
    )

    private fun loadsDefaults() {
        expectEquals(ReadingPreferences.DEFAULT, newStore().load(), "an unset store")
    }

    private fun roundTripsFace() {
        val store = newStore()
        store.save(ReadingPreferences(face = ReadingFace.SANS_SERIF))
        expectEquals(ReadingPreferences(face = ReadingFace.SANS_SERIF), store.load(), "a chosen face")
    }

    private fun roundTripsSize() {
        val store = newStore()
        store.save(ReadingPreferences(size = 24f))
        expectEquals(ReadingPreferences(size = 24f), store.load(), "a chosen size")
    }

    private fun roundTripsMargin() {
        val store = newStore()
        store.save(ReadingPreferences(margin = ReadingMargin.WIDE))
        expectEquals(ReadingPreferences(margin = ReadingMargin.WIDE), store.load(), "a chosen margin")
    }

    private fun roundTripsAll() {
        val store = newStore()
        store.save(ReadingPreferences(ReadingFace.SANS_SERIF, 22f, ReadingMargin.NARROW))
        expectEquals(
            ReadingPreferences(ReadingFace.SANS_SERIF, 22f, ReadingMargin.NARROW),
            store.load(),
            "every choice",
        )
    }

    private fun replaces() {
        val store = newStore()
        store.save(ReadingPreferences(ReadingFace.SANS_SERIF, 14f, ReadingMargin.NARROW))
        store.save(ReadingPreferences(ReadingFace.SERIF, 28f, ReadingMargin.WIDE))
        expectEquals(
            ReadingPreferences(ReadingFace.SERIF, 28f, ReadingMargin.WIDE),
            store.load(),
            "replaced preferences",
        )
    }
}
