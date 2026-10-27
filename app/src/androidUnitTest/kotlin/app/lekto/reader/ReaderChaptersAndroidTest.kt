package app.lekto.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.Chapter
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chapter sheet on a **simulated Android runtime** (ADR-0037, issue #198):
 * the `LazyColumn` the sheet is built on is Android's own scroll container, so
 * the list is proved here as well as on the desktop lane. A book with two short
 * chapters makes the page and row counts exact under Robolectric's metrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class ReaderChaptersAndroidTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    private val blocks = listOf(
        TextBlock(BlockKind.HEADING, listOf(TextRun("One")), headingLevel = 2),
        TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("First body."))),
        TextBlock(BlockKind.HEADING, listOf(TextRun("Two")), headingLevel = 2),
        TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Second body."))),
    )

    private val book = ReaderText(
        title = "Book",
        language = "en",
        blocks = blocks,
        chapters = listOf(Chapter("One", startBlock = 0), Chapter("Two", startBlock = 2)),
    )

    @Test
    fun theSheetListsTheChaptersAndAJumpShowsItsPage() {
        compose.setContent { Reader(book) }

        compose.onNodeWithTag(READER_POSITION_TAG).assertTextContains("Chapter 1 · Page 1 of 2", substring = true)
        compose.onNodeWithTag(READER_POSITION_TAG).performClick()
        compose.onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithTag(readerChapterRowTag(1)).assertTextContains("Two")

        compose.onNodeWithTag(readerChapterRowTag(1)).performClick()

        compose.onNodeWithTag(READER_CHAPTER_SHEET_TAG).assertDoesNotExist()
        compose.onNodeWithText("Second body.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(READER_POSITION_TAG).assertTextContains("Chapter 2 · Page 2 of 2", substring = true)
    }

    @Composable
    private fun Reader(text: ReaderText) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(text, renderer),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(onBack = {}),
            )
        }
    }
}
