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
 * A long-book guard (issue #16): a book sized like 300 pages is tokenised and
 * paginated, and the first page is timed apart from the whole-book layout. The
 * exact numbers are printed for the record and the assertions are deliberately
 * loose — a wall-clock bound tight enough to be meaningful would be flaky on
 * shared CI — but the shape (more than 100 pages, a first page that is a prefix
 * of the full pagination) is pinned so a regression to whole-book layout before
 * the first page turns red.
 *
 * The measurement runs inside `setContent` because a `TextMeasurer` only exists
 * in a composition; the times therefore include that one frame, which is the
 * price of measuring the real layout rather than a mock.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // Paragraph count and viewport dimensions are the experiment's parameters.
class LongChapterPerformanceTest {

    private val chapter = ReaderChapter(
        title = "A three-hundred-page book",
        language = "en",
        blocks = List(900) { index ->
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
    fun aLongBookIsOpenedWithoutLayingOutEveryPage() = runComposeUiTest {
        val segmenter = IcuTextSegmenter()
        val renderer = ReaderRenderer(segmenter, MasteryLookup { _, _ -> MasteryLevel.UNKNOWN })

        val tokeniseStart = System.nanoTime()
        val tokens = buildReaderTokens(chapter, renderer)
        val tokeniseMs = millisSince(tokeniseStart)

        lateinit var firstPage: ReaderPage
        lateinit var pages: List<ReaderPage>
        var firstPageMs = 0L
        var paginateMs = 0L
        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val width = with(density) { 320.dp.roundToPx() }
            val height = with(density) { 560.dp.roundToPx() }
            val text = remember { tokens.text }
            val layout = ReaderLayout(measurer, ReaderStyles.Reading.body, width, height)
            var start = System.nanoTime()
            firstPage = paginateChapter(text, layout).first()
            firstPageMs = millisSince(start)
            start = System.nanoTime()
            pages = paginateChapter(text, layout).toList()
            paginateMs = millisSince(start)
        }
        waitForIdle()

        println(
            "long-book: chars=${tokens.text.length} words=${tokens.words.size} " +
                "pages=${pages.size} tokenise=${tokeniseMs}ms firstPage=${firstPageMs}ms paginate=${paginateMs}ms",
        )
        assertTrue(pages.size > 100, "a 300-page book should span more than 100 pages at this viewport")
        assertTrue(firstPage.end in 1 until tokens.text.length, "the first page is one slice of the book")
        // The point of AC "opens without a perceptible stall": finding the first
        // page must cost a small fraction of laying out every page. A regression
        // to whole-book-before-the-first-page makes the two times comparable and
        // fails here, while the ratio survives the machine's absolute speed.
        assertTrue(firstPageMs * 4 < paginateMs, "the first page must not wait on the whole book")
    }

    private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
