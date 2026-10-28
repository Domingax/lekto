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
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The **App language** screen through semantics (ADR-0034; issue #214): the
 * languages the app ships offered by name, the current one marked and selected,
 * a choice raising its action, the way back, and the whole screen following the
 * **app language**.
 */
@OptIn(ExperimentalTestApi::class)
class AppLanguageScreenSemanticsTest {

    @Test
    fun itOffersTheLanguagesTheAppShips() = runComposeUiTest {
        appLanguage("en")

        onNodeWithTag(appLanguageOptionTag("en")).assertIsDisplayed()
        onNodeWithTag(appLanguageOptionTag("fr")).assertIsDisplayed()
        // A language the app does not ship is never offered.
        onNodeWithTag(appLanguageOptionTag("es")).assertDoesNotExist()
        onNodeWithText("English").assertIsDisplayed()
        onNodeWithText("French").assertIsDisplayed()
    }

    @Test
    fun theCurrentLanguageIsMarkedAndSelected() = runComposeUiTest {
        appLanguage("fr")

        onNodeWithTag(appLanguageOptionTag("fr")).assertIsSelected()
    }

    @Test
    fun choosingALanguageRaisesTheAction() = runComposeUiTest {
        var chosen: String? = null
        appLanguage("en", actions = AppLanguageActions(onSelect = { code -> chosen = code }))

        onNodeWithTag(appLanguageOptionTag("fr")).performClick()

        assertEquals("fr", chosen)
    }

    @Test
    fun theWayBackRaisesItsAction() = runComposeUiTest {
        var back = false
        appLanguage("en", actions = AppLanguageActions(onBack = { back = true }))

        onNodeWithTag(APP_LANGUAGE_BACK_TAG).performClick()

        assertTrue(back)
    }

    @Test
    fun theWholeScreenRendersInTheAppLanguage() = runComposeUiTest {
        appLanguage("fr", texts = appTextsFor("fr"))

        onNodeWithText("Langue de l'application").assertIsDisplayed()
        onNodeWithText("français").performScrollTo().assertIsDisplayed()
        onNodeWithText("anglais").performScrollTo().assertIsDisplayed()
    }

    /** Renders the screen in the app theme, sized like a phone. */
    private fun ComposeUiTest.appLanguage(
        current: String,
        actions: AppLanguageActions = AppLanguageActions(),
        texts: AppTexts? = null,
    ) {
        val screen: @Composable () -> Unit = {
            AppLanguageScreen(
                current = current,
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
            )
        }
        setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}
