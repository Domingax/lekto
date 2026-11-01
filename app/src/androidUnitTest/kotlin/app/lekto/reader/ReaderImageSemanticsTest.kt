package app.lekto.reader

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.IMAGE_PLACEHOLDER
import app.lekto.core.text.ImageRef
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A picture on the page, on Android (issue #263): the alt stands in while it
 * cannot be decoded, and the decoded image is described by that same alt, so a
 * screen reader hears the book the way a sighted reader sees it. The desktop
 * twin is `app/src/desktopTest`'s same-named test (issue #74).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class ReaderImageSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    private val chapter = ReaderText(
        title = "Pictures",
        language = "en",
        blocks = listOf(
            TextBlock(
                kind = BlockKind.IMAGE,
                runs = listOf(TextRun(IMAGE_PLACEHOLDER)),
                image = ImageRef("a.png", ratio = 2f, alt = "A bar chart"),
            ),
        ),
    )

    @Test
    fun aPictureThatCannotBeDecodedShowsItsAlt() {
        showReader(BookImageLoader.None)

        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodesWithText("A bar chart").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("A bar chart").assertIsDisplayed()
    }

    @Test
    fun aDecodedPictureIsDescribedByItsAlt() {
        val picture = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).asImageBitmap()
        showReader(BookImageLoader { picture })

        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodesWithContentDescription("A bar chart").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("A bar chart").assertIsDisplayed()
    }

    private fun showReader(images: BookImageLoader) {
        compose.setContent {
            MaterialTheme {
                ReaderScreen(
                    document = ReaderDocument(chapter, renderer, images = images),
                    modifier = Modifier.size(width = 360.dp, height = 640.dp),
                )
            }
        }
    }

    private companion object {
        /** The picture decodes off the main thread, so the assertion waits for it. */
        const val WAIT_MS = 5_000L
    }
}
