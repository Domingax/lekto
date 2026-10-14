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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.lekto.core.CefrLevel
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.OfferedDictionary
import app.lekto.dictionary.DictionaryUiState
import app.lekto.difficulty.DifficultyUiState
import app.lekto.testkit.testDifficultyMetadata
import app.lekto.testkit.testOfferedLanguage
import app.lekto.testkit.testPackMetadata
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
@Suppress("TooManyFunctions") // One behaviour, one test; splitting the class hides the surface's contract.
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

    @Test
    fun eachLanguageShowsItsDifficultyTableState() {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", difficulty = DIFFICULTY))),
            difficulty = mapOf("en" to DifficultyUiState(status = DifficultyState.Ready(testDifficultyMetadata()))),
        )

        compose.onNodeWithText("is installed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Download again").assertIsDisplayed()
    }

    @Test
    fun aDownloadIsOfferedWhereTheManifestPublishesATable() {
        var chosen: Pair<String, String>? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", difficulty = DIFFICULTY))),
            onDownloadDifficulty = { language, artifact -> chosen = language to artifact },
        )

        compose.onNodeWithTag(languageDifficultyDownloadTag("en")).performClick()

        // The download carries the asset name the manifest published, not one re-derived here.
        assertEquals("en" to DIFFICULTY, chosen)
    }

    @Test
    fun aLanguageWithNoPublishedTableSaysSoAndOffersNoDownload() {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("fr"))))

        compose.onNodeWithText("doesn't publish word-difficulty data", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(languageDifficultyDownloadTag("fr")).assertDoesNotExist()
    }

    @Test
    fun downloadingDifficultyShowsProgressAndDisablesTheAction() {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", difficulty = DIFFICULTY))),
            difficulty = mapOf("en" to DifficultyUiState(installing = true)),
        )

        compose.onNodeWithText("Downloading word-difficulty data", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(languageDifficultyDownloadTag("en")).assertIsNotEnabled()
    }

    @Test
    fun aDifficultyFailureShowsAndDismisses() {
        var dismissed: String? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", difficulty = DIFFICULTY))),
            difficulty = mapOf("en" to DifficultyUiState(error = "The download failed.")),
            onDismissDifficultyError = { language -> dismissed = language },
        )

        compose.onNodeWithText("The download failed.").assertIsDisplayed()
        compose.onNodeWithTag(languageDifficultyDismissTag("en")).performClick()

        assertEquals("en", dismissed)
    }

    @Test
    fun eachPublishedDictionaryIsLabelledByItsPair() {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))))

        compose.onNodeWithText("English → French").assertIsDisplayed()
    }

    @Test
    fun eachDictionaryShowsItsState() {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(
                DICTIONARY to DictionaryUiState(status = DictionaryPackState.Ready(testPackMetadata())),
            ),
        )

        compose.onNodeWithText("is installed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Download again").assertIsDisplayed()
    }

    @Test
    fun anIncompatibleDictionaryTellsTheUserToDownloadAgain() {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(
                DICTIONARY to DictionaryUiState(status = DictionaryPackState.Incompatible(found = 9, expected = 1)),
            ),
        )

        compose.onNodeWithText("format 9", substring = true).assertIsDisplayed()
    }

    @Test
    fun aCorruptDictionaryTellsTheUserToDownloadAgain() {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(
                DICTIONARY to DictionaryUiState(status = DictionaryPackState.Corrupt("file is not a database")),
            ),
        )

        compose.onNodeWithText("damaged", substring = true).assertIsDisplayed()
    }

    @Test
    fun aDownloadIsOfferedWhereTheManifestPublishesAPair() {
        var chosen: Triple<String, String, String>? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            onDownloadDictionary = { reading, translation, artifact ->
                chosen = Triple(reading, translation, artifact)
            },
        )

        compose.onNodeWithTag(languageDictionaryDownloadTag(DICTIONARY)).performClick()

        // The download carries the pair and the asset name the manifest published.
        assertEquals(Triple("en", "fr", DICTIONARY), chosen)
    }

    @Test
    fun aLanguageWithNoPublishedDictionarySaysSoAndOffersNoDownload() {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("fr"))))

        compose.onNodeWithText("doesn't publish an offline dictionary", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(languageDictionaryDownloadTag(DICTIONARY)).assertDoesNotExist()
    }

    @Test
    fun downloadingADictionaryShowsProgressAndDisablesTheAction() {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(DICTIONARY to DictionaryUiState(installing = true)),
        )

        compose.onNodeWithText("Downloading the offline dictionary", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(languageDictionaryDownloadTag(DICTIONARY)).assertIsNotEnabled()
    }

    @Test
    fun aDictionaryFailureShowsAndDismisses() {
        var dismissed: String? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(DICTIONARY to DictionaryUiState(error = "The dictionary download failed.")),
            onDismissDictionaryError = { artifact -> dismissed = artifact },
        )

        compose.onNodeWithText("The dictionary download failed.").assertIsDisplayed()
        compose.onNodeWithTag(languageDictionaryDismissTag(DICTIONARY)).performScrollTo().performClick()

        assertEquals(DICTIONARY, dismissed)
    }

    @Test
    fun attributionIsReachable() {
        var opened = false
        render(LanguagesUiState(), onOpenAttribution = { opened = true })

        compose.onNodeWithTag(LANGUAGES_ATTRIBUTION_TAG).performClick()

        assertTrue(opened)
    }
}

/** The manifest's published difficulty asset name for English. */
private const val DIFFICULTY: String = "lekto-difficulty-en.sqlite.gz"

/** The manifest's published English → French dictionary asset name. */
private const val DICTIONARY: String = "lekto-dictionary-en-fr.sqlite.gz"

/** The English → French dictionary pair the manifest publishes, as the surface reads it. */
private val PAIR: OfferedDictionary = OfferedDictionary(translationLanguage = "fr", name = DICTIONARY)

/** Renders the Languages surface in the app theme, sized like a phone. */
@Suppress("LongParameterList") // One helper mirroring the screen's state, levels, difficulty, dictionaries and actions.
private fun LanguagesScreenSemanticsTest.render(
    state: LanguagesUiState,
    levels: Map<String, CefrLevel> = emptyMap(),
    difficulty: Map<String, DifficultyUiState> = emptyMap(),
    dictionaries: Map<String, DictionaryUiState> = emptyMap(),
    onReload: () -> Unit = {},
    onSetLevel: (String, CefrLevel) -> Unit = { _, _ -> },
    onClearLevel: (String) -> Unit = {},
    onDownloadDifficulty: (String, String) -> Unit = { _, _ -> },
    onDismissDifficultyError: (String) -> Unit = {},
    onDownloadDictionary: (String, String, String) -> Unit = { _, _, _ -> },
    onDismissDictionaryError: (String) -> Unit = {},
    onOpenAttribution: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    compose.setContent {
        MaterialTheme {
            LanguagesScreen(
                state = state,
                levels = levels,
                difficulty = difficulty,
                dictionaries = dictionaries,
                actions = LanguagesActions(
                    onReload = onReload,
                    onSetLevel = onSetLevel,
                    onClearLevel = onClearLevel,
                    onDownloadDifficulty = onDownloadDifficulty,
                    onDismissDifficultyError = onDismissDifficultyError,
                    onDownloadDictionary = onDownloadDictionary,
                    onDismissDictionaryError = onDismissDictionaryError,
                    onOpenAttribution = onOpenAttribution,
                    onBack = onBack,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
