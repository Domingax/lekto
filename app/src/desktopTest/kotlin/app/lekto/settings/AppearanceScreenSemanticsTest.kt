package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.theme.AppTheme
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The **Appearance** screen through semantics (ADR-0036; docs/design.md §7.6): the
 * four entries — System, Light, Dark, Sepia — each named and shown on its own
 * paper, the current choice marked, a choice raising its action, the way back, and
 * the whole screen following the **app language** (ADR-0034).
 */
@OptIn(ExperimentalTestApi::class)
class AppearanceScreenSemanticsTest {

    @Test
    fun itOffersTheFourPapers() = runComposeUiTest {
        appearance(AppTheme.SYSTEM)

        AppTheme.entries.forEach { theme ->
            onNodeWithTag(appearanceOptionTag(theme)).performScrollTo().assertIsDisplayed()
        }
        onNodeWithText("System").assertIsDisplayed()
        onNodeWithText("Light").assertIsDisplayed()
        onNodeWithText("Dark").assertIsDisplayed()
        onNodeWithText("Sepia").assertIsDisplayed()
    }

    @Test
    fun eachPaperIsShownByWhatItLooksLike() = runComposeUiTest {
        appearance(AppTheme.SYSTEM)

        onNodeWithText("Follows the device setting").assertIsDisplayed()
        onNodeWithText("Warm paper, black ink").assertIsDisplayed()
        onNodeWithText("Warm night, light ink").assertIsDisplayed()
        onNodeWithText("Rag paper").assertIsDisplayed()
    }

    @Test
    fun theCurrentChoiceIsMarked() = runComposeUiTest {
        appearance(AppTheme.DARK)

        onNodeWithTag(appearanceOptionTag(AppTheme.DARK)).assertIsSelected()
        onNodeWithText("Current").assertIsDisplayed()
    }

    @Test
    fun theSystemEntryIsMarkedOnAFreshInstall() = runComposeUiTest {
        appearance(AppTheme.SYSTEM)

        onNodeWithTag(appearanceOptionTag(AppTheme.SYSTEM)).assertIsSelected()
    }

    @Test
    fun choosingAPaperRaisesIt() = runComposeUiTest {
        var chosen: AppTheme? = null
        appearance(AppTheme.SYSTEM, AppearanceActions(onSelect = { theme -> chosen = theme }))

        onNodeWithTag(appearanceOptionTag(AppTheme.SEPIA)).performScrollTo().performClick()

        assertEquals(AppTheme.SEPIA, chosen)
    }

    @Test
    fun theBackControlLeavesForSettings() = runComposeUiTest {
        var back = false
        appearance(AppTheme.SYSTEM, AppearanceActions(onBack = { back = true }))

        onNodeWithTag(APPEARANCE_BACK_TAG).performClick()

        assertTrue(back)
    }

    @Test
    fun theWholeScreenRendersInTheAppLanguage() = runComposeUiTest {
        appearance(AppTheme.SYSTEM, texts = appTextsFor("fr"))

        onNodeWithText("Apparence").assertIsDisplayed()
        onNodeWithText("Système").assertIsDisplayed()
        onNodeWithText("Sombre").assertIsDisplayed()
        onNodeWithText("Sépia").assertIsDisplayed()
        onNodeWithText("Actuel").assertIsDisplayed()
    }
}

/** Renders the screen in the app theme, sized like a phone. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.appearance(
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
    setContent {
        MaterialTheme {
            if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
        }
    }
}
