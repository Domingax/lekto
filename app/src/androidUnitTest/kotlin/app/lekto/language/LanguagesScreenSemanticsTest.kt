package app.lekto.language

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lekto.testkit.testOfferedLanguage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The **Languages** surface on a **simulated Android runtime** (issue #146): the
 * same-named twin of `app/desktopTest`'s `LanguagesScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. The listing, the honest
 * empty state with its **Reload list** action, the fetch progress and failure,
 * and leaving are the same behaviours the desktop lane proves, on the runtime the
 * app ships on.
 *
 * The list is a `LazyColumn` keyed by the language tag — a `String`, which the
 * platform can save — so this lane also guards the key rule issue #69 learned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class LanguagesScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listsTheOfferedLanguagesByName() {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))))

        compose.onNodeWithText("Languages").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithText("French").assertIsDisplayed()
        compose.onNodeWithTag(LANGUAGES_RELOAD_TAG).assertIsDisplayed()
    }

    @Test
    fun reloadingIsOfferedEvenWhenTheListIsShown() {
        var reloaded = false
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            onReload = { reloaded = true },
        )

        compose.onNodeWithTag(LANGUAGES_RELOAD_TAG).performClick()

        assertTrue(reloaded)
    }

    @Test
    fun withNoLanguagesItSaysSoAndOffersAReload() {
        var reloaded = false
        render(LanguagesUiState(), onReload = { reloaded = true })

        compose.onNodeWithText("hasn't loaded the list of languages", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(LANGUAGES_RELOAD_TAG).performClick()

        assertTrue(reloaded)
    }

    @Test
    fun anAvailableButEmptyListSaysSoHonestly() {
        render(LanguagesUiState(available = true))

        compose.onNodeWithText("doesn't offer any languages", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(LANGUAGES_RELOAD_TAG).assertIsDisplayed()
    }

    @Test
    fun reloadingDisablesTheActionAndReportsProgress() {
        render(LanguagesUiState(reloading = true))

        compose.onNodeWithText("Loading the language list", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(LANGUAGES_RELOAD_TAG).assertIsNotEnabled()
    }

    @Test
    fun aFetchFailureIsShownHonestly() {
        render(LanguagesUiState(error = "The language list couldn't be loaded."))

        compose.onNodeWithText("The language list couldn't be loaded.").assertIsDisplayed()
    }

    @Test
    fun goingBackRaisesTheAction() {
        var backed = false
        render(LanguagesUiState(), onBack = { backed = true })

        compose.onNodeWithText("Back").performClick()

        assertTrue(backed)
    }
}

/** Renders the Languages surface in the app theme, sized like a phone. */
private fun LanguagesScreenSemanticsTest.render(
    state: LanguagesUiState,
    onReload: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    compose.setContent {
        MaterialTheme {
            LanguagesScreen(
                state = state,
                actions = LanguagesActions(onReload = onReload, onBack = onBack),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
