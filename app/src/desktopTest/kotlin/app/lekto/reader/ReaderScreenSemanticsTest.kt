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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
@Suppress("MagicNumber") // The tap coordinates and the thirds of the width are the test's parameters.
class ReaderScreenSemanticsTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    private companion object {
        /** The back arrow and the **Aa** control: the clickable nodes the screen adds itself. */
        const val CHROME_BUTTONS = 2

        /** One short line, so the page has blank space below it for a centre tap. */
        val SHORT_CHAPTER = ReaderChapter(
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
    fun eachWordIsAFocusableLink() = runComposeUiTest {
        setContent { Reader(renderer) }

        // The chrome contributes the back arrow and the **Aa** control; every
        // word adds another clickable node, so the count proves the word layer is
        // tappable and reachable, not just painted.
        val clickables = onAllNodes(hasClickAction()).fetchSemanticsNodes()

        assertTrue(clickables.size > CHROME_BUTTONS, "expected word links beyond the chrome, found ${clickables.size}")
    }

    /**
     * The reader's two bands (docs/design.md §7.2): the back arrow, the book's
     * title and the **Aa** control above; the two tap-zone indicators and the
     * position below. The chevrons show where the thirds are, so they carry no
     * click action of their own.
     */
    @Test
    fun theBandsCarryTheBackTitleAaAndThePosition() = runComposeUiTest {
        setContent { Reader(renderer) }

        onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
        onNodeWithTag(READER_TITLE_TAG).assertTextEquals(SampleChapter.chapter.title)
        onNodeWithTag(READING_PREFERENCES_TAG).assertIsDisplayed()
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("Page 1 of", substring = true)
        onNodeWithTag(READER_PREVIOUS_INDICATOR_TAG).assertIsDisplayed().assertHasNoClickAction()
        onNodeWithTag(READER_NEXT_INDICATOR_TAG).assertIsDisplayed().assertHasNoClickAction()
    }

    /**
     * The chevrons are indicators, not buttons (docs/design.md §7.2), so the
     * page carries the two turns as accessibility actions: a reader who cannot
     * tap a third of the screen keeps a way through the book.
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
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()

        // A tap in the right third advances; in the left third it goes back.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()

        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(4f, center.y)) }
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingTheCentreRecedesAndReturnsTheChrome() = runComposeUiTest {
        setContent { Reader(renderer, chapter = SHORT_CHAPTER) }
        onNodeWithText("Hello.").assertIsDisplayed()
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()

        // The short chapter leaves the middle of the page free of glyphs, so the
        // centre tap is a chrome toggle rather than a word lookup.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        onNodeWithText("Page 1 of", substring = true).assertDoesNotExist()
        onNodeWithTag(READER_BACK_TAG).assertDoesNotExist()
        onNodeWithText("Hello.").assertIsDisplayed()

        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
    }

    @Test
    fun opensAtTheSavedPosition() = runComposeUiTest {
        // A position at the far end of the text must open on the last page, not
        // the first: a resumed session is exactly where the user left it.
        setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        onNodeWithText("Page 1 of", substring = true).assertDoesNotExist()
        onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()
    }

    @Test
    fun aPageTurnReportsItsPosition() = runComposeUiTest {
        var reported = -1
        setContent { Reader(renderer, onPositionChange = { offset -> reported = offset }) }
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()

        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }

        assertTrue(reported > 0, "turning to page two must report a non-zero character offset")
    }

    @Test
    fun selectingAPhraseByLongPressDragReportsIt() = runComposeUiTest {
        var selected: PhraseSelection? = null
        setContent { Reader(renderer, onPhraseSelected = { chosen -> selected = chosen }) }

        onNodeWithText("On the quiet evening", substring = true).assertIsDisplayed()
        val start = firstWordCentre()
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
        chapter: ReaderChapter = SampleChapter.chapter,
        initialOffset: Int = 0,
        onPositionChange: (Int) -> Unit = {},
        onPhraseSelected: (PhraseSelection) -> Unit = {},
    ) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(chapter, renderer, initialOffset),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(
                    onPhraseSelected = onPhraseSelected,
                    onBack = {},
                    onPositionChange = onPositionChange,
                ),
            )
        }
    }
}

/** The first word link's centre, in the reader page's coordinates. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.firstWordCentre(): Offset {
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val word = onAllNodes(WORD_LINK)
        .onFirst()
        .fetchSemanticsNode()
        .boundsInRoot
    return word.center - page.topLeft
}

/**
 * A word link on the page: a clickable node that carries neither text nor a
 * description. The reader's chrome has an icon since the navigation shell
 * (issue #185) — clickable, described, and not a word — so a word is the one
 * clickable node with nothing to read on it.
 */
private val WORD_LINK = hasClickAction() and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)
