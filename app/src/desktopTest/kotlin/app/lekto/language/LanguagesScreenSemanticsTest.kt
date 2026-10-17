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
import app.lekto.core.CefrLevel
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.OfferedDictionary
import app.lekto.dictionary.DictionaryUiState
import app.lekto.difficulty.DifficultyUiState
import app.lekto.testkit.testDifficultyMetadata
import app.lekto.testkit.testOfferedLanguage
import app.lekto.testkit.testPackMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The **Languages** surface through semantics (issues #146, #147): it lists the
 * offered **Reading language**s by name, offers each one a **Reader level**
 * picker whose **not set** choice clears the level, shows the honest empty state
 * with a **Reload list** action when none is known, reports a fetch in progress
 * and a fetch failure, and leaves. The ordering is proved as a pure function in
 * `core`; this lane pins what the reader sees.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One behaviour, one test; splitting the class hides the surface's contract.
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

    @Test
    fun eachLanguageShowsAReaderLevelPicker() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))),
            levels = mapOf("fr" to CefrLevel.B2),
        )

        onNodeWithTag(languageLevelPickerTag("en")).assertIsDisplayed()
        onNodeWithText("Not set").assertIsDisplayed()
        onNodeWithTag(languageLevelPickerTag("fr")).assertIsDisplayed()
        onNodeWithText("B2").assertIsDisplayed()
    }

    @Test
    fun aLevelIsReadByItsBaseTag() = runComposeUiTest {
        // The level is keyed by the language's base tag, so an offered tag with a
        // subtag still shows the level set for its base (CONTEXT.md, "Reading language").
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("pt-BR"))),
            levels = mapOf("pt" to CefrLevel.B1),
        )

        onNodeWithText("B1").assertIsDisplayed()
    }

    @Test
    fun choosingALevelRaisesTheSetAction() = runComposeUiTest {
        var chosen: Pair<String, CefrLevel>? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            onSetLevel = { language, level -> chosen = language to level },
        )

        onNodeWithTag(languageLevelPickerTag("en")).performClick()
        onNodeWithTag(languageLevelOptionTag("en", CefrLevel.B1)).performClick()

        assertEquals("en" to CefrLevel.B1, chosen)
    }

    @Test
    fun choosingNotSetRaisesTheClearAction() = runComposeUiTest {
        var cleared: String? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            levels = mapOf("en" to CefrLevel.B2),
            onClearLevel = { language -> cleared = language },
        )

        onNodeWithText("B2").assertIsDisplayed()
        onNodeWithTag(languageLevelPickerTag("en")).performClick()
        onNodeWithTag(languageLevelClearTag("en")).performClick()

        assertEquals("en", cleared)
    }

    @Test
    fun eachLanguageShowsItsDifficultyTableState() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", difficulty = DIFFICULTY))),
            difficulty = mapOf("en" to DifficultyUiState(status = DifficultyState.Ready(testDifficultyMetadata()))),
        )

        onNodeWithText("is installed", substring = true).assertIsDisplayed()
        onNodeWithText("Download again").assertIsDisplayed()
    }

    @Test
    fun aDownloadIsOfferedWhereTheManifestPublishesATable() = runComposeUiTest {
        var chosen: Pair<String, String>? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", difficulty = DIFFICULTY))),
            onDownloadDifficulty = { language, artifact -> chosen = language to artifact },
        )

        onNodeWithTag(languageDifficultyDownloadTag("en")).performClick()

        // The download carries the asset name the manifest published, not one re-derived here.
        assertEquals("en" to DIFFICULTY, chosen)
    }

    @Test
    fun aLanguageWithNoPublishedTableSaysSoAndOffersNoDownload() = runComposeUiTest {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("fr"))))

        onNodeWithText("doesn't publish word-difficulty data", substring = true).assertIsDisplayed()
        onNodeWithTag(languageDifficultyDownloadTag("fr")).assertDoesNotExist()
    }

    @Test
    fun downloadingDifficultyShowsProgressAndDisablesTheAction() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", difficulty = DIFFICULTY))),
            difficulty = mapOf("en" to DifficultyUiState(installing = true)),
        )

        onNodeWithText("Downloading word-difficulty data", substring = true).assertIsDisplayed()
        onNodeWithTag(languageDifficultyDownloadTag("en")).assertIsNotEnabled()
    }

    @Test
    fun aDifficultyFailureShowsAndDismisses() = runComposeUiTest {
        var dismissed: String? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", difficulty = DIFFICULTY))),
            difficulty = mapOf("en" to DifficultyUiState(error = "The download failed.")),
            onDismissDifficultyError = { language -> dismissed = language },
        )

        onNodeWithText("The download failed.").assertIsDisplayed()
        onNodeWithTag(languageDifficultyDismissTag("en")).performClick()

        assertEquals("en", dismissed)
    }

    @Test
    fun eachPublishedDictionaryIsLabelledByItsPair() = runComposeUiTest {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))))

        onNodeWithText("English → French").assertIsDisplayed()
    }

    @Test
    fun eachDictionaryShowsItsState() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(
                DICTIONARY to DictionaryUiState(status = DictionaryPackState.Ready(testPackMetadata())),
            ),
        )

        onNodeWithText("is installed", substring = true).assertIsDisplayed()
        onNodeWithText("Download again").assertIsDisplayed()
    }

    @Test
    fun anIncompatibleDictionaryTellsTheUserToDownloadAgain() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(
                DICTIONARY to DictionaryUiState(status = DictionaryPackState.Incompatible(found = 9, expected = 1)),
            ),
        )

        onNodeWithText("format 9", substring = true).assertIsDisplayed()
    }

    @Test
    fun aCorruptDictionaryTellsTheUserToDownloadAgain() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(
                DICTIONARY to DictionaryUiState(status = DictionaryPackState.Corrupt("file is not a database")),
            ),
        )

        onNodeWithText("damaged", substring = true).assertIsDisplayed()
    }

    @Test
    fun aDownloadIsOfferedWhereTheManifestPublishesAPair() = runComposeUiTest {
        var chosen: Triple<String, String, String>? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            onDownloadDictionary = { reading, translation, artifact ->
                chosen = Triple(reading, translation, artifact)
            },
        )

        onNodeWithTag(languageDictionaryDownloadTag(DICTIONARY)).performClick()

        // The download carries the pair and the asset name the manifest published.
        assertEquals(Triple("en", "fr", DICTIONARY), chosen)
    }

    @Test
    fun aLanguageWithNoPublishedDictionarySaysSoAndOffersNoDownload() = runComposeUiTest {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("fr"))))

        onNodeWithText("doesn't publish an offline dictionary", substring = true).assertIsDisplayed()
        onNodeWithTag(languageDictionaryDownloadTag(DICTIONARY)).assertDoesNotExist()
    }

    @Test
    fun downloadingADictionaryShowsProgressAndDisablesTheAction() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(DICTIONARY to DictionaryUiState(installing = true)),
        )

        onNodeWithText("Downloading the offline dictionary", substring = true).assertIsDisplayed()
        onNodeWithTag(languageDictionaryDownloadTag(DICTIONARY)).assertIsNotEnabled()
    }

    @Test
    fun aDictionaryFailureShowsAndDismisses() = runComposeUiTest {
        var dismissed: String? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en", dictionaries = listOf(PAIR)))),
            dictionaries = mapOf(DICTIONARY to DictionaryUiState(error = "The dictionary download failed.")),
            onDismissDictionaryError = { artifact -> dismissed = artifact },
        )

        onNodeWithText("The dictionary download failed.").assertIsDisplayed()
        onNodeWithTag(languageDictionaryDismissTag(DICTIONARY)).performClick()

        assertEquals(DICTIONARY, dismissed)
    }

    @Test
    fun theSurfaceOffersOnlyThePublishedTranslationLanguages() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            translationLanguages = listOf("fr", "es"),
        )

        onNodeWithText("Translation language").assertIsDisplayed()
        onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).assertIsDisplayed()
    }

    @Test
    fun choosingATranslationLanguageRaisesTheAction() = runComposeUiTest {
        var chosen: String? = null
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            translationLanguages = listOf("fr", "es"),
            onSetTranslationLanguage = { language -> chosen = language },
        )

        onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).performClick()
        onNodeWithTag(translationLanguageOptionTag("es")).performClick()

        assertEquals("es", chosen)
    }

    @Test
    fun theChosenTranslationLanguageIsShownByName() = runComposeUiTest {
        render(
            LanguagesUiState(languages = listOf(testOfferedLanguage("en"))),
            translationLanguage = "fr",
            translationLanguages = listOf("fr", "es"),
        )

        onNodeWithText("French").assertIsDisplayed()
    }

    @Test
    fun withNoPublishedTranslationLanguageNoPickerIsOffered() = runComposeUiTest {
        render(LanguagesUiState(languages = listOf(testOfferedLanguage("en"))))

        onNodeWithTag(TRANSLATION_LANGUAGE_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun attributionIsReachable() = runComposeUiTest {
        var opened = false
        render(LanguagesUiState(), onOpenAttribution = { opened = true })

        onNodeWithTag(LANGUAGES_ATTRIBUTION_TAG).performClick()

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
@OptIn(ExperimentalTestApi::class)
@Suppress("LongParameterList") // One helper mirroring the screen's state, levels, difficulty, dictionaries and actions.
private fun ComposeUiTest.render(
    state: LanguagesUiState,
    levels: Map<String, CefrLevel> = emptyMap(),
    difficulty: Map<String, DifficultyUiState> = emptyMap(),
    dictionaries: Map<String, DictionaryUiState> = emptyMap(),
    translationLanguage: String? = null,
    translationLanguages: List<String> = emptyList(),
    onReload: () -> Unit = {},
    onSetLevel: (String, CefrLevel) -> Unit = { _, _ -> },
    onClearLevel: (String) -> Unit = {},
    onDownloadDifficulty: (String, String) -> Unit = { _, _ -> },
    onDismissDifficultyError: (String) -> Unit = {},
    onDownloadDictionary: (String, String, String) -> Unit = { _, _, _ -> },
    onDismissDictionaryError: (String) -> Unit = {},
    onSetTranslationLanguage: (String) -> Unit = {},
    onOpenAttribution: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    setContent {
        MaterialTheme {
            LanguagesScreen(
                state = state,
                levels = levels,
                difficulty = difficulty,
                dictionaries = dictionaries,
                translationLanguage = translationLanguage,
                translationLanguages = translationLanguages,
                actions = LanguagesActions(
                    onReload = onReload,
                    onSetLevel = onSetLevel,
                    onClearLevel = onClearLevel,
                    onDownloadDifficulty = onDownloadDifficulty,
                    onDismissDifficultyError = onDismissDifficultyError,
                    onDownloadDictionary = onDownloadDictionary,
                    onDismissDictionaryError = onDismissDictionaryError,
                    onSetTranslationLanguage = onSetTranslationLanguage,
                    onOpenAttribution = onOpenAttribution,
                    onBack = onBack,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
