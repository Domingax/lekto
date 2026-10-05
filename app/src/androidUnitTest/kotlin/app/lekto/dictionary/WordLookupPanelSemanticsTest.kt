package app.lekto.dictionary

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The word lookup panel on a **simulated Android runtime** (issue #76): the
 * same-named twin of `app/desktopTest`'s `WordLookupPanelSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. The desktop twin proves a
 * found word's definitions and translations, the offline message beside the
 * reference shortcuts, an unknown word, the pronunciation control, the mastery
 * selector and Save; this lane proves the same under Robolectric, on the runtime
 * that enforces Android's platform constraints.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("TooManyFunctions") // One panel, one test per outcome and control; splitting hides the panel's surface.
class WordLookupPanelSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val shortcuts = dictionaryShortcuts("blorple", "en", "fr")

    @Test
    fun `a found word shows its pronunciation definitions and translations`() {
        val entry = DictionaryEntry(
            lemma = "blorple",
            language = "en",
            partOfSpeech = "verb",
            pronunciation = "/ˈblɔːpəl/",
            audioUrl = null,
            senses = listOf(DictionarySense("To move swiftly.", listOf("blorper"))),
        )

        compose.setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        compose.onNodeWithText("blorple").assertIsDisplayed()
        compose.onNodeWithText("/ˈblɔːpəl/").assertIsDisplayed()
        compose.onNodeWithText("To move swiftly.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("→ blorper", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an offline lookup keeps its message and still offers the shortcuts`() {
        compose.setContent { Panel(WordLookup.Unavailable("The offline dictionary isn't installed yet.")) }

        compose.onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        DictionarySource.entries.forEach { source ->
            compose.onNodeWithTag(sourceShortcutTag(source)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `a shortcut opens the canonical page for the word`() {
        var opened: DictionaryShortcut? = null
        compose.setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), onOpenShortcut = { opened = it })
        }

        compose.onNodeWithTag(sourceShortcutTag(DictionarySource.REVERSO)).performScrollTo().performClick()

        assertEquals(
            "https://context.reverso.net/translation/english-french/blorple",
            opened?.url,
        )
    }

    @Test
    fun `an unknown word says so and can be dismissed`() {
        var dismissed = false
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onDismiss = { dismissed = true }) }

        compose.onNodeWithText("isn't in the offline dictionary", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `an inflected surface shows the lemma it resolved to`() {
        val entry = DictionaryEntry(
            lemma = "blorple",
            language = "en",
            partOfSpeech = "verb",
            pronunciation = null,
            audioUrl = null,
            senses = listOf(DictionarySense("To move swiftly.", listOf("blorper"))),
        )

        compose.setContent {
            Panel(WordLookup.Found("blorpled", "en", "blorple", listOf(entry)), term = "blorpled")
        }

        compose.onNodeWithText("blorpled").assertIsDisplayed()
        compose.onNodeWithText("blorple").assertIsDisplayed()
    }

    @Test
    fun `an entry without pronunciation or translations still shows its definition`() {
        val entry = DictionaryEntry(
            lemma = "vrok",
            language = "en",
            partOfSpeech = null,
            pronunciation = null,
            audioUrl = null,
            senses = listOf(DictionarySense("A word.", emptyList())),
        )

        compose.setContent { Panel(WordLookup.Found("vrok", "en", "vrok", listOf(entry))) }

        compose.onNodeWithText("A word.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the listen button invokes pronunciation`() {
        var spoken = false
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSpeak = { spoken = true }) }

        compose.onNodeWithTag(PRONOUNCE_TAG).performClick()

        assertTrue("the Listen button must reach the pronunciation seam", spoken)
    }

    @Test
    fun `a language with no voice shows an honest message`() {
        compose.setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), speech = SpeechResult.NoVoice("fr"))
        }

        compose.onNodeWithText("No voice is installed for \"fr\".", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an unavailable engine shows its message`() {
        compose.setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                speech = SpeechResult.Unavailable("No speech engine is available on this device."),
            )
        }

        compose.onNodeWithText("No speech engine is available on this device.").assertIsDisplayed()
    }

    @Test
    fun `saving from the panel saves at the default level`() {
        var saved: MasteryLevel? = null
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSave = { saved = it }) }

        compose.onNodeWithTag(SAVE_TAG).performScrollTo().performClick()

        assertEquals(MasteryLevel.FAMILIAR, saved)
    }

    @Test
    fun `tapping a mastery level saves at that level`() {
        var saved: MasteryLevel? = null
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSave = { saved = it }) }

        compose.onNodeWithTag(masteryTag(MasteryLevel.MASTERED)).performScrollTo().performClick()

        assertEquals(MasteryLevel.MASTERED, saved)
    }

    @Test
    fun `a saved word labels the save button and marks its level`() {
        val entry = VocabularyEntry(WordKey("en", "blorple"), "blorple", mastery = MasteryLevel.RECOGNIZED)
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), entry = entry) }

        compose.onNodeWithText("Saved").assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).performScrollTo().assertIsDisplayed()
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
                actions = WordLookupActions(onOpenShortcut = onOpenShortcut, onDismiss = onDismiss),
                pronunciation = Pronunciation(onSpeak = onSpeak, result = speech),
                vocabulary = VocabularyPanel(entry = entry, onSave = onSave),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
