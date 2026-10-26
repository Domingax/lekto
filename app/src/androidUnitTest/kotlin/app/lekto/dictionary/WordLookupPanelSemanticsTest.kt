package app.lekto.dictionary

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.lekto.theme.LektoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The word lookup sheet on a **simulated Android runtime** (issue #76): the
 * same-named twin of `app/desktopTest`'s `WordLookupPanelSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. It re-proves the sheet's
 * new grammar after issue #210 — the level is the save, the speaker and close are
 * icons, the references are one muted line — under Robolectric, on the runtime
 * that enforces Android's platform constraints.
 */
@RunWith(RobolectricTestRunner::class)
// `android-compileSdk`; Robolectric 4.16 supports API 36. The qualifiers give the
// sheet a phone-sized window (360 × 800 dp), so its 45 % cap leaves room for the
// senses and the test reads them where a reader would.
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@Suppress("TooManyFunctions") // One sheet, one test per outcome and control; splitting hides the panel's surface.
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
        compose.onNodeWithText("verb", substring = true).assertIsDisplayed()
        compose.onNodeWithText("/ˈblɔːpəl/", substring = true).assertIsDisplayed()
        compose.onNodeWithText("To move swiftly.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("→ blorper", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an offline lookup keeps its message and still offers the references`() {
        compose.setContent { Panel(WordLookup.Unavailable("The offline dictionary isn't installed yet.")) }

        compose.onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        DictionarySource.entries.forEach { source ->
            compose.onNodeWithTag(sourceShortcutTag(source)).assertIsDisplayed()
        }
    }

    @Test
    fun `a reference opens the canonical page for the word`() {
        var opened: DictionaryShortcut? = null
        compose.setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), onOpenShortcut = { opened = it })
        }

        compose.onNodeWithTag(sourceShortcutTag(DictionarySource.REVERSO)).performClick()

        assertEquals(
            "https://context.reverso.net/translation/english-french/blorple",
            opened?.url,
        )
    }

    @Test
    fun `every reference stays reachable`() {
        val opened = mutableListOf<DictionaryShortcut>()
        compose.setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), onOpenShortcut = { opened += it })
        }

        DictionarySource.entries.forEach { source ->
            compose.onNodeWithTag(sourceShortcutTag(source)).performClick()
        }

        assertEquals(DictionarySource.entries.toSet(), opened.map { it.source }.toSet())
    }

    @Test
    fun `an unknown word says so and can be closed from its icon`() {
        var dismissed = false
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onDismiss = { dismissed = true }) }

        compose.onNodeWithText("isn't in the offline dictionary", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(LOOKUP_CLOSE_TAG).performClick()

        assertTrue("the close icon must close the sheet", dismissed)
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
    fun `a word with several readings names every part of speech`() {
        val noun = DictionaryEntry(
            lemma = "blorple",
            language = "en",
            partOfSpeech = "noun",
            pronunciation = null,
            audioUrl = null,
            senses = listOf(DictionarySense("A thing.", emptyList())),
        )
        val verb = noun.copy(partOfSpeech = "verb", senses = listOf(DictionarySense("To act.", emptyList())))

        compose.setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(noun, verb))) }

        compose.onNodeWithText("noun · verb", substring = true).assertIsDisplayed()
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

        compose.onNodeWithText("A word.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the speaker is an icon that speaks the word`() {
        var spoken = false
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSpeak = { spoken = true }) }

        compose.onNodeWithTag(PRONOUNCE_TAG).performClick()

        assertTrue("the speaker must reach the pronunciation seam", spoken)
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
    fun `choosing a level saves at that level`() {
        var saved: MasteryLevel? = null
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSave = { saved = it }) }

        compose.onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).performClick()

        assertEquals(MasteryLevel.LEARNED, saved)
    }

    @Test
    fun `a missing dictionary still allows the save and says so`() {
        var saved: MasteryLevel? = null
        compose.setContent {
            Panel(WordLookup.Unavailable("The offline dictionary isn't installed yet."), onSave = { saved = it })
        }

        compose.onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()

        assertEquals(MasteryLevel.FAMILIAR, saved)
    }

    @Test
    fun `there is no separate save control`() {
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        assertTrue(
            "the level is the save; a second control would be two gestures for one intention",
            compose.onAllNodesWithTag("lookup-save").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue("no Save word either", compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the current level is named beside the chips`() {
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        compose.onNodeWithText("Recognized").assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).assertIsSelected()
    }

    @Test
    fun `a saved word's level is named and marked`() {
        val entry = VocabularyEntry(WordKey("en", "blorple"), "blorple", mastery = MasteryLevel.FAMILIAR)
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), entry = entry) }

        compose.onNodeWithText("Familiar").assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).assertIsSelected()
    }

    @Test
    fun `the sheet caps a long word at forty five percent of the screen`() {
        // Long enough that the content wants far more than the ceiling: this is
        // the case the cap exists for, and the case a short panel could pass by
        // accident.
        val senses = (1..24).map { index -> DictionarySense("Sense $index.", listOf("gloss $index")) }
        val entry = DictionaryEntry("blorple", "en", "verb", null, null, senses)
        compose.setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        val sheet = compose.onNodeWithTag(LOOKUP_PANEL_TAG).getUnclippedBoundsInRoot()
        val height = sheet.bottom.value - sheet.top.value
        val ceiling = PANEL_HEIGHT.value * SHEET_MAX_HEIGHT_FRACTION

        assertTrue(
            "the sheet is $height dp tall, above the $ceiling dp ceiling",
            height <= ceiling + 1f,
        )
        assertTrue(
            "the sheet is only $height dp tall, so the cap was not exercised against $ceiling dp",
            height >= ceiling - 1f,
        )
    }

    @Test
    fun `every level chip fits inside the sheet`() {
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        val sheet = compose.onNodeWithTag(LOOKUP_PANEL_TAG).getUnclippedBoundsInRoot()
        val firstChip = compose.onNodeWithTag(masteryTag(MasteryLevel.UNKNOWN)).getUnclippedBoundsInRoot()
        val lastChip = compose.onNodeWithTag(masteryTag(MasteryLevel.KNOWN)).getUnclippedBoundsInRoot()

        assertTrue(
            "the last chip ends at ${lastChip.right} dp, past the sheet's ${sheet.right} dp",
            lastChip.right.value <= sheet.right.value + 1f,
        )
        assertTrue(
            "the five chips must share one row; the last starts at ${lastChip.top} dp",
            abs(firstChip.top.value - lastChip.top.value) < 1f,
        )
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
        LektoTheme {
            WordLookupPanel(
                result = result,
                term = term,
                shortcuts = shortcuts,
                actions = WordLookupActions(onOpenShortcut = onOpenShortcut, onDismiss = onDismiss),
                pronunciation = Pronunciation(onSpeak = onSpeak, result = speech),
                vocabulary = VocabularyPanel(entry = entry, onSave = onSave),
                modifier = Modifier.size(width = PANEL_WIDTH, height = PANEL_HEIGHT),
            )
        }
    }

    private companion object {
        val PANEL_WIDTH = 360.dp
        val PANEL_HEIGHT = 640.dp
    }
}
