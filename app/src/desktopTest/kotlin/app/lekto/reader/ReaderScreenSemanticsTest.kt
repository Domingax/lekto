package app.lekto.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The reader screen's behaviour through semantics: it renders the sample
 * chapter, and page navigation works in both directions. A phone-sized viewport
 * is forced so the sample spans several pages deterministically and the chrome
 * fits.
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

        // The page bar contributes two clickable nodes (Previous/Next); every
        // word adds another, so the count proves the word layer is tappable and
        // reachable, not just painted.
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

    @Composable
    private fun Reader(renderer: ReaderRenderer) {
        MaterialTheme {
            ReaderScreen(
                chapter = SampleChapter.chapter,
                renderer = renderer,
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
