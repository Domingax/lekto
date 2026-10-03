package app.lekto.reader

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
 * A long-book guard (issue #16): a book sized like 300 pages measures the cost
 * of the real open path — build the styled chapter text once, lay out the first
 * page, tokenise that page — against laying out and tokenising the whole book.
 * The exact numbers are printed for the record and the assertions are loose (a
 * wall-clock bound tight enough to be meaningful would be flaky on shared CI),
 * but the shape is pinned: the first page must not wait on the whole book, so a
 * regression to eager whole-book work turns red.
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
    @Suppress("LongMethod") // One linear measurement: chapter text, first page, first-page tokens, whole-book layout.
    fun aLongBookIsOpenedWithoutProcessingTheWholeBook() = runComposeUiTest {
        val renderer = ReaderRenderer(IcuTextSegmenter(), MasteryLookup { _, _ -> MasteryLevel.UNKNOWN })

        var start = System.nanoTime()
        val chapterText = buildChapterText(chapter, renderer.styles)
        val chapterTextMs = millisSince(start)

        lateinit var firstPage: ReaderPage
        lateinit var pages: List<ReaderPage>
        var firstPageMs = 0L
        var firstPageTokensMs = 0L
        var paginateMs = 0L
        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val width = with(density) { 320.dp.roundToPx() }
            val height = with(density) { 560.dp.roundToPx() }
            val layout = ReaderLayout(measurer, renderer.styles.body, width, height)
            // Warm up the text engine and the segmenter so the timings separate the
            // first page's steady-state cost from one-off JIT and ICU startup.
            val warmUp = paginateChapter(chapterText, layout).first()
            buildPageTokens(chapterText, warmUp, renderer, chapter)
            start = System.nanoTime()
            firstPage = paginateChapter(chapterText, layout).first()
            firstPageMs = millisSince(start)
            start = System.nanoTime()
            buildPageTokens(chapterText, firstPage, renderer, chapter)
            firstPageTokensMs = millisSince(start)
            start = System.nanoTime()
            pages = paginateChapter(chapterText, layout).toList()
            paginateMs = millisSince(start)
        }
        waitForIdle()

        start = System.nanoTime()
        val eagerTokens = buildReaderTokens(chapter, renderer)
        val eagerTokensMs = millisSince(start)

        println(
            "long-book: chars=${chapterText.length} words=${eagerTokens.words.size} pages=${pages.size} " +
                "chapterText=${chapterTextMs}ms eagerTokens=${eagerTokensMs}ms firstPage=${firstPageMs}ms " +
                "firstPageTokens=${firstPageTokensMs}ms paginate=${paginateMs}ms",
        )
        assertTrue(pages.size > 100, "a 300-page book should span more than 100 pages at this viewport")
        assertTrue(firstPage.end in 1 until chapterText.length, "the first page is one slice of the book")
        // The point of AC "opens without a perceptible stall": the first page's
        // work must be a small fraction of the whole book's. A regression to
        // whole-book-before-the-first-page makes the first page cost the whole
        // pagination and fails here, while the ratio survives the machine's speed.
        assertTrue(firstPageMs + firstPageTokensMs < paginateMs, "the first page must not wait on the whole book")
    }

    private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
