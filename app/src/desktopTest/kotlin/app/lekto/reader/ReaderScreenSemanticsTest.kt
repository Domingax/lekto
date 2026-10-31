package app.lekto.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The reader screen's behaviour through semantics and injected taps: it renders
 * the sample chapter, page navigation works in both directions (buttons and the
 * left/right tap zones), a tap on the middle recedes the chrome and returns it,
 * and it opens at a saved position (issue #16). A phone-sized viewport is forced
 * so the sample spans several pages deterministically and the chrome fits.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber", "TooManyFunctions") // The tap coordinates, and one test per behaviour the reader owns.
class ReaderScreenSemanticsTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    private companion object {
        /** The back arrow, the **Aa** control and the two page chevrons: the screen's own controls. */
        const val CHROME_BUTTONS = 4

        /** One short line, so the page has blank space below it for a centre tap. */
        val SHORT_CHAPTER = ReaderText(
            title = "Short",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Hello.")))),
        )
    }

    @Test
    fun rendersWordsOnTheFirstPage() = runComposeUiTest {
        setContent { Reader(renderer) }

        onNodeWithText("On the quiet evening", substring = true).assertIsDisplayed()
    }

    @Test
    fun aTapOnAWordAsksForThatWordLookup() = runComposeUiTest {
        var tapped: WordTap? = null
        setContent { Reader(renderer, onWordTap = { tap -> tapped = tap }) }

        // Words carry no link of their own (ADR-0007): the page hit-tests the tap
        // against its tokens, so a tap anywhere on a word finds it.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(firstWordTapPoint()) }

        val word = assertNotNull(tapped, "a tap on a word must ask for its lookup")
        assertEquals("The", word.token.surface)
        assertNotNull(word.contextSentence, "the tap carries the sentence it was found in")
    }

    /**
     * The reader's two bands (docs/design.md §7.2): the back arrow, the book's
     * title and the **Aa** control above; the two chevrons and the position below.
     * The chevrons mark the tap zones and are controls too, disabled at a bound,
     * so a tap turns the page like the zone under them.
     */
    @Test
    fun theBandsCarryTheBackTitleAaAndThePosition() = runComposeUiTest {
        setContent { Reader(renderer) }

        onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
        onNodeWithTag(READER_TITLE_TAG).assertTextEquals(SampleChapter.chapter.title)
        onNodeWithTag(READING_PREFERENCES_TAG).assertIsDisplayed()
        assertEquals(0, progressPercent())
        onNodeWithTag(READER_PREVIOUS_PAGE_TAG).assertIsDisplayed().assertIsNotEnabled()
        onNodeWithTag(READER_NEXT_PAGE_TAG).assertIsDisplayed().assertIsEnabled()

        onNodeWithTag(READER_NEXT_PAGE_TAG).performClick()
        assertTrue(progressPercent() > 0, "page two is further through the book than page one")

        onNodeWithTag(READER_PREVIOUS_PAGE_TAG).performClick()
        assertEquals(0, progressPercent())
    }

    /**
     * The page carries the two turns as accessibility actions beside the two
     * chevrons, so a screen-reader or switch user has a way through the book
     * without hunting for the bottom band.
     */
    @Test
    fun thePageOffersTheTwoPageTurnsToAScreenReader() = runComposeUiTest {
        setContent { Reader(renderer) }

        val actions = onNodeWithText("On the quiet evening", substring = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]

        assertEquals(listOf("Previous page", "Next page"), actions.map { action -> action.label })
    }

    @Test
    fun theRightZoneTurnsThePageAndTheLeftZoneTurnsItBack() = runComposeUiTest {
        setContent { Reader(renderer) }
        assertEquals(0, progressPercent())

        // A tap in the right third advances; in the left third it goes back.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        val second = progressPercent()
        assertTrue(second > 0, "the right zone turns forward")

        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(4f, center.y)) }
        assertEquals(0, progressPercent())
    }

    @Test
    fun tappingTheCentreRecedesAndReturnsTheChrome() = runComposeUiTest {
        setContent { Reader(renderer, chapter = SHORT_CHAPTER) }
        onNodeWithText("Hello.").assertIsDisplayed()
        onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()

        // The short chapter leaves the middle of the page free of glyphs, so the
        // centre tap is a chrome toggle rather than a word lookup.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        onNodeWithTag(READER_POSITION_TAG).assertDoesNotExist()
        onNodeWithTag(READER_BACK_TAG).assertDoesNotExist()
        onNodeWithText("Hello.").assertIsDisplayed()

        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()
        onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
    }

    @Test
    fun opensAtTheSavedPosition() = runComposeUiTest {
        // A position at the far end of the text must open on the last page, not
        // the first: a resumed session is exactly where the user left it.
        setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        assertTrue(progressPercent() >= 90, "a session saved at the end opens near the end")
        onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()
        onNodeWithTag(READER_PREVIOUS_PAGE_TAG).assertIsEnabled()
        onNodeWithTag(READER_NEXT_PAGE_TAG).assertIsNotEnabled()
    }

    @Test
    fun reachingTheLastPageDoesNotOpenTheEndState() = runComposeUiTest {
        setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        onNodeWithTag(READER_END_PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun nextOnTheLastPageOpensTheEndState() = runComposeUiTest {
        setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }
        onNodeWithTag(READER_NEXT_PAGE_TAG).assertIsNotEnabled()

        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }

        onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
        onNodeWithText("THE END").assertIsDisplayed()
        onNodeWithText(SampleChapter.chapter.title).assertIsDisplayed()
        onNodeWithTag(READER_MARK_FINISHED_TAG).assertIsDisplayed()
        onNodeWithTag(READER_BACK_TO_LIBRARY_TAG).assertIsDisplayed()
        // The state hides the reader's chrome.
        onNodeWithTag(READER_POSITION_TAG).assertDoesNotExist()
        onNodeWithTag(READER_BACK_TAG).assertDoesNotExist()
    }

    @Test
    fun markingFinishedKeepsThePanelOpenAndUndoFlipsItBack() = runComposeUiTest {
        var marked = 0
        var undone = 0
        setContent {
            Reader(
                renderer,
                initialOffset = Int.MAX_VALUE,
                onMarkFinished = { marked++ },
                onMarkUnfinished = { undone++ },
            )
        }
        openEndState()

        onNodeWithTag(READER_MARK_FINISHED_TAG).performClick()

        assertEquals(1, marked)
        onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
        onNodeWithText("Finished").assertIsDisplayed()
        onNodeWithTag(READER_BACK_TO_LIBRARY_TAG).assertIsDisplayed()
        onNodeWithTag(READER_MARK_UNFINISHED_TAG).assertIsDisplayed()
        onNodeWithTag(READER_MARK_FINISHED_TAG).assertDoesNotExist()

        onNodeWithTag(READER_MARK_UNFINISHED_TAG).performClick()

        assertEquals(1, undone)
        onNodeWithTag(READER_MARK_FINISHED_TAG).assertIsDisplayed()
        onNodeWithTag(READER_MARK_UNFINISHED_TAG).assertDoesNotExist()
    }

    @Test
    fun aFinishedSessionOpensTheEndStateAlreadyMarked() = runComposeUiTest {
        setContent { Reader(renderer, initialOffset = Int.MAX_VALUE, initiallyFinished = true) }
        openEndState()

        onNodeWithText("Finished").assertIsDisplayed()
        onNodeWithTag(READER_MARK_UNFINISHED_TAG).assertIsDisplayed()
    }

    @Test
    fun markingFinishedNeverMovesThePosition() = runComposeUiTest {
        val reported = mutableListOf<Int>()
        setContent {
            Reader(
                renderer,
                initialOffset = Int.MAX_VALUE,
                onPositionChange = { offset -> reported += offset },
                onMarkFinished = {},
            )
        }
        openEndState()

        onNodeWithTag(READER_MARK_FINISHED_TAG).performClick()

        assertTrue(reported.isEmpty(), "declaring finished is not a page turn: $reported")
    }

    @Test
    fun backToLibraryLeavesTheReader() = runComposeUiTest {
        var left = false
        setContent { Reader(renderer, initialOffset = Int.MAX_VALUE, onBack = { left = true }) }
        openEndState()

        onNodeWithTag(READER_BACK_TO_LIBRARY_TAG).performClick()

        assertTrue(left, "Back to Library leaves for the Library")
    }

    @Test
    fun aPageTurnReportsItsPosition() = runComposeUiTest {
        var reported = -1
        setContent { Reader(renderer, onPositionChange = { offset -> reported = offset }) }
        onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()

        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }

        assertTrue(reported > 0, "turning to page two must report a non-zero character offset")
    }

    @Test
    fun selectingAPhraseByLongPressDragReportsIt() = runComposeUiTest {
        var selected: PhraseSelection? = null
        setContent { Reader(renderer, onPhraseSelected = { chosen -> selected = chosen }) }

        onNodeWithText("On the quiet evening", substring = true).assertIsDisplayed()
        val start = firstWordTapPoint()
        onNodeWithTag(READER_PAGE_TAG).performTouchInput {
            down(start)
            advanceEventTime(1000)
            moveTo(Offset(start.x + 160f, start.y))
            up()
        }

        val phrase = assertNotNull(selected, "a long-press drag must select a phrase")
        val chapter = SampleChapter.chapter.blocks.joinToString("\n\n") { block -> block.text }
        assertTrue(phrase.text.contains(" "), "a drag across the line must select more than one word: ${phrase.text}")
        assertTrue(chapter.contains(phrase.text), "the phrase must come from the chapter: ${phrase.text}")
    }

    @Test
    fun theTapZonesAreThirdsOfTheWidth() {
        val width = 300
        assertEquals(ReaderTapZone.PREVIOUS, readerTapZone(0f, width))
        assertEquals(ReaderTapZone.PREVIOUS, readerTapZone(99f, width))
        assertEquals(ReaderTapZone.CHROME, readerTapZone(150f, width))
        assertEquals(ReaderTapZone.NEXT, readerTapZone(201f, width))
        assertEquals(ReaderTapZone.NEXT, readerTapZone(300f, width))
    }

    @Composable
    @Suppress("LongParameterList") // The reader's inputs are the test's knobs; a bundle would only hide them.
    private fun Reader(
        renderer: ReaderRenderer,
        chapter: ReaderText = SampleChapter.chapter,
        initialOffset: Int = 0,
        initiallyFinished: Boolean = false,
        onBack: () -> Unit = {},
        onPositionChange: (Int) -> Unit = {},
        onWordTap: (WordTap) -> Unit = {},
        onPhraseSelected: (PhraseSelection) -> Unit = {},
        onMarkFinished: () -> Unit = {},
        onMarkUnfinished: () -> Unit = {},
    ) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(chapter, renderer, initialOffset),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(
                    onWordTap = onWordTap,
                    onPhraseSelected = onPhraseSelected,
                    onBack = onBack,
                    onPositionChange = onPositionChange,
                    onMarkFinished = onMarkFinished,
                    onMarkUnfinished = onMarkUnfinished,
                ),
                initiallyFinished = initiallyFinished,
            )
        }
    }

    /** Taps the next zone on the last page, which is the only way the end state opens. */
    private fun ComposeUiTest.openEndState() {
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
    }
}

/** The **Reading progress** the position line shows, as a whole percentage. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.progressPercent(): Int {
    val text = onNodeWithTag(READER_POSITION_TAG).fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString(" ") { line -> line.text }
    return Regex("(\\d+)\\s*%").find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: error("no percentage in the position: $text")
}

/** The first word link's centre, in the reader page's coordinates. */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // A few points into the text's first line.
private fun ComposeUiTest.firstWordTapPoint(): Offset {
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val text = onNodeWithTag(READER_PAGE_TEXT_TAG).fetchSemanticsNode().boundsInRoot
    // A few points inside the text's first line, which starts with the first word.
    return Offset(text.left - page.left + 8f, text.top - page.top + 8f)
}
