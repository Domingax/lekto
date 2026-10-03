package app.lekto.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
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
        mastery = MasteryLookup { _, _ -> MasteryLevel.KNOWN },
    )

    private companion object {
        /** Previous and Next: the only clickable nodes the screen adds itself. */
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

        // The chrome contributes Previous/Next; every word adds another clickable
        // node, so the count proves the word layer is tappable and reachable, not
        // just painted.
        val clickables = onAllNodes(hasClickAction()).fetchSemanticsNodes()

        assertTrue(clickables.size > CHROME_BUTTONS, "expected word links beyond the chrome, found ${clickables.size}")
    }

    @Test
    fun navigatesForwardAndBackWithTheButtons() = runComposeUiTest {
        setContent { Reader(renderer) }

        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Previous page").assertIsNotEnabled()

        onNodeWithText("Next page").performClick()
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
        onNodeWithText("Previous page").assertIsEnabled()

        onNodeWithText("Previous page").performClick()
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Previous page").assertIsNotEnabled()
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
        onNodeWithText("Library").assertDoesNotExist()
        onNodeWithText("Hello.").assertIsDisplayed()

        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun opensAtTheSavedPosition() = runComposeUiTest {
        // A position at the far end of the text must open on the last page, not
        // the first: a resumed session is exactly where the user left it.
        setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        onNodeWithText("Page 1 of", substring = true).assertDoesNotExist()
        onNodeWithText("Previous page").assertIsEnabled()
        onNodeWithText("Next page").assertIsNotEnabled()
    }

    @Test
    fun aPageTurnReportsItsPosition() = runComposeUiTest {
        var reported = -1
        setContent { Reader(renderer, onPositionChange = { offset -> reported = offset }) }

        onNodeWithText("Next page").performClick()

        assertTrue(reported > 0, "turning to page two must report a non-zero character offset")
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
    private fun Reader(
        renderer: ReaderRenderer,
        chapter: ReaderChapter = SampleChapter.chapter,
        initialOffset: Int = 0,
        onPositionChange: (Int) -> Unit = {},
    ) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(chapter, renderer, initialOffset),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(onBack = {}, onPositionChange = onPositionChange),
            )
        }
    }
}
