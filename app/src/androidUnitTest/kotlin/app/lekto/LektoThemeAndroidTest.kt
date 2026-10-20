package app.lekto

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.font.FontFamily
import app.lekto.theme.Dark
import app.lekto.theme.LektoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared theme on a **simulated Android runtime**: the same `LektoTheme`
 * composable both clients use, painted with the Dark paper, so the theme cannot
 * become a desktop-only code path.
 *
 * The window background before the first frame is a platform concern and belongs
 * to the **App theme** preference (issue #182); this pins what Android composes
 * once the app is running.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class LektoThemeAndroidTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSharedThemePaintsAndroidInTheDarkPaper() {
        var background: Color? = null
        var ink: Color? = null
        var titleFamily: FontFamily? = null

        compose.setContent {
            LektoTheme(theme = Dark) {
                background = MaterialTheme.colorScheme.background
                ink = MaterialTheme.colorScheme.onBackground
                titleFamily = MaterialTheme.typography.headlineMedium.fontFamily
            }
        }
        compose.waitForIdle()

        assertEquals(Dark.background, background)
        assertEquals(Dark.onBackground, ink)
        assertEquals(FontFamily.Serif, titleFamily)
    }
}
