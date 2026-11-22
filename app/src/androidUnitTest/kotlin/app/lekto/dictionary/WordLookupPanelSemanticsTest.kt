package app.lekto.dictionary

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
 * grammar after issue #210 — the level is the save, the speaker and close are
 * icons, the references are one muted line — and the footer's state line after
 * issue #279, under Robolectric, on the runtime that enforces Android's platform
 * constraints.
 */
@RunWith(RobolectricTestRunner::class)
// `android-compileSdk`; Robolectric 4.16 supports API 36. The qualifiers give the
// sheet a phone-sized window (360 × 800 dp), so its 60 % cap leaves room for the
// senses and the test reads them where a reader would.
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@Suppress(
    "TooManyFunctions", // One sheet, one test per outcome and control; splitting hides the panel's surface.
    "LargeClass", // The sheet's whole surface in one place: states, layout, references and drag.
)
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
    fun `dragging the handle down dismisses the sheet`() {
        var dismissed = false
        compose.setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), onDismiss = { dismissed = true })
        }

        compose.onNodeWithTag(LOOKUP_HANDLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, DRAG_DISTANCE))
            up()
        }

        assertTrue("dragging the handle down must close the sheet", dismissed)
    }

    @Test
    fun `the offline sheet offers the way to install the dictionary`() {
        var openedSettings = false
        compose.setContent {
            Panel(
                WordLookup.Unavailable("The offline dictionary isn't installed yet."),
                onOpenSettings = { openedSettings = true },
            )
        }

        compose.onNodeWithText("Install the offline dictionary").assertIsDisplayed()
        compose.onNodeWithTag(LOOKUP_INSTALL_TAG).performClick()

        assertTrue("the install action must reach the settings seam", openedSettings)
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
    fun `an unsaved word says it is not saved and marks no chip`() {
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        compose.onNodeWithText("Not saved").assertIsDisplayed()
        MasteryLevel.entries.forEach { level ->
            compose.onNodeWithTag(masteryTag(level)).assertIsNotSelected()
        }
    }

    @Test
    fun `an unsaved word with a derived level suggests it as likely`() {
        compose.setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), suggestedLevel = MasteryLevel.RECOGNIZED)
        }

        compose.onNodeWithText("Likely Recognized").assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).assertIsNotSelected()
    }

    @Test
    fun `the first tap saves and the footer states the fact`() {
        var saved by mutableStateOf<MasteryLevel?>(null)
        compose.setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                entry = saved?.let { level -> blorpleEntry(level) },
                suggestedLevel = MasteryLevel.RECOGNIZED,
                onSave = { level -> saved = level },
            )
        }

        compose.onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).performClick()

        compose.onNodeWithText("Saved · Learned").assertIsDisplayed()
        compose.onNodeWithText("Likely Recognized").assertDoesNotExist()
        compose.onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).assertIsSelected()
    }

    @Test
    fun `a later tap changes the level and the line follows`() {
        var saved by mutableStateOf<MasteryLevel?>(MasteryLevel.LEARNED)
        compose.setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                entry = saved?.let { level -> blorpleEntry(level) },
                onSave = { level -> saved = level },
            )
        }

        compose.onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()

        compose.onNodeWithText("Saved · Familiar").assertIsDisplayed()
        compose.onNodeWithText("Saved · Learned").assertDoesNotExist()
        compose.onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).assertIsSelected()
        compose.onNodeWithTag(masteryTag(MasteryLevel.LEARNED)).assertIsNotSelected()
    }

    @Test
    fun `a failed write says it could not save and no chip claims a save`() {
        compose.setContent {
            Panel(
                WordLookup.NotInDictionary("zzzz", "en"),
                suggestedLevel = MasteryLevel.RECOGNIZED,
                saveFailed = true,
            )
        }

        compose.onNodeWithText("Couldn't save").assertIsDisplayed()
        compose.onNodeWithText("Likely Recognized").assertDoesNotExist()
        MasteryLevel.entries.forEach { level ->
            compose.onNodeWithTag(masteryTag(level)).assertIsNotSelected()
        }
    }

    @Test
    fun `the state line is announced by a screen reader`() {
        // The line changes under the reader's finger, so a polite live region
        // carries the receipt to a screen reader (issue #279's acceptance).
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        compose.onNodeWithText("Not saved").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }

    @Test
    fun `a saved word's level is stated and marked`() {        compose.setContent {
            Panel(WordLookup.NotInDictionary("zzzz", "en"), entry = blorpleEntry(MasteryLevel.FAMILIAR))
        }

        compose.onNodeWithText("Saved · Familiar").assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).assertIsSelected()
    }

    @Test
    fun `a one sense word opens a shorter sheet than the old forty five percent`() {
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
        compose.setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        val height = sheetHeight()

        assertTrue(
            "the sheet is $height dp tall, so it still rations a one-sense word",
            height < PANEL_HEIGHT.value * LEGACY_SHEET_MAX_FRACTION,
        )
    }

    @Test
    fun `a three entry word rises to the sixty percent cap with three definitions in view`() {
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
        compose.setContent { Panel(WordLookup.Found("blorple", "en", "blorple", entries)) }

        val ceiling = PANEL_HEIGHT.value * SHEET_MAX_FRACTION
        assertEquals(
            "a three-entry word must rise to the ${ceiling}dp cap, not below it",
            ceiling,
            sheetHeight(),
            HEIGHT_TOLERANCE,
        )
        for (number in 1..DEFINITIONS_IN_VIEW) {
            compose.onNodeWithText("Definition $number.").assertIsDisplayed()
        }
    }

    @Test
    fun `a landscape phone still shows two definition lines and the level selector`() {
        // 640 × 360: the short side the old 45 % cap turned into a slit between the
        // header and the pinned footer.
        val senses = (1..2).map { index -> DictionarySense("Definition $index.", emptyList()) }
        val entry = DictionaryEntry("blorple", "en", "verb", null, null, senses)
        compose.setContent {
            Panel(
                WordLookup.Found("blorple", "en", "blorple", listOf(entry)),
                width = 640.dp,
                height = 360.dp,
            )
        }

        compose.onNodeWithText("Definition 1.").assertIsDisplayed()
        compose.onNodeWithText("Definition 2.").assertIsDisplayed()
        compose.onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).assertIsDisplayed()
    }

    @Test
    fun `the references scroll at the end of the body and never grow the sheet`() {
        val senses = (1..24).map { index -> DictionarySense("Sense $index.", listOf("gloss $index")) }
        val entry = DictionaryEntry("blorple", "en", "verb", null, null, senses)
        compose.setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        val ceiling = PANEL_HEIGHT.value * SHEET_MAX_FRACTION
        val atRest = sheetHeight()
        assertEquals("the long answer must sit at the cap before scrolling", ceiling, atRest, HEIGHT_TOLERANCE)

        val reference = compose.onNodeWithTag(sourceShortcutTag(DictionarySource.GOOGLE_TRANSLATE))
        reference.performScrollTo().assertIsDisplayed()

        assertEquals(
            "scrolling the references must not change the sheet's height",
            atRest,
            sheetHeight(),
            HEIGHT_TOLERANCE,
        )
        val level = compose.onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val scrolled = reference.getUnclippedBoundsInRoot()
        assertTrue(
            "the references end at ${scrolled.bottom} dp, past the footer's ${level.top} dp",
            scrolled.bottom.value <= level.top.value + HEIGHT_TOLERANCE,
        )
    }

    @Test
    fun `a two digit sense number stays on one line`() {
        val senses = (1..12).map { index -> DictionarySense("Sense $index.", emptyList()) }
        val entry = DictionaryEntry("blorple", "en", "verb", null, null, senses)
        compose.setContent { Panel(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        val one = compose.onNodeWithTag(senseNumberTag(1)).getUnclippedBoundsInRoot()
        val twelve = compose.onNodeWithTag(senseNumberTag(12)).getUnclippedBoundsInRoot()
        val oneHeight = one.bottom.value - one.top.value
        val twelveHeight = twelve.bottom.value - twelve.top.value

        assertTrue(
            "the number 12 is $twelveHeight dp tall against 1's $oneHeight dp, so it wrapped",
            abs(oneHeight - twelveHeight) < 1f,
        )
    }

    @Test
    fun `the state line is centred with the chips`() {
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        val state = compose.onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val chip = compose.onNodeWithTag(masteryTag(MasteryLevel.RECOGNIZED)).getUnclippedBoundsInRoot()
        val stateCentre = (state.top.value + state.bottom.value) / 2f
        val chipCentre = (chip.top.value + chip.bottom.value) / 2f

        assertTrue(
            "the state line spans ${state.top}..${state.bottom} dp, the chip ${chip.top}..${chip.bottom} dp",
            abs(stateCentre - chipCentre) < 1f,
        )
    }

    @Test
    fun `the references start at the content edge`() {
        compose.setContent { Panel(WordLookup.NotInDictionary("zzzz", "en")) }

        val state = compose.onNodeWithTag(LOOKUP_LEVEL_TAG).getUnclippedBoundsInRoot()
        val reference =
            compose.onNodeWithTag(sourceShortcutTag(DictionarySource.WORD_REFERENCE)).getUnclippedBoundsInRoot()

        assertTrue(
            "the references start at ${reference.left} dp, the content at ${state.left} dp",
            abs(state.left.value - reference.left.value) < 1f,
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
        suggestedLevel: MasteryLevel? = null,
        saveFailed: Boolean = false,
        onOpenShortcut: (DictionaryShortcut) -> Unit = {},
        onSpeak: () -> Unit = {},
        onSave: (MasteryLevel) -> Unit = {},
        onDismiss: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        width: Dp = PANEL_WIDTH,
        height: Dp = PANEL_HEIGHT,
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
            )
        }
    }

    /** The sheet's measured height in dp, from its bounds in the test root. */
    private fun sheetHeight(): Float {
        val sheet = compose.onNodeWithTag(LOOKUP_PANEL_TAG).getUnclippedBoundsInRoot()
        return sheet.bottom.value - sheet.top.value
    }

    /** The saved entry for the panel's word at [level]. */
    private fun blorpleEntry(level: MasteryLevel): VocabularyEntry =
        VocabularyEntry(WordKey("en", "blorple"), "blorple", mastery = level)

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
