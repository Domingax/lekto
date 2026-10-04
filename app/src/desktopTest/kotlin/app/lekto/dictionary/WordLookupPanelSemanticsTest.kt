package app.lekto.dictionary

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.DictionaryShortcut
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.dictionary.dictionaryShortcuts
import app.lekto.core.speech.SpeechResult
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The word lookup panel through semantics (issue #19): a known word shows its
 * pronunciation, definitions and translations beside the reference shortcuts; an
 * unknown word and an offline lookup keep their honest message and the shortcuts
 * still stand; a shortcut opens the canonical page it names. Since issue #21 it
 * also carries the pronunciation control and the honest result of speaking.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One panel, one test per outcome and control; splitting hides the panel's surface.
class WordLookupPanelSemanticsTest {

    private val shortcuts = dictionaryShortcuts("blorple", "en", "fr")

    @Test
    fun aFoundWordShowsItsPronunciationDefinitionsAndTranslations() = runComposeUiTest {
        val entry = DictionaryEntry(
            lemma = "blorple",
            language = "en",
            partOfSpeech = "verb",
            pronunciation = "/ˈblɔːpəl/",
            audioUrl = null,
            senses = listOf(DictionarySense("To move swiftly.", listOf("blorper"))),
        )

        setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        onNodeWithText("blorple").assertIsDisplayed()
        onNodeWithText("/ˈblɔːpəl/").assertIsDisplayed()
        onNodeWithText("To move swiftly.", substring = true).assertIsDisplayed()
        onNodeWithText("→ blorper", substring = true).assertIsDisplayed()
    }

    @Test
    fun anOfflineLookupKeepsItsMessageAndStillOffersTheShortcuts() = runComposeUiTest {
        setContent { Panel(WordLookup.Unavailable("The offline dictionary isn't installed yet.")) }

        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        DictionarySource.entries.forEach { source ->
            onNodeWithTag(sourceShortcutTag(source)).assertIsDisplayed()
        }
    }

    @Test
    fun aShortcutOpensTheCanonicalPageForTheWord() = runComposeUiTest {
        var opened: DictionaryShortcut? = null
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onOpenShortcut = { opened = it }) }

        onNodeWithTag(sourceShortcutTag(DictionarySource.REVERSO)).performClick()

        assertEquals(
            "https://context.reverso.net/translation/english-french/blorple",
            opened?.url,
        )
    }

    @Test
    fun anUnknownWordSaysSoAndCanBeDismissed() = runComposeUiTest {
        var dismissed = false
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onDismiss = { dismissed = true }) }

        onNodeWithText("isn't in the offline dictionary", substring = true).assertIsDisplayed()
        onNodeWithText("Close").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun anInflectedSurfaceShowsTheLemmaItResolvedTo() = runComposeUiTest {
        val entry = DictionaryEntry(
            lemma = "blorple",
            language = "en",
            partOfSpeech = "verb",
            pronunciation = null,
            audioUrl = null,
            senses = listOf(DictionarySense("To move swiftly.", listOf("blorper"))),
        )

        setContent { Panel(WordLookup.Found("blorpled", "en", "blorple", listOf(entry)), term = "blorpled") }

        onNodeWithText("blorpled").assertIsDisplayed()
        onNodeWithText("blorple").assertIsDisplayed()
    }

    @Test
    fun anEntryWithoutPronunciationOrTranslationsStillShowsItsDefinition() = runComposeUiTest {
        val entry = DictionaryEntry(
            lemma = "vrok",
            language = "en",
            partOfSpeech = null,
            pronunciation = null,
            audioUrl = null,
            senses = listOf(DictionarySense("A word.", emptyList())),
        )

        setContent { Panel(WordLookup.Found("vrok", "en", "vrok", listOf(entry))) }

        onNodeWithText("A word.", substring = true).assertIsDisplayed()
    }

    @Test
    fun theListenButtonInvokesPronunciation() = runComposeUiTest {
        var spoken = false
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSpeak = { spoken = true }) }

        onNodeWithTag(PRONOUNCE_TAG).performClick()

        assertTrue(spoken, "the Listen button must reach the pronunciation seam")
    }

    @Test
    fun aLanguageWithNoVoiceShowsAnHonestMessage() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), speech = SpeechResult.NoVoice("fr")) }

        onNodeWithText("No voice is installed for \"fr\".", substring = true).assertIsDisplayed()
    }

    @Test
    fun anUnavailableEngineShowsItsMessage() = runComposeUiTest {
        setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                speech = SpeechResult.Unavailable("No speech engine is available on this device."),
            )
        }

        onNodeWithText("No speech engine is available on this device.").assertIsDisplayed()
    }

    @Test
    fun savingFromThePanelSavesAtTheDefaultLevel() = runComposeUiTest {
        var saved: MasteryLevel? = null
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSave = { saved = it }) }

        onNodeWithTag(SAVE_TAG).performScrollTo().performClick()

        assertEquals(MasteryLevel.FAMILIAR, saved)
    }

    @Test
    fun tappingAMasteryLevelSavesAtThatLevel() = runComposeUiTest {
        var saved: MasteryLevel? = null
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSave = { saved = it }) }

        onNodeWithTag(masteryTag(MasteryLevel.MASTERED)).performScrollTo().performClick()

        assertEquals(MasteryLevel.MASTERED, saved)
    }

    @Test
    fun aSavedWordLabelsTheSaveButtonAndMarksItsLevel() = runComposeUiTest {
        val entry = VocabularyEntry(WordKey("en", "blorple"), "blorple", mastery = MasteryLevel.RECOGNIZED)
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), entry = entry) }

        onNodeWithText("Saved").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).assertIsDisplayed()
    }

    @Suppress("LongParameterList") // The panel's inputs are independent; a bundle would only hide that.
    @Composable
    private fun Panel(
        result: WordLookup,
        term: String = "blorple",
        speech: SpeechResult? = null,
        entry: VocabularyEntry? = null,
        onOpenShortcut: (DictionaryShortcut) -> Unit = {},
        onSpeak: () -> Unit = {},
        onSave: (MasteryLevel) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        MaterialTheme {
            WordLookupPanel(
                result = result,
                term = term,
                shortcuts = shortcuts,
                onOpenShortcut = onOpenShortcut,
                onDismiss = onDismiss,
                pronunciation = Pronunciation(onSpeak = onSpeak, result = speech),
                vocabulary = VocabularyPanel(entry = entry, onSave = onSave),
                modifier = Modifier.size(width = 360.dp, height = 480.dp),
            )
        }
    }
}
