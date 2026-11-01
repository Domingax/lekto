package app.lekto.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.IMAGE_PLACEHOLDER
import app.lekto.core.text.ImageRef
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test

/**
 * A picture on the page: the alt stands in while it cannot be decoded, and the
 * decoded image is described by that same alt, so a screen reader hears the book
 * the way a sighted reader sees it (issue #263).
 */
@OptIn(ExperimentalTestApi::class)
class ReaderImageSemanticsTest {

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
    fun aPictureThatCannotBeDecodedShowsItsAlt() = runComposeUiTest {
        setContent { showReader(BookImageLoader.None) }

        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithText("A bar chart").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("A bar chart").assertIsDisplayed()
    }

    @Test
    fun aDecodedPictureIsDescribedByItsAlt() = runComposeUiTest {
        val picture = ImageBitmap(width = 8, height = 4)
        setContent { showReader(BookImageLoader { picture }) }

        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodesWithContentDescription("A bar chart").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithContentDescription("A bar chart").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showReader(images: BookImageLoader) {
        setContent {
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
