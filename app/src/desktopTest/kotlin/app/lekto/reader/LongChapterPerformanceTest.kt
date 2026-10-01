package app.lekto.reader

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.IcuTextSegmenter
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A long-chapter guard: the spike report states how tokenisation and a
 * whole-chapter layout scale, so this pins the shape (one measure per chapter,
 * pages produced) at a deliberately generous time bound. The exact numbers are
 * printed for the report and are not asserted — a wall-clock assert tight enough
 * to be meaningful would be flaky on shared CI.
 *
 * The measurement runs inside `setContent` because a `TextMeasurer` only exists
 * in a composition; the times therefore include that one frame, which is the
 * price of measuring the real layout rather than a mock.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // Paragraph count and viewport dimensions are the experiment's parameters.
class LongChapterPerformanceTest {

    private val chapter = ReaderChapter(
        title = "Long chapter",
        language = "en",
        blocks = List(400) { index ->
            TextBlock(
                kind = BlockKind.PARAGRAPH,
                runs = listOf(
                    TextRun(
                        "Para $index: the quick brown fox jumps over the lazy dog while the harbour " +
                            "lantern burns through the quiet evening and the keeper watches the sea.",
                    ),
                ),
            )
        },
    )

    @Test
    fun tokenisesAndPaginatesALongChapter() = runComposeUiTest {
        val segmenter = IcuTextSegmenter()
        val renderer = ReaderRenderer(segmenter, MasteryLookup { _, _ -> MasteryLevel.UNKNOWN })

        val tokeniseStart = System.nanoTime()
        val tokens = buildReaderTokens(chapter, renderer)
        val tokeniseMs = millisSince(tokeniseStart)

        lateinit var pages: List<ReaderPage>
        var paginateMs = 0L
        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val width = with(density) { 320.dp.roundToPx() }
            val height = with(density) { 560.dp.roundToPx() }
            val text = remember { tokens.text }
            val start = System.nanoTime()
            pages = paginateChapter(text, ReaderLayout(measurer, ReaderStyles.Reading.body, width, height))
            paginateMs = millisSince(start)
        }
        waitForIdle()

        println(
            "long-chapter: chars=${tokens.text.length} words=${tokens.words.size} " +
                "pages=${pages.size} tokenise=${tokeniseMs}ms paginate=${paginateMs}ms",
        )
        assertTrue(pages.size > 1)
    }

    private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
