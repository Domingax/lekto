package app.lekto.dictionary

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
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
import app.lekto.ui.LOOKUP_HANDLE_TAG
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
 * is no separate Save control, and the footer states the result — a suggestion
 * while the word is unsaved, the fact once it is (issue #279).
 */
@OptIn(ExperimentalTestApi::class)
@Suppress(
    "TooManyFunctions", // One sheet, one test per outcome and control; splitting hides the panel's surface.
    "LargeClass", // The sheet's whole surface in one place: states, layout, references and drag.
)
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
    fun draggingTheHandleDownDismissesTheSheet() = runComposeUiTest {
        var dismissed = false
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), onDismiss = { dismissed = true }) }

        onNodeWithTag(LOOKUP_HANDLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, DRAG_DISTANCE))
            up()
        }

        assertTrue(dismissed, "dragging the handle down must close the sheet")
    }

    @Test
    fun theOfflineSheetOffersTheWayToInstallTheDictionary() = runComposeUiTest {
        var openedSettings = false
        setContent {
            Panel(
                WordLookup.Unavailable("The offline dictionary isn't installed yet."),
                onOpenSettings = { openedSettings = true },
            )
        }

        onNodeWithText("Install the offline dictionary").assertIsDisplayed()
        onNodeWithTag(LOOKUP_INSTALL_TAG).performClick()

        assertTrue(openedSettings, "the install action must reach the settings seam")
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
    fun anUnsavedWordSaysItIsNotSavedAndMarksNoChip() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        onNodeWithText("Not saved").assertIsDisplayed()
        MasteryLevel.entries.forEach { level ->
            onNodeWithTag(masteryTag(level)).assertIsNotSelected()
        }
    }

    @Test
    fun anUnsavedWordWithADerivedLevelSuggestsItAsLikely() = runComposeUiTest {
        setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), suggestedLevel = MasteryLevel.RECOGNIZED)
        }

        onNodeWithText("Likely Recognized").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).assertIsNotSelected()
    }

    @Test
    fun theFirstTapSavesAndTheFooterStatesTheFact() = runComposeUiTest {
        var saved by mutableStateOf<MasteryLevel?>(null)
        setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                entry = saved?.let { level -> blorpleEntry(level) },
                suggestedLevel = MasteryLevel.RECOGNIZED,
                onSave = { level -> saved = level },
            )
        }

        onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).performClick()

        onNodeWithText("Saved · Learned").assertIsDisplayed()
        onNodeWithText("Likely Recognized").assertDoesNotExist()
        onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).assertIsSelected()
    }

    @Test
    fun aLaterTapChangesTheLevelAndTheLineFollows() = runComposeUiTest {
        var saved by mutableStateOf<MasteryLevel?>(MasteryLevel.LEARNED)
        setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                entry = saved?.let { level -> blorpleEntry(level) },
                onSave = { level -> saved = level },
            )
        }

        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()

        onNodeWithText("Saved · Familiar").assertIsDisplayed()
        onNodeWithText("Saved · Learned").assertDoesNotExist()
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).assertIsSelected()
        onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).assertIsNotSelected()
    }

    @Test
    fun aFailedWriteSaysItCouldNotSaveAndNoChipClaimsASave() = runComposeUiTest {
        setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                suggestedLevel = MasteryLevel.RECOGNIZED,
                saveFailed = true,
            )
        }

        onNodeWithText("Couldn't save").assertIsDisplayed()
        onNodeWithText("Likely Recognized").assertDoesNotExist()
        MasteryLevel.entries.forEach { level ->
            onNodeWithTag(masteryTag(level)).assertIsNotSelected()
        }
    }

    @Test
    fun theSuggestionIsInTheMutedInkAndTheFactInTheInk() = runComposeUiTest {
        // The receipt's ink is part of the ticket (issue #279): a suggestion never
        // looks like a fact. Semantics carries the words, not their colour, so the
        // difference is read from the pixels of the same line in the two states.
        var saved by mutableStateOf<MasteryLevel?>(null)
        setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                entry = saved?.let { level -> blorpleEntry(level) },
                suggestedLevel = MasteryLevel.RECOGNIZED,
            )
        }

        val suggestion = stateLineInk()
        saved = MasteryLevel.LEARNED
        waitForIdle()
        val fact = stateLineInk()

        assertTrue(
            suggestion.luminance() > fact.luminance(),
            "the suggestion's ink ($suggestion) must be the muted one, the fact's ($fact) the darker ink",
        )
    }

    @Test
    fun theStateLineIsAnnouncedByAScreenReader() = runComposeUiTest {
        // The line changes under the reader's finger, so a polite live region
        // carries the receipt to a screen reader (issue #279's acceptance).
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        onNodeWithText("Not saved").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }

    @Test
    fun aSavedWordsLevelIsStatedAndMarked() = runComposeUiTest {
        setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), entry = blorpleEntry(MasteryLevel.FAMILIAR))
        }

        onNodeWithText("Saved · Familiar").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).assertIsSelected()
    }

    @Test
    fun aOneSenseWordOpensAShorterSheetThanTheOldFortyFivePercent() = runComposeUiTest {
        // A word with one sense and no translation: the content-sized sheet still
        // carries the header, the sense, the references and the pinned level, and
        // must open shorter than the old 45 % ceiling (issue #282).
        val entry = DictionaryEntry(
            lemma = "blorple",
            language = "en",
            partOfSpeech = "verb",
            pronunciation = null,
            audioUrl = null,
            senses = listOf(DictionarySense("To move swiftly.", emptyList())),
        )
        setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        val height = sheetHeight()

        assertTrue(
            height < PANEL_HEIGHT.value * LEGACY_SHEET_MAX_FRACTION,
            "the sheet is $height dp tall, so it still rations a one-sense word",
        )
    }

    @Test
    fun aThreeEntryWordRisesToTheSixtyPercentCapWithThreeDefinitionsInView() = runComposeUiTest {
        // Three readings: the first three definitions stand at the top, while the
        // third reading's further senses push the content past the cap.
        val parts = listOf("noun", "verb", "adjective")
        val entries = parts.mapIndexed { index, part ->
            val senses = listOf(DictionarySense("Definition ${index + 1}.", emptyList()))
            val overflow = if (index == parts.lastIndex) {
                (4..9).map { number -> DictionarySense("Definition $number.", listOf("gloss $number")) }
            } else {
                emptyList()
            }
            DictionaryEntry(
                lemma = "blorple",
                language = "en",
                partOfSpeech = part,
                pronunciation = null,
                audioUrl = null,
                senses = senses + overflow,
            )
        }
        setContent { Panel(WordLookup.Found("blorple", "en", "blorple", entries)) }

        val ceiling = PANEL_HEIGHT.value * SHEET_MAX_FRACTION
        assertEquals(
            ceiling,
            sheetHeight(),
            HEIGHT_TOLERANCE,
            "a three-entry word must rise to the ${ceiling}dp cap, not below it",
        )
        for (number in 1..DEFINITIONS_IN_VIEW) {
            onNodeWithText("Definition $number.").assertIsDisplayed()
        }
    }

    @Test
    fun aLandscapePhoneStillShowsTwoDefinitionLinesAndTheLevelSelector() = runComposeUiTest {
        // 640 × 360: the short side the old 45 % cap turned into a slit between the
        // header and the pinned footer.
        val senses = (1..2).map { index -> DictionarySense("Definition $index.", emptyList()) }
        val entry = DictionaryEntry("blorple", "en", "verb", null, null, senses)
        setContent {
            Panel(
                WordLookup.Found("blorple", "en", "blorple", listOf(entry)),
                width = 640.dp,
                height = 360.dp,
            )
        }

        onNodeWithText("Definition 1.").assertIsDisplayed()
        onNodeWithText("Definition 2.").assertIsDisplayed()
        onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).assertIsDisplayed()
    }

    @Test
    fun theReferencesScrollAtTheEndOfTheBodyAndNeverGrowTheSheet() = runComposeUiTest {
        val senses = (1..24).map { index -> DictionarySense("Sense $index.", listOf("gloss $index")) }
        val entry = DictionaryEntry("blorple", "en", "verb", null, null, senses)
        setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        val ceiling = PANEL_HEIGHT.value * SHEET_MAX_FRACTION
        val atRest = sheetHeight()
        assertEquals(ceiling, atRest, HEIGHT_TOLERANCE, "the long answer must sit at the cap before scrolling")

        val reference = onNodeWithTag(sourceShortcutTag(DictionarySource.GOOGLE_TRANSLATE))
        reference.performScrollTo().assertIsDisplayed()

        assertEquals(
            atRest,
            sheetHeight(),
            HEIGHT_TOLERANCE,
            "scrolling the references must not change the sheet's height",
        )
        val level = onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val scrolled = reference.getUnclippedBoundsInRoot()
        assertTrue(
            scrolled.bottom.value <= level.top.value + HEIGHT_TOLERANCE,
            "the references end at ${scrolled.bottom} dp, past the footer's ${level.top} dp",
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
    fun theColumnShapeStacksTheStateLineAboveItsChips() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en"), inColumn = true) }

        val state = onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val chip = onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).getUnclippedBoundsInRoot()

        onNodeWithText("Not saved").assertIsDisplayed()
        assertTrue(
            state.bottom.value <= chip.top.value + 1f,
            "the state line ends at ${state.bottom} dp, past the chips' ${chip.top} dp; it must stand above them",
        )
    }

    @Test
    fun theStateLineIsCentredWithTheChips() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        val state = onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val chip = onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).getUnclippedBoundsInRoot()
        val stateCentre = (state.top.value + state.bottom.value) / 2f
        val chipCentre = (chip.top.value + chip.bottom.value) / 2f

        assertTrue(
            abs(stateCentre - chipCentre) < 1f,
            "the state line centres at $stateCentre dp, the chips at $chipCentre dp",
        )
    }

    @Test
    fun theReferencesStartAtTheContentEdge() = runComposeUiTest {
        setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        val state = onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val reference = onNodeWithTag(sourceShortcutTag(DictionarySource.WORD_REFERENCE)).getUnclippedBoundsInRoot()

        assertTrue(
            abs(state.left.value - reference.left.value) < 1f,
            "the references start at ${reference.left} dp, the content at ${state.left} dp",
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
        suggestedLevel: MasteryLevel? = null,
        saveFailed: Boolean = false,
        onOpenShortcut: (DictionaryShortcut) -> Unit = {},
        onSpeak: () -> Unit = {},
        onSave: (MasteryLevel) -> Unit = {},
        onDismiss: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        width: Dp = PANEL_WIDTH,
        height: Dp = PANEL_HEIGHT,
        inColumn: Boolean = false,
    ) {
        LektoTheme {
            WordLookupPanel(
                result = result,
                term = term,
                shortcuts = shortcuts,
                actions = WordLookupActions(onOpenShortcut = onOpenShortcut, onDismiss = onDismiss),
                pronunciation = Pronunciation(onSpeak = onSpeak, result = speech),
                vocabulary = VocabularyPanel(
                    entry = entry,
                    onSave = onSave,
                    suggestedLevel = suggestedLevel,
                    saveFailed = saveFailed,
                ),
                onOpenSettings = onOpenSettings,
                modifier = Modifier.size(width = width, height = height),
                inColumn = inColumn,
            )
        }
    }

    /** The sheet's measured height in dp, from its bounds in the test root. */
    private fun ComposeUiTest.sheetHeight(): Float {
        val sheet = onNodeWithTag(LOOKUP_PANEL_TAG).getUnclippedBoundsInRoot()
        return sheet.bottom.value - sheet.top.value
    }

    /** The saved entry for the panel's word at [level]. */
    private fun blorpleEntry(level: MasteryLevel): VocabularyEntry =
        VocabularyEntry(WordKey("en", "blorple"), "blorple", mastery = level)

    /**
     * The darkest pixel the state line draws, which is its ink: an antialiased edge
     * only lightens a glyph, so the strongest pixel carries the colour the panel
     * chose.
     */
    private fun ComposeUiTest.stateLineInk(): Color {
        val pixels = onNodeWithTag(LOOKUP_LEVEL_TAG).captureToImage().toPixelMap()
        var darkest = Color.White
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (pixel.luminance() < darkest.luminance()) darkest = pixel
            }
        }
        return darkest
    }

    private companion object {
        val PANEL_WIDTH = 360.dp
        val PANEL_HEIGHT = 640.dp

        /** The most of the screen the lookup sheet may cover (docs/design.md §7.3). */
        const val SHEET_MAX_FRACTION = 0.60f

        /** The ceiling this change replaced; a one-sense word must now open well below it. */
        const val LEGACY_SHEET_MAX_FRACTION = 0.45f

        /** Bounds are read as floats, so a dp of slack separates "at the cap" from "missing it". */
        const val HEIGHT_TOLERANCE = 1f

        /** The drag the dismissal test injects: comfortably past the handle's threshold. */
        const val DRAG_DISTANCE = 160f

        /** The definitions the three-entry case must show before any scrolling. */
        const val DEFINITIONS_IN_VIEW = 3
    }
}
