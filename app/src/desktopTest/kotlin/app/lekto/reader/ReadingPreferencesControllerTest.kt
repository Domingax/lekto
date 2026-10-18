package app.lekto.reader

import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingMargin
import app.lekto.core.reading.ReadingPreferences
import app.lekto.testkit.InMemoryReadingPreferencesStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reader's live **Reading preference** (ADR-0035): it reads the stored value
 * at startup, every control writes through, and the size stops at its bounds. The
 * store is the truth, so a "restart" — a fresh controller over the same store —
 * shows the last choice.
 */
@Suppress("MagicNumber") // The chosen sizes are the test's parameters.
class ReadingPreferencesControllerTest {

    @Test
    fun aFreshStoreStartsWithTheDefaults() {
        val controller = ReadingPreferencesController(InMemoryReadingPreferencesStore())

        assertEquals(ReadingPreferences.DEFAULT, controller.preferences)
    }

    @Test
    fun aStoredPreferenceIsLoadedAtStartup() {
        val store = InMemoryReadingPreferencesStore(ReadingPreferences(ReadingFace.SANS_SERIF, 24f, ReadingMargin.WIDE))

        assertEquals(
            ReadingPreferences(ReadingFace.SANS_SERIF, 24f, ReadingMargin.WIDE),
            ReadingPreferencesController(store).preferences,
        )
    }

    @Test
    fun applyingAChoiceWritesThrough() {
        val store = InMemoryReadingPreferencesStore()
        val controller = ReadingPreferencesController(store)

        controller.update(ReadingPreferences(ReadingFace.SANS_SERIF, 24f, ReadingMargin.WIDE))

        assertEquals(
            ReadingPreferences(ReadingFace.SANS_SERIF, 24f, ReadingMargin.WIDE),
            controller.preferences,
        )
        assertEquals(
            ReadingPreferences(ReadingFace.SANS_SERIF, 24f, ReadingMargin.WIDE),
            store.load(),
        )
    }

    @Test
    fun aRestartKeepsTheChoice() {
        val store = InMemoryReadingPreferencesStore()
        ReadingPreferencesController(store).update(ReadingPreferences(ReadingFace.SANS_SERIF, 28f, ReadingMargin.WIDE))

        assertEquals(
            ReadingPreferences(ReadingFace.SANS_SERIF, 28f, ReadingMargin.WIDE),
            ReadingPreferencesController(store).preferences,
        )
    }
}
