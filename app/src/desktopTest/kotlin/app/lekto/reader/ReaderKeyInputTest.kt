package app.lekto.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader's keyboard (issue #246): `←` and `→` turn the page, through the same
 * `ReaderState` calls the chevrons make, and they stand down while a panel or the
 * end state has the keyboard.
 *
 * Desktop-only by nature — the keys are the desktop's way through the book until
 * the swipe lands — but the handler is common code, so a hardware keyboard on
 * Android takes the same path.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // The page counts are the sample chapter's own.
class ReaderKeyInputTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    @Test
    fun theArrowKeysTurnThePageInBothDirections() = runComposeUiTest {
        setContent { Reader() }
        assertEquals(0, progressPercent())

        onRoot().performKeyInput { pressKey(Key.DirectionRight) }

        val second = progressPercent()
        assertTrue(second > 0, "the right arrow turns forward")

        onRoot().performKeyInput { pressKey(Key.DirectionLeft) }

        assertEquals(0, progressPercent())
    }

    @Test
    fun theArrowKeysStandDownWhileTheTextPanelIsOpen() = runComposeUiTest {
        setContent { Reader() }
        onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        val before = progressPercent()

        onRoot().performKeyInput { pressKey(Key.DirectionRight) }

        assertEquals(before, progressPercent(), "the panel owns the keyboard while it is open")
    }

    @Composable
    private fun Reader() {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(SampleChapter.chapter, renderer),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(onBack = {}),
            )
        }
    }
}

/** The **Reading progress** the position line shows, as a whole percentage. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.progressPercent(): Int {
    val text = onNodeWithTag(READER_POSITION_TAG).fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString(" ") { line -> line.text }
    return Regex("(\\d+)\\s*%").find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: error("no percentage in the position: $text")
}
