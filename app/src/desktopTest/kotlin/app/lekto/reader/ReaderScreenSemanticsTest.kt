package app.lekto.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The reader screen's behaviour through semantics: it renders the sample
 * chapter, page navigation works in both directions, it opens at a saved
 * position, and a tap on the reading surface recedes the chrome and returns it
 * (issue #16). A phone-sized viewport is forced so the sample spans several
 * pages deterministically and the chrome fits.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderScreenSemanticsTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { _, _ -> MasteryLevel.KNOWN },
    )

    private companion object {
        /** Previous and Next: the only clickable nodes the screen adds itself. */
        const val CHROME_BUTTONS = 2
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
    fun navigatesForwardAndBack() = runComposeUiTest {
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
    fun opensAtTheSavedPosition() = runComposeUiTest {
        // A position at the far end of the text must open on the last page, not
        // the first: a resumed session is exactly where the user left it.
        setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        onNodeWithText("Page 1 of", substring = true).assertDoesNotExist()
        onNodeWithText("Previous page").assertIsEnabled()
        onNodeWithText("Next page").assertIsNotEnabled()
    }

    @Test
    fun tappingTheSurfaceRecedesAndReturnsTheChrome() = runComposeUiTest {
        setContent { Reader(renderer) }

        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Library").assertIsDisplayed()

        // The surface sits behind the text, so a tap on the text itself is not a
        // surface tap; a tap in the margins is. The semantics action is the
        // deterministic way to reach it without pointer-coordinate injection.
        onNodeWithContentDescription("Reading surface").performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithText("Page 1 of", substring = true).assertDoesNotExist()
        onNodeWithText("Library").assertDoesNotExist()

        onNodeWithContentDescription("Reading surface").performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun aPageTurnReportsItsPosition() = runComposeUiTest {
        var reported = -1
        setContent { Reader(renderer, onPositionChange = { offset -> reported = offset }) }

        onNodeWithText("Next page").performClick()

        assertTrue(reported > 0, "turning to page two must report a non-zero character offset")
    }

    @Composable
    private fun Reader(renderer: ReaderRenderer, initialOffset: Int = 0, onPositionChange: (Int) -> Unit = {}) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(SampleChapter.chapter, renderer, initialOffset),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(onBack = {}, onPositionChange = onPositionChange),
            )
        }
    }
}
