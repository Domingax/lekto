package app.lekto.dictionary

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.lekto.theme.LektoTheme
import app.lekto.ui.LOOKUP_SHEET_MAX_HEIGHT_FRACTION
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The word lookup sheet through semantics (issue #19, reshaped by issue #210): a
 * known word shows its pronunciation, definitions and translations beside the
 * reference line; an unknown word and an offline lookup keep their honest message
 * and the references still stand; the speaker, the close and every reference stay
 * reachable. The grammar itself is the ticket: **choosing a level saves**, there
 * is no separate Save control, and the current level is named beside the chips.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One sheet, one test per outcome and control; splitting hides the panel's surface.
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
        onNodeWithText("verb", substring = true).assertIsDisplayed()
        onNodeWithText("/ˈblɔːpəl/", substring = true).assertIsDisplayed()
        onNodeWithText("To move swiftly.", substring = true).assertIsDisplayed()
        onNodeWithText("→ blorper", substring = true).assertIsDisplayed()
    }

    @Test
    fun anOfflineLookupKeepsItsMessageAndStillOffersTheReferences() = runComposeUiTest {
        setContent { Panel(WordLookup.Unavailable("The offline dictionary isn't installed yet.")) }

        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        DictionarySource.entries.forEach { source ->
            onNodeWithTag(sourceShortcutTag(source)).assertIsDisplayed()
        }
    }

    @Test
    fun aReferenceOpensTheCanonicalPageForTheWord() = runComposeUiTest {
        var opened: DictionaryShortcut? = null
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onOpenShortcut = { opened = it }) }

        onNodeWithTag(sourceShortcutTag(DictionarySource.REVERSO)).performClick()

        assertEquals(
            "https://context.reverso.net/translation/english-french/blorple",
            opened?.url,
        )
    }

    @Test
    fun everyReferenceStaysReachable() = runComposeUiTest {
        val opened = mutableListOf<DictionaryShortcut>()
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onOpenShortcut = { opened += it }) }

        DictionarySource.entries.forEach { source ->
            onNodeWithTag(sourceShortcutTag(source)).performClick()
        }

        assertEquals(DictionarySource.entries.toSet(), opened.map { it.source }.toSet())
    }

    @Test
    fun anUnknownWordSaysSoAndCanBeClosedFromItsIcon() = runComposeUiTest {
        var dismissed = false
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onDismiss = { dismissed = true }) }

        onNodeWithText("isn't in the offline dictionary", substring = true).assertIsDisplayed()
        onNodeWithTag(LOOKUP_CLOSE_TAG).performClick()

        assertTrue(dismissed, "the close icon must close the sheet")
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
    fun aWordWithSeveralReadingsNamesEveryPartOfSpeech() = runComposeUiTest {
        val noun = DictionaryEntry(
            lemma = "blorple",
            language = "en",
            partOfSpeech = "noun",
            pronunciation = null,
            audioUrl = null,
            senses = listOf(DictionarySense("A thing.", emptyList())),
        )
        val verb = noun.copy(partOfSpeech = "verb", senses = listOf(DictionarySense("To act.", emptyList())))

        setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(noun, verb))) }

        onNodeWithText("noun · verb", substring = true).assertIsDisplayed()
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
    fun theSpeakerIsAnIconThatSpeaksTheWord() = runComposeUiTest {
        var spoken = false
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSpeak = { spoken = true }) }

        onNodeWithTag(PRONOUNCE_TAG).performClick()

        assertTrue(spoken, "the speaker must reach the pronunciation seam")
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
    fun choosingALevelSavesAtThatLevel() = runComposeUiTest {
        var saved: MasteryLevel? = null
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onSave = { saved = it }) }

        onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).performClick()

        assertEquals(MasteryLevel.LEARNED, saved)
    }

    @Test
    fun aMissingDictionaryStillAllowsTheSaveAndSaysSo() = runComposeUiTest {
        var saved: MasteryLevel? = null
        setContent {
            Panel(WordLookup.Unavailable("The offline dictionary isn't installed yet."), onSave = { saved = it })
        }

        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()

        assertEquals(MasteryLevel.FAMILIAR, saved)
    }

    @Test
    fun thereIsNoSeparateSaveControl() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        assertTrue(
            onAllNodesWithTag("lookup-save").fetchSemanticsNodes().isEmpty(),
            "the level is the save; a second control would be two gestures for one intention",
        )
        assertTrue(onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty(), "no Save word either")
    }

    @Test
    fun theCurrentLevelIsNamedBesideTheChips() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        onNodeWithText("Recognized").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).assertIsSelected()
    }

    @Test
    fun aSavedWordsLevelIsNamedAndMarked() = runComposeUiTest {
        val entry = VocabularyEntry(WordKey("en", "blorple"), "blorple", mastery = MasteryLevel.FAMILIAR)
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), entry = entry) }

        onNodeWithText("Familiar").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).assertIsSelected()
    }

    @Test
    fun theSheetCapsALongWordAtFortyFivePercentOfTheScreen() = runComposeUiTest {
        // Long enough that the content wants far more than the ceiling: this is
        // the case the cap exists for, and the case a short panel could pass by
        // accident.
        val senses = (1..24).map { index -> DictionarySense("Sense $index.", listOf("gloss $index")) }
        val entry = DictionaryEntry("blorple", "en", "verb", null, null, senses)
        setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        val sheet = onNodeWithTag(LOOKUP_PANEL_TAG).getUnclippedBoundsInRoot()
        val height = sheet.bottom.value - sheet.top.value
        val ceiling = PANEL_HEIGHT.value * LOOKUP_SHEET_MAX_HEIGHT_FRACTION

        assertTrue(
            height <= ceiling + 1f,
            "the sheet is $height dp tall, above the $ceiling dp ceiling",
        )
        assertTrue(
            height >= ceiling - 1f,
            "the sheet is only $height dp tall, so the cap was not exercised against $ceiling dp",
        )
    }

    @Test
    fun aTwoDigitSenseNumberStaysOnOneLine() = runComposeUiTest {
        val senses = (1..12).map { index -> DictionarySense("Sense $index.", emptyList()) }
        val entry = DictionaryEntry("blorple", "en", "verb", null, null, senses)
        setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        val one = onNodeWithTag(senseNumberTag(1)).getUnclippedBoundsInRoot()
        val twelve = onNodeWithTag(senseNumberTag(12)).getUnclippedBoundsInRoot()
        val oneHeight = one.bottom.value - one.top.value
        val twelveHeight = twelve.bottom.value - twelve.top.value

        assertTrue(
            abs(oneHeight - twelveHeight) < 1f,
            "the number 12 is $twelveHeight dp tall against 1's $oneHeight dp, so it wrapped",
        )
    }

    @Test
    fun theLevelLabelIsCentredWithTheChips() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        val label = onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val chip = onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).getUnclippedBoundsInRoot()
        val labelCentre = (label.top.value + label.bottom.value) / 2f
        val chipCentre = (chip.top.value + chip.bottom.value) / 2f

        assertTrue(
            abs(labelCentre - chipCentre) < 1f,
            "the level label centres at $labelCentre dp, the chips at $chipCentre dp",
        )
    }

    @Test
    fun theReferencesStartAtTheContentEdge() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        val label = onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val reference = onNodeWithTag(sourceShortcutTag(DictionarySource.WORD_REFERENCE)).getUnclippedBoundsInRoot()

        assertTrue(
            abs(label.left.value - reference.left.value) < 1f,
            "the references start at ${reference.left} dp, the content at ${label.left} dp",
        )
    }

    @Test
    fun everyLevelChipFitsInsideTheSheet() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        val sheet = onNodeWithTag(LOOKUP_PANEL_TAG).getUnclippedBoundsInRoot()
        val firstChip = onNodeWithTag(masteryTag(MasteryLevel.UNKNOWN)).getUnclippedBoundsInRoot()
        val lastChip = onNodeWithTag(masteryTag(MasteryLevel.KNOWN)).getUnclippedBoundsInRoot()

        assertTrue(
            lastChip.right.value <= sheet.right.value + 1f,
            "the last chip ends at ${lastChip.right} dp, past the sheet's ${sheet.right} dp",
        )
        assertTrue(
            abs(firstChip.top.value - lastChip.top.value) < 1f,
            "the five chips must share one row; the last starts at ${lastChip.top} dp",
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
