package app.lekto.theme

import app.lekto.core.theme.AppTheme
import app.lekto.testkit.InMemoryAppThemeStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app's live **App theme** (ADR-0036): it reads the stored choice at startup,
 * every choice writes through, and a "restart" — a fresh controller over the same
 * store — shows the last one. The store is the truth; the controller only keeps it
 * observable so a change repaints at once.
 */
class AppThemeControllerTest {

    @Test
    fun aFreshStoreFollowsThePlatform() {
        val controller = AppThemeController(InMemoryAppThemeStore())

        assertEquals(AppTheme.SYSTEM, controller.theme)
    }

    @Test
    fun aStoredChoiceIsLoadedAtStartup() {
        val store = InMemoryAppThemeStore(AppTheme.SEPIA)

        assertEquals(AppTheme.SEPIA, AppThemeController(store).theme)
    }

    @Test
    fun choosingAPaperWritesThrough() {
        val store = InMemoryAppThemeStore()
        val controller = AppThemeController(store)

        controller.choose(AppTheme.DARK)

        assertEquals(AppTheme.DARK, controller.theme)
        assertEquals(AppTheme.DARK, store.load())
    }

    @Test
    fun aRestartKeepsTheChoice() {
        val store = InMemoryAppThemeStore()
        AppThemeController(store).choose(AppTheme.LIGHT)

        assertEquals(AppTheme.LIGHT, AppThemeController(store).theme)
    }
}
