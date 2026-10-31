@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package app.lekto.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.Chapter
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.blockStartOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The desktop **context column** (issue #199; ADR-0037; docs/design.md §6,
 * §7.2/§7.3): at 1100 dp and above the reader reserves a right-hand column that
 * shows the chapter list when nothing is touched and the lookup panel when a word
 * is, and the page never moves when it swaps. Below the breakpoint the column is
 * absent and the phone's sheet and bottom panel are used instead.
 *
 * The test window itself is the viewport, because the column's breakpoint is the
 * reader's own width: the desktop test display is 1024 dp by default, which is a
 * phone-shaped window here.
 *
 * The lookup panel is a stand-in — the reader hosts whatever the caller passes,
 * and only its placement is the reader's business — so the swap, the width and
 * the dismiss are the reader's own behaviour under test.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // The two viewports and the page geometry are the test's parameters.
class ReaderContextColumnTest {

    private companion object {
        const val SECOND_CHAPTER_BLOCK = 2
    }

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    private val blocks = listOf(
        heading("One"),
        paragraph("First body."),
        heading("Two"),
        paragraph("Second body."),
    )

    private val chapters = listOf(
        Chapter("One", startBlock = 0),
        Chapter("Two", startBlock = SECOND_CHAPTER_BLOCK),
    )

    private val book = ReaderText(title = "Book", language = "en", blocks = blocks, chapters = chapters)

    @Test
    fun theColumnIsReservedAtTheBreakpoint() = columnTest(WIDE) {
        setContent { Reader() }

        onNodeWithTag(READER_CONTEXT_COLUMN_TAG).assertIsDisplayed()
        onNodeWithText("Chapters").assertIsDisplayed()
        onNodeWithTag(readerChapterRowTag(0)).assertTextContains("One")
        onNodeWithTag(readerChapterRowTag(1)).assertTextContains("Two")
    }

    @Test
    fun theChapterListIsNotASheetAtTheBreakpoint() = columnTest(WIDE) {
        setContent { Reader() }

        // The list is already in the column, so the position stays a label: it
        // carries no click action at all, rather than opening a sheet that this
        // shape does not have.
        onNodeWithTag(READER_POSITION_TAG).assertHasNoClickAction()
        onNodeWithTag(READER_POSITION_TAG).performClick()
        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun belowTheBreakpointThereIsNoColumnAndTheSheetIsTheWayIn() = columnTest(NARROW) {
        setContent { Reader() }

        onNodeWithTag(READER_CONTEXT_COLUMN_TAG).assertDoesNotExist()
        onNodeWithTag(READER_POSITION_TAG).performClick()
        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun theColumnMarksTheCurrentChapter() = columnTest(WIDE) {
        setContent { Reader(initialOffset = blockStartOffset(blocks, SECOND_CHAPTER_BLOCK)) }

        onNodeWithTag(readerChapterRowTag(0)).assertIsNotSelected().assertTextContains("One")
        onNodeWithTag(readerChapterRowTag(1)).assertIsSelected()
    }

    @Test
    fun choosingAChapterInTheColumnJumpsAndSavesItsPosition() = columnTest(WIDE) {
        var saved = -1
        setContent {
            Reader(
                initialOffset = blockStartOffset(blocks, SECOND_CHAPTER_BLOCK),
                onPositionChange = { offset -> saved = offset },
            )
        }

        onNodeWithTag(readerChapterRowTag(0)).performClick()

        onNodeWithText("First body.", substring = true).assertIsDisplayed()
        assertEquals(
            blockStartOffset(blocks, 0),
            saved,
            "a column jump saves the chapter's first block, like the sheet",
        )
        onNodeWithTag(readerChapterRowTag(0)).assertIsSelected()
    }

    @Test
    fun thePositionReturnsTheColumnToTheChaptersWhileALookupIsOpen() = columnTest(WIDE) {
        setContent { Reader() }

        tapFirstWord()
        onNodeWithTag(FAKE_LOOKUP_TAG).assertIsDisplayed()
        // With a word shown, the position is a control again: it is the way back
        // to the list without closing the word first (issue #199).
        onNodeWithTag(READER_POSITION_TAG).assertHasClickAction()

        onNodeWithTag(READER_POSITION_TAG).performClick()

        onNodeWithText("Chapters").assertIsDisplayed()
        onNodeWithTag(FAKE_LOOKUP_TAG).assertDoesNotExist()
    }

    @Test
    fun selectingAWordSwapsTheColumnToTheLookupAndDismissingRestoresTheChapters() = columnTest(WIDE) {
        setContent { Reader() }

        onNodeWithText("Chapters").assertIsDisplayed()
        onNodeWithTag(FAKE_LOOKUP_TAG).assertDoesNotExist()

        tapFirstWord()

        onNodeWithTag(FAKE_LOOKUP_TAG).assertIsDisplayed()
        onNodeWithText("Chapters").assertDoesNotExist()

        onNodeWithTag(FAKE_LOOKUP_CLOSE_TAG).performClick()

        onNodeWithText("Chapters").assertIsDisplayed()
        onNodeWithTag(readerChapterRowTag(0)).assertIsDisplayed()
        onNodeWithTag(FAKE_LOOKUP_TAG).assertDoesNotExist()
    }

    @Test
    fun selectingAWordKeepsThePageWidth() = columnTest(WIDE) {
        setContent { Reader() }
        val before = onNodeWithTag(READER_PAGE_TAG).getUnclippedBoundsInRoot()

        tapFirstWord()
        onNodeWithTag(FAKE_LOOKUP_TAG).assertIsDisplayed()
        val during = onNodeWithTag(READER_PAGE_TAG).getUnclippedBoundsInRoot()

        onNodeWithTag(FAKE_LOOKUP_CLOSE_TAG).performClick()
        val after = onNodeWithTag(READER_PAGE_TAG).getUnclippedBoundsInRoot()

        assertEquals(
            before.right - before.left,
            during.right - during.left,
            "opening the lookup must not narrow the page",
        )
        assertEquals(
            before.right - before.left,
            after.right - after.left,
            "closing the lookup must not widen the page",
        )
        assertEquals(before.left, during.left, "the page must not move sideways")
    }

    @Test
    fun theEndOfTheBookTakesTheWholeWindow() = columnTest(WIDE) {
        setContent { Reader(initialOffset = Int.MAX_VALUE) }
        onNodeWithTag(READER_CONTEXT_COLUMN_TAG).assertIsDisplayed()

        // The end state is full-screen, so it leaves no room for the column.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }

        onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
        onNodeWithTag(READER_CONTEXT_COLUMN_TAG).assertDoesNotExist()
    }

    @Test
    fun theColumnSaysWhenTheBookHasNoChapterList() = columnTest(WIDE) {
        val single = book.copy(chapters = listOf(Chapter("Only", startBlock = 0)))
        setContent { Reader(text = single) }

        onNodeWithTag(READER_CONTEXT_COLUMN_TAG).assertIsDisplayed()
        onNodeWithText("Chapters").assertIsDisplayed()
        onNodeWithText("This book has no chapter list.").assertIsDisplayed()
    }

    /**
     * The reader in a test window of [width], with a lookup the test opens by
     * tapping a word and closes through the stand-in panel: the reader's own
     * placement decides whether it is the column or the sheet.
     */
    @Suppress("LongParameterList") // The test's knobs; a bundle would only hide them.
    @Composable
    private fun Reader(
        text: ReaderText = book,
        initialOffset: Int = 0,
        onPositionChange: (Int) -> Unit = {},
    ) {
        var lookupOpen by remember { mutableStateOf(false) }
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(text, renderer, initialOffset),
                modifier = Modifier.fillMaxSize(),
                actions = ReaderActions(
                    onWordTap = { lookupOpen = true },
                    onBack = {},
                    onPositionChange = onPositionChange,
                    onDismissOverlay = { lookupOpen = false }.takeIf { lookupOpen },
                ),
                context = ReaderContext(
                    isLookupOpen = lookupOpen,
                    panel = { _, inColumn ->
                        FakeLookup(inColumn = inColumn, onClose = { lookupOpen = false })
                    },
                ),
            )
        }
    }
}

/** The stand-in lookup panel's own tag, and its close action's. */
private const val FAKE_LOOKUP_TAG = "fake-lookup"
private const val FAKE_LOOKUP_CLOSE_TAG = "fake-lookup-close"

/** A heading block, as the parser would hand the reader. */
private fun heading(text: String) = TextBlock(BlockKind.HEADING, listOf(TextRun(text)), headingLevel = 2)

/** A paragraph block, as the parser would hand the reader. */
private fun paragraph(text: String) = TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(text)))

/** A stand-in lookup panel: it names its host, so the test can tell the column from the sheet. */
@Composable
private fun FakeLookup(inColumn: Boolean, onClose: () -> Unit) {
    Text(
        text = if (inColumn) "The word, in the column" else "The word, in the sheet",
        modifier = Modifier
            .testTag(FAKE_LOOKUP_TAG)
            .clickable(onClick = onClose),
    )
    Text(
        text = "Close",
        modifier = Modifier
            .testTag(FAKE_LOOKUP_CLOSE_TAG)
            .clickable(onClick = onClose),
    )
}

/** The window a wide test runs in: at the breakpoint exactly, where the column appears. */
private val WIDE = 1100.dp

/** One dp below it: a phone-shaped window, where the sheet is the way in. */
private val NARROW = 1099.dp

/** The window's height, tall enough for two short chapters and both bands. */
private val COLUMN_TEST_HEIGHT = 720.dp

/** A desktop test whose window is [width] wide: the reader's breakpoint is its own width, not the phone's. */
@OptIn(ExperimentalTestApi::class)
private fun columnTest(width: androidx.compose.ui.unit.Dp, block: suspend SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(size = Size(width.value, COLUMN_TEST_HEIGHT.value), block = block)

/** Taps the page's first word: words carry no link, so the page hit-tests the tap (ADR-0007). */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // A few points into the text's first line.
private fun ComposeUiTest.tapFirstWord() {
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val text = onNodeWithTag(READER_PAGE_TEXT_TAG).fetchSemanticsNode().boundsInRoot
    onNodeWithTag(READER_PAGE_TAG).performTouchInput {
        click(Offset(text.left - page.left + 8f, text.top - page.top + 8f))
    }
}
