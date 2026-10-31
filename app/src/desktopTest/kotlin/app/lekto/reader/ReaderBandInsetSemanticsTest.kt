package app.lekto.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The reader's two bands against the window's edges (issue #245). The top band is
 * a bar, so its controls take `LektoSpacing.BarInset` — Material's 4 dp app-bar
 * inset — and land near the border. The bottom band keeps `LektoSpacing.ScreenEdge`,
 * so its chevrons sit where they always did.
 *
 * The page spans the same width as the bands, so it is the reference the four
 * controls are measured against.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderBandInsetSemanticsTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    @Test
    fun theTopBandReachesTheEdgeAndTheChevronsKeepTheContentMargin() = runComposeUiTest {
        setContent { Reader(renderer) }

        // The top band is a bar, so its controls take Material's 4 dp inset and
        // land 8 dp in (the 40 dp container of a 48 dp target). The bottom band's
        // chevrons keep the screen's 24 dp margin, so they stay at 28 dp.
        val page = onNodeWithTag(READER_PAGE_TAG).getUnclippedBoundsInRoot()
        assertNearTheEdge(8.dp, onNodeWithTag(READER_BACK_TAG).getUnclippedBoundsInRoot().left - page.left)
        assertNearTheEdge(8.dp, page.right - onNodeWithTag(READING_PREFERENCES_TAG).getUnclippedBoundsInRoot().right)
        assertNearTheEdge(28.dp, onNodeWithTag(READER_PREVIOUS_PAGE_TAG).getUnclippedBoundsInRoot().left - page.left)
        assertNearTheEdge(28.dp, page.right - onNodeWithTag(READER_NEXT_PAGE_TAG).getUnclippedBoundsInRoot().right)
    }

    /** Half a dp absorbs the density's rounding of a 4 dp inset. */
    @Suppress("MagicNumber") // The half-dp tolerance is the test's own parameter.
    private fun assertNearTheEdge(expected: Dp, actual: Dp) {
        assertTrue(
            abs(expected.value - actual.value) <= 0.5f,
            "the control sits $actual from the edge, not $expected",
        )
    }

    @Composable
    private fun Reader(renderer: ReaderRenderer) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(SampleChapter.chapter, renderer),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(onBack = {}),
            )
        }
    }
}
