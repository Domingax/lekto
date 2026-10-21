package app.lekto.theme

import app.lekto.core.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The **App theme**'s mapping onto the identity's three colour schemes
 * (docs/design.md §2.1; ADR-0036). A chosen paper is itself; **System** takes the
 * platform's report, which is the one place the platform is consulted.
 */
class AppThemeColorsTest {

    @Test
    fun eachChosenPaperIsItsScheme() {
        assertEquals(Light, appThemeScheme(AppTheme.LIGHT, systemInDark = false))
        assertEquals(Light, appThemeScheme(AppTheme.LIGHT, systemInDark = true))
        assertEquals(Dark, appThemeScheme(AppTheme.DARK, systemInDark = false))
        assertEquals(Sepia, appThemeScheme(AppTheme.SEPIA, systemInDark = false))
    }

    @Test
    fun systemTakesThePlatformsPaper() {
        assertEquals(Dark, appThemeScheme(AppTheme.SYSTEM, systemInDark = true))
        assertEquals(Light, appThemeScheme(AppTheme.SYSTEM, systemInDark = false))
    }

    @Test
    fun theWindowPaperIsTheActiveBackground() {
        assertEquals(Light.background, appThemeScheme(AppTheme.LIGHT, systemInDark = false).background)
        assertEquals(Dark.background, appThemeScheme(AppTheme.DARK, systemInDark = false).background)
        assertEquals(Sepia.background, appThemeScheme(AppTheme.SEPIA, systemInDark = false).background)
    }
}
