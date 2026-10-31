package app.lekto.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.testkit.WhitespaceTextSegmenter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The reader's two bands against the window's edges (issue #245). The top band is
 * a bar, so its controls take `LektoSpacing.BarInset` — Material's 4 dp app-bar
 * inset — and land near the border. The bottom band keeps `LektoSpacing.ScreenEdge`,
 * so its chevrons sit where they always did. The twin of `app/desktopTest`'s
 * class, on the runtime that ultimately draws the bands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class ReaderBandInsetSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theTopBandReachesTheEdgeAndTheChevronsKeepTheContentMargin() {
        compose.setContent { Reader() }

        // The top band is a bar, so its controls take Material's 4 dp inset and land
        // 8 dp in (the 40 dp container of a 48 dp target). The bottom band's chevrons
        // keep the screen's 24 dp margin, so they stay at 28 dp.
        val page = compose.onNodeWithTag(READER_PAGE_TAG).getUnclippedBoundsInRoot()
        assertNearTheEdge(8.dp, compose.onNodeWithTag(READER_BACK_TAG).getUnclippedBoundsInRoot().left - page.left)
        assertNearTheEdge(
            8.dp,
            page.right - compose.onNodeWithTag(READING_PREFERENCES_TAG).getUnclippedBoundsInRoot().right,
        )
        assertNearTheEdge(
            28.dp,
            compose.onNodeWithTag(READER_PREVIOUS_PAGE_TAG).getUnclippedBoundsInRoot().left - page.left,
        )
        assertNearTheEdge(
            28.dp,
            page.right - compose.onNodeWithTag(READER_NEXT_PAGE_TAG).getUnclippedBoundsInRoot().right,
        )
    }

    /** Half a dp absorbs the density's rounding of a 4 dp inset. */
    @Suppress("MagicNumber") // The half-dp tolerance is the test's own parameter.
    private fun assertNearTheEdge(expected: Dp, actual: Dp) {
        assertTrue("the control sits $actual from the edge, not $expected", abs(expected.value - actual.value) <= 0.5f)
    }

    @Composable
    private fun Reader() {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(
                    SampleChapter.chapter,
                    ReaderRenderer(
                        segmenter = WhitespaceTextSegmenter(),
                        mastery = MasteryLookup { MasteryLevel.KNOWN },
                    ),
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(onBack = {}),
            )
        }
    }
}
