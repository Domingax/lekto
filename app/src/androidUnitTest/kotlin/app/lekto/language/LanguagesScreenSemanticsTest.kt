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
import app.lekto.core.CefrLevel
import app.lekto.testkit.testOfferedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The **Languages** surface on a **simulated Android runtime** (issues #146,
 * #147): the same-named twin of `app/desktopTest`'s
 * `LanguagesScreenSemanticsTest`, so the parity rule (issue #73) sees the two
 * lanes together. The listing, each language's **Reader level** picker with its
 * **not set** choice, the honest empty state with its **Reload list** action, the
 * fetch progress and failure, and leaving are the same behaviours the desktop
 * lane proves, on the runtime the app ships on.
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

    @Test
    fun eachLanguageShowsAReaderLevelPicker() {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))),
            levels = mapOf("fr" to CefrLevel.B2),
        )

        compose.onNodeWithTag(languageLevelPickerTag("en")).assertIsDisplayed()
        compose.onNodeWithText("Not set").assertIsDisplayed()
        compose.onNodeWithTag(languageLevelPickerTag("fr")).assertIsDisplayed()
        compose.onNodeWithText("B2").assertIsDisplayed()
    }

    @Test
    fun aLevelIsReadByItsBaseTag() {
        // The level is keyed by the language's base tag, so an offered tag with a
        // subtag still shows the level set for its base (CONTEXT.md, "Reading language").
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("pt-BR"))),
            levels = mapOf("pt" to CefrLevel.B1),
        )

        compose.onNodeWithText("B1").assertIsDisplayed()
    }

    @Test
    fun choosingALevelRaisesTheSetAction() {
        var chosen: Pair<String, CefrLevel>? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            onSetLevel = { language, level -> chosen = language to level },
        )

        compose.onNodeWithTag(languageLevelPickerTag("en")).performClick()
        compose.onNodeWithTag(languageLevelOptionTag("en", CefrLevel.B1)).performClick()

        assertEquals("en" to CefrLevel.B1, chosen)
    }

    @Test
    fun choosingNotSetRaisesTheClearAction() {
        var cleared: String? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            levels = mapOf("en" to CefrLevel.B2),
            onClearLevel = { language -> cleared = language },
        )

        compose.onNodeWithText("B2").assertIsDisplayed()
        compose.onNodeWithTag(languageLevelPickerTag("en")).performClick()
        compose.onNodeWithTag(languageLevelClearTag("en")).performClick()

        assertEquals("en", cleared)
    }
}

/** Renders the Languages surface in the app theme, sized like a phone. */
@Suppress("LongParameterList") // One helper mirroring the screen's state, levels and actions.
private fun LanguagesScreenSemanticsTest.render(
    state: LanguagesUiState,
    levels: Map<String, CefrLevel> = emptyMap(),
    onReload: () -> Unit = {},
    onSetLevel: (String, CefrLevel) -> Unit = { _, _ -> },
    onClearLevel: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    compose.setContent {
        MaterialTheme {
            LanguagesScreen(
                state = state,
                levels = levels,
                actions = LanguagesActions(
                    onReload = onReload,
                    onSetLevel = onSetLevel,
                    onClearLevel = onClearLevel,
                    onBack = onBack,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
