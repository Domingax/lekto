package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.lekto.core.theme.AppTheme
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The **Appearance** screen on a **simulated Android runtime** (ADR-0036): the
 * same-named twin of `app/desktopTest`'s `AppearanceScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. The four entries, the
 * current mark, choosing a paper, the way back and the app language are the same
 * behaviours the desktop lane proves, run here on the runtime the app ships on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AppearanceScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `it offers the four papers on Android`() {
        compose.setContent { Appearance(AppTheme.SYSTEM) }

        AppTheme.entries.forEach { theme ->
            compose.onNodeWithTag(appearanceOptionTag(theme)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("System").assertIsDisplayed()
        compose.onNodeWithText("Sepia").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `each paper is shown by what it looks like on Android`() {
        compose.setContent { Appearance(AppTheme.SYSTEM) }

        compose.onNodeWithText("Follows the device setting").assertIsDisplayed()
        compose.onNodeWithText("Warm paper, black ink").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Warm night, light ink").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rag paper").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the current choice is marked on Android`() {
        compose.setContent { Appearance(AppTheme.DARK) }

        compose.onNodeWithTag(appearanceOptionTag(AppTheme.DARK)).assertIsSelected()
        compose.onNodeWithText("Current").assertIsDisplayed()
    }

    @Test
    fun `choosing a paper raises it on Android`() {
        var chosen: AppTheme? = null
        compose.setContent {
            Appearance(AppTheme.SYSTEM, AppearanceActions(onSelect = { theme -> chosen = theme }))
        }

        compose.onNodeWithTag(appearanceOptionTag(AppTheme.SEPIA)).performScrollTo().performClick()

        assertEquals(AppTheme.SEPIA, chosen)
    }

    @Test
    fun `the back control leaves for settings on Android`() {
        var back = false
        compose.setContent { Appearance(AppTheme.SYSTEM, AppearanceActions(onBack = { back = true })) }

        compose.onNodeWithTag(APPEARANCE_BACK_TAG).performClick()

        assertTrue(back)
    }

    @Test
    fun `the whole screen renders in the app language on Android`() {
        compose.setContent { Appearance(AppTheme.SYSTEM, texts = appTextsFor("fr")) }

        compose.onNodeWithText("Apparence").assertIsDisplayed()
        compose.onNodeWithText("Sombre").assertIsDisplayed()
        compose.onNodeWithText("Actuel").assertIsDisplayed()
    }

    @Composable
    private fun Appearance(
        current: AppTheme,
        actions: AppearanceActions = AppearanceActions(),
        texts: AppTexts? = null,
    ) {
        val screen: @Composable () -> Unit = {
            AppearanceScreen(
                current = current,
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
        MaterialTheme {
            if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
        }
    }
}
