package app.lekto

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.tools.MarkLayer
import app.lekto.tools.markPng
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test
import org.jetbrains.skia.Image as SkiaImage

/**
 * The launcher mark at 48 dp, the size the mark has to survive (issue #177;
 * docs/design.md §5).
 *
 * It records the same drawing `:app:renderAppIcon` renders for the desktop, so a
 * change to the bands, the rim or the letter fails here rather than in a launcher
 * nobody looked at. The sheet is text-free — the letter is a path, not a font — so
 * it verifies on any machine.
 *
 * Record or update it with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`, the task the golden CI job runs.
 */
@OptIn(ExperimentalTestApi::class)
class AppIconTest {

    @Test
    fun theMarkAtLauncherSize() = runComposeUiTest {
        setContent {
            Column {
                Row(modifier = Modifier.background(Color(PAPER)).padding(8.dp)) {
                    Mark(MarkLayer.MARK, LAUNCHER_DP)
                    Mark(MarkLayer.MARK, REVIEW_DP)
                }
                // The monochrome layer as Android 13 draws it: one tint over the
                // whole glyph, on the night surface it is read against.
                Row(modifier = Modifier.background(Color(NIGHT)).padding(8.dp)) {
                    Mark(MarkLayer.LETTER, LAUNCHER_DP, background = SURFACE, tint = PAPER)
                    Mark(MarkLayer.LETTER, REVIEW_DP, background = SURFACE, tint = PAPER)
                }
            }
        }

        onRoot().captureRoboImage("app-mark.png")
    }
}

@Composable
private fun Mark(layer: MarkLayer, size: Int, background: Long = PAPER, tint: Long? = null) {
    val bitmap = remember(layer, size, background, tint) {
        SkiaImage.makeFromEncoded(markPng(size, layer, background, tint)).toComposeImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.padding(4.dp).size(size.dp),
    )
}

// The size the mark has to survive, and twice it so the review sees the detail.
private const val LAUNCHER_DP: Int = 48
private const val REVIEW_DP: Int = 96

// The Light paper, the warm night and its surface, docs/design.md §2.1.
private const val PAPER: Long = 0xFFFAF7F2
private const val NIGHT: Long = 0xFF15130F
private const val SURFACE: Long = 0xFF1F1C17
