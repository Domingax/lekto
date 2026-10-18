package app.lekto.testkit

import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.reading.ReadingPreferencesStore

/**
 * An in-memory [ReadingPreferencesStore] for tests (ADR-0035; ADR-0023's
 * pattern): the app's composition and UI are proved without a file, while the
 * JSON-backed implementation is proved in `core/commonTest` (docs/testing.md,
 * "Test levels"). A store starts at [ReadingPreferences.DEFAULT] — exactly what a
 * fresh install holds.
 */
class InMemoryReadingPreferencesStore(preferences: ReadingPreferences = ReadingPreferences.DEFAULT) :
    ReadingPreferencesStore {

    private var stored: ReadingPreferences = preferences

    override fun load(): ReadingPreferences = stored

    override fun save(preferences: ReadingPreferences) {
        stored = preferences
    }
}
