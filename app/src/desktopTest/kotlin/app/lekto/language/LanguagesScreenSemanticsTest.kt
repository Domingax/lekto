package app.lekto.language

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.testkit.testOfferedLanguage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The **Languages** surface through semantics (issue #146): it lists the offered
 * **Reading language**s by name, shows the honest empty state with a **Reload
 * list** action when none is known, reports a fetch in progress and a fetch
 * failure, and leaves. The ordering is proved as a pure function in `core`; this
 * lane pins what the reader sees.
 */
@OptIn(ExperimentalTestApi::class)
class LanguagesScreenSemanticsTest {

    @Test
    fun listsTheOfferedLanguagesByName() = runComposeUiTest {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))))

        onNodeWithText("Languages").assertIsDisplayed()
        onNodeWithText("English").assertIsDisplayed()
        onNodeWithText("French").assertIsDisplayed()
        onNodeWithTag(LANGUAGES_RELOAD_TAG).assertIsDisplayed()
    }

    @Test
    fun reloadingIsOfferedEvenWhenTheListIsShown() = runComposeUiTest {
        var reloaded = false
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            onReload = { reloaded = true },
        )

        onNodeWithTag(LANGUAGES_RELOAD_TAG).performClick()

        assertTrue(reloaded)
    }

    @Test
    fun withNoLanguagesItSaysSoAndOffersAReload() = runComposeUiTest {
        var reloaded = false
        render(LanguagesUiState(), onReload = { reloaded = true })

        onNodeWithText("hasn't loaded the list of languages", substring = true).assertIsDisplayed()
        onNodeWithTag(LANGUAGES_RELOAD_TAG).performClick()

        assertTrue(reloaded)
    }

    @Test
    fun anAvailableButEmptyListSaysSoHonestly() = runComposeUiTest {
        render(LanguagesUiState(available = true))

        onNodeWithText("doesn't offer any languages", substring = true).assertIsDisplayed()
        onNodeWithTag(LANGUAGES_RELOAD_TAG).assertIsDisplayed()
    }

    @Test
    fun reloadingDisablesTheActionAndReportsProgress() = runComposeUiTest {
        render(LanguagesUiState(reloading = true))

        onNodeWithText("Loading the language list", substring = true).assertIsDisplayed()
        onNodeWithTag(LANGUAGES_RELOAD_TAG).assertIsNotEnabled()
    }

    @Test
    fun aFetchFailureIsShownHonestly() = runComposeUiTest {
        render(LanguagesUiState(error = "The language list couldn't be loaded."))

        onNodeWithText("The language list couldn't be loaded.").assertIsDisplayed()
    }

    @Test
    fun goingBackRaisesTheAction() = runComposeUiTest {
        var backed = false
        render(LanguagesUiState(), onBack = { backed = true })

        onNodeWithText("Back").performClick()

        assertTrue(backed)
    }
}

/** Renders the Languages surface in the app theme, sized like a phone. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.render(state: LanguagesUiState, onReload: () -> Unit = {}, onBack: () -> Unit = {}) {
    setContent {
        MaterialTheme {
            LanguagesScreen(
                state = state,
                actions = LanguagesActions(onReload = onReload, onBack = onBack),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
