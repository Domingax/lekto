package app.lekto.testkit

import app.lekto.core.theme.AppTheme
import app.lekto.core.theme.AppThemeStore

/**
 * An in-memory [AppThemeStore] for tests (ADR-0036; ADR-0023's pattern): the app's
 * composition and UI are proved without a file, while the JSON-backed
 * implementation is proved in `core/commonTest` (docs/testing.md, "Test levels").
 * A store starts at [AppTheme.SYSTEM] — exactly what a fresh install holds.
 */
class InMemoryAppThemeStore(theme: AppTheme = AppTheme.SYSTEM) : AppThemeStore {

    private var stored: AppTheme = theme

    override fun load(): AppTheme = stored

    override fun save(theme: AppTheme) {
        stored = theme
    }
}
