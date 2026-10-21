package app.lekto

import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import app.lekto.core.theme.AppTheme
import app.lekto.theme.appThemeScheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Android window theme the **App theme** asks for (ADR-0036), on a simulated
 * Android runtime: **System** takes the platform's night bit, so a dark device
 * launches dark; a chosen paper wins in either setting, so an explicitly chosen
 * Dark on a light device does not flash, and an explicitly chosen Light on a dark
 * one does not either.
 *
 * The window's colours are resources, which cannot name the Compose constants, so
 * a case pins the two together: a paper changed in one place and not the other
 * fails here rather than in a launch-window flash nobody notices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AndroidThemeTest {

    @Test
    fun systemTakesThePlatformsNightBit() {
        assertEquals(R.style.Theme_Lekto_Dark, androidWindowTheme(AppTheme.SYSTEM, configuration(night = true)))
        assertEquals(R.style.Theme_Lekto_Light, androidWindowTheme(AppTheme.SYSTEM, configuration(night = false)))
    }

    @Test
    fun aChosenPaperWinsInEitherSetting() {
        assertEquals(R.style.Theme_Lekto_Dark, androidWindowTheme(AppTheme.DARK, configuration(night = false)))
        assertEquals(R.style.Theme_Lekto_Light, androidWindowTheme(AppTheme.LIGHT, configuration(night = true)))
        assertEquals(R.style.Theme_Lekto_Sepia, androidWindowTheme(AppTheme.SEPIA, configuration(night = true)))
    }

    @Test
    fun theWindowColoursAreTheThemesPapers() {
        val context = RuntimeEnvironment.getApplication()
        val light = appThemeScheme(AppTheme.LIGHT, systemInDark = false).background.toArgb()
        val dark = appThemeScheme(AppTheme.DARK, systemInDark = false).background.toArgb()
        val sepia = appThemeScheme(AppTheme.SEPIA, systemInDark = false).background.toArgb()

        assertEquals(light, context.getColor(R.color.lekto_paper_light))
        assertEquals(dark, context.getColor(R.color.lekto_paper_dark))
        assertEquals(sepia, context.getColor(R.color.lekto_paper_sepia))
    }
}

/** A device configuration whose only relevant bit is the platform's night setting. */
private fun configuration(night: Boolean): Configuration = Configuration().apply {
    uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
}
