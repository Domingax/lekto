package app.lekto.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.BlockKind
import app.lekto.core.text.Chapter
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader's chapters end to end (ADR-0037, issue #198): a page break at every
 * chapter start, the `Chapter N · Page X of Y` position, the chapter sheet, and a
 * jump that saves its place and shows the target at once.
 *
 * The book is short on purpose: each chapter segment fits one page, so the pages
 * and their numbers are deterministic at a phone viewport.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One test per reader-chapter behaviour; splitting hides the surface.
class ReaderChaptersTest {

    private companion object {
        const val FIRST_CHAPTER_BLOCK = 1
        const val SECOND_CHAPTER_BLOCK = 3
    }

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    private val blocks = listOf(
        paragraph("Intro before chapters."),
        heading("One"),
        paragraph("First body."),
        heading("Two"),
        paragraph("Second body."),
    )

    private val chapters = listOf(
        Chapter("One", startBlock = 1),
        Chapter("Two", startBlock = 3),
    )

    private val book = ReaderText(title = "Book", language = "en", blocks = blocks, chapters = chapters)

    @Test
    fun aChapterAlwaysStartsANewPageAndThePositionNamesIt() = runComposeUiTest {
        setContent { Reader(book) }

        // Text before the first chapter has no chapter: the position is the page alone.
        onNodeWithText("Intro before chapters.", substring = true).assertIsDisplayed()
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("Page 1 of 3", substring = true)

        onNodeWithTag(READER_NEXT_PAGE_TAG).performClick()

        onNodeWithText("First body.", substring = true).assertIsDisplayed()
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("One", substring = true)
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("Page 2 of 3", substring = true)

        onNodeWithTag(READER_NEXT_PAGE_TAG).performClick()

        onNodeWithText("Second body.", substring = true).assertIsDisplayed()
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("Two", substring = true)
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("Page 3 of 3", substring = true)
    }

    @Test
    fun theBarShowsTheBooksOwnChapterTitleNotAnInventedNumber() = runComposeUiTest {
        // A printed book may number its chapters in Roman numerals, and front
        // matter may still be a chapter: the bar repeats the book's title so it
        // cannot disagree with the list (ADR-0039).
        val roman = book.copy(chapters = listOf(Chapter("XI", startBlock = 1), Chapter("XII", startBlock = 3)))
        setContent { Reader(roman, initialOffset = startOf(1)) }

        onNodeWithTag(READER_POSITION_TAG).assertTextContains("XI", substring = true)
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("Page 2 of 3", substring = true)
    }

    @Test
    fun aBookWithOneChapterKeepsThePositionALabel() = runComposeUiTest {
        val single = ReaderText(
            title = "Book",
            language = "en",
            blocks = blocks.take(3),
            chapters = listOf(Chapter("Only", startBlock = 0)),
        )
        setContent { Reader(single) }

        onNodeWithTag(READER_POSITION_TAG).assertTextContains("Page 1 of 1")
        onNodeWithTag(READER_POSITION_TAG).performClick()

        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingThePositionOpensTheSheetAndHighlightsTheCurrentChapter() = runComposeUiTest {
        setContent { Reader(book, initialOffset = startOf(SECOND_CHAPTER_BLOCK)) }

        onNodeWithText("Second body.", substring = true).assertIsDisplayed()
        onNodeWithTag(READER_POSITION_TAG).performClick()

        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertIsDisplayed()
        onNodeWithText("Chapters").assertIsDisplayed()
        onNodeWithTag(readerChapterRowTag(0)).assertTextContains("One")
        onNodeWithTag(readerChapterRowTag(1)).assertIsSelected()
    }

    @Test
    fun choosingAChapterSavesItsFirstBlockAndShowsItAtOnce() = runComposeUiTest {
        var saved = -1
        setContent {
            Reader(book, initialOffset = startOf(SECOND_CHAPTER_BLOCK), onPositionChange = { offset -> saved = offset })
        }

        onNodeWithTag(READER_POSITION_TAG).performClick()
        onNodeWithTag(readerChapterRowTag(0)).performClick()

        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertDoesNotExist()
        onNodeWithText("First body.", substring = true).assertIsDisplayed()
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("One", substring = true)
        onNodeWithTag(READER_POSITION_TAG).assertTextContains("Page 2 of 3", substring = true)
        assertEquals(startOf(FIRST_CHAPTER_BLOCK), saved, "a jump saves the chapter's first block")
    }

    @Test
    fun choosingTheCurrentChapterOnlyClosesTheSheet() = runComposeUiTest {
        var saved = -1
        setContent {
            Reader(book, initialOffset = startOf(SECOND_CHAPTER_BLOCK), onPositionChange = { offset -> saved = offset })
        }

        onNodeWithTag(READER_POSITION_TAG).performClick()
        onNodeWithTag(readerChapterRowTag(1)).performClick()

        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertDoesNotExist()
        assertEquals(-1, saved, "a jump to the current chapter writes nothing")
    }

    @Test
    fun closeAndOutsideTapDismissTheSheet() = runComposeUiTest {
        setContent { Reader(book) }
        onNodeWithTag(READER_POSITION_TAG).performClick()
        onNodeWithTag(READER_CHAPTER_SHEET_CLOSE_TAG).performClick()
        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertDoesNotExist()

        onNodeWithTag(READER_POSITION_TAG).performClick()
        onNodeWithTag(READER_CHAPTER_SHEET_SCRIM_TAG).performTouchInput { click(topCenter) }
        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun openingTheSheetDismissesAnOpenLookupPanel() = runComposeUiTest {
        var dismissed = false
        setContent { Reader(book, onDismissOverlay = { dismissed = true }) }

        onNodeWithTag(READER_POSITION_TAG).performClick()

        onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertIsDisplayed()
        assertTrue(dismissed, "the chapter sheet and the lookup panel are never open together")
    }

    @Test
    fun aPartThatOnlyGroupsIsAHeadingNotAChoice() = runComposeUiTest {
        val nested = ReaderText(
            title = "Book",
            language = "en",
            blocks = blocks,
            chapters = listOf(
                Chapter("Part", startBlock = 1, children = listOf(Chapter("One", startBlock = 1))),
                Chapter("Two", startBlock = 3),
            ),
        )
        setContent { Reader(nested) }

        onNodeWithTag(READER_POSITION_TAG).performClick()

        onNodeWithTag(readerChapterRowTag(0)).assertTextContains("Part")
        onNodeWithTag(readerChapterRowTag(0)).assertHasNoClickAction()
        onNodeWithTag(readerChapterRowTag(1)).assertTextContains("One")
    }

    /** The character offset where [blockIndex]'s block starts in the rendered text. */
    private fun startOf(blockIndex: Int): Int =
        blocks.take(blockIndex).sumOf { block -> block.text.length + BLOCK_SEPARATOR.length }

    @Composable
    @Suppress("LongParameterList") // The reader's inputs are the test's knobs; a bundle would only hide them.
    private fun Reader(
        text: ReaderText,
        initialOffset: Int = 0,
        onPositionChange: (Int) -> Unit = {},
        onDismissOverlay: (() -> Unit)? = null,
    ) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(text, renderer, initialOffset),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(
                    onBack = {},
                    onPositionChange = onPositionChange,
                    onDismissOverlay = onDismissOverlay,
                ),
            )
        }
    }

    private fun heading(text: String) = TextBlock(BlockKind.HEADING, listOf(TextRun(text)), headingLevel = 2)

    private fun paragraph(text: String) = TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(text)))
}
