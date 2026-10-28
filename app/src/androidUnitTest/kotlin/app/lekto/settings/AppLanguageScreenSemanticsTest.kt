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
 * The **App language** screen on a **simulated Android runtime** (ADR-0034;
 * issue #214): the same-named twin of `app/desktopTest`'s
 * `AppLanguageScreenSemanticsTest`, so the parity rule (issue #73) sees the two
 * lanes together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLanguageScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun itOffersTheLanguagesTheAppShips() {
        render("en")

        compose.onNodeWithTag(appLanguageOptionTag("en")).assertIsDisplayed()
        compose.onNodeWithTag(appLanguageOptionTag("fr")).assertIsDisplayed()
        // A language the app does not ship is never offered.
        compose.onNodeWithTag(appLanguageOptionTag("es")).assertDoesNotExist()
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithText("French").assertIsDisplayed()
    }

    @Test
    fun theCurrentLanguageIsMarkedAndSelected() {
        render("fr")

        compose.onNodeWithTag(appLanguageOptionTag("fr")).assertIsSelected()
        compose.onNodeWithText("Current").assertIsDisplayed()
    }

    @Test
    fun choosingALanguageRaisesTheAction() {
        var chosen: String? = null
        render("en", actions = AppLanguageActions(onSelect = { code -> chosen = code }))

        compose.onNodeWithTag(appLanguageOptionTag("fr")).performClick()

        assertEquals("fr", chosen)
    }

    @Test
    fun theWayBackRaisesItsAction() {
        var back = false
        render("en", actions = AppLanguageActions(onBack = { back = true }))

        compose.onNodeWithTag(APP_LANGUAGE_BACK_TAG).performClick()

        assertTrue(back)
    }

    @Test
    fun theWholeScreenRendersInTheAppLanguage() {
        render("fr", texts = appTextsFor("fr"))

        compose.onNodeWithText("Langue de l'application").assertIsDisplayed()
        compose.onNodeWithText("français").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("anglais").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Actuel").assertIsDisplayed()
    }

    /** Renders the screen in the app theme, sized like a phone. */
    private fun render(current: String, actions: AppLanguageActions = AppLanguageActions(), texts: AppTexts? = null) {
        val screen: @Composable () -> Unit = {
            AppLanguageScreen(
                current = current,
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
            )
        }
        compose.setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}
