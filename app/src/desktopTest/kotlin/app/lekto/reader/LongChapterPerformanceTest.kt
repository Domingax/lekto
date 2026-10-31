package app.lekto.reader

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.BlockKind
import app.lekto.core.text.IcuTextSegmenter
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A long-book guard (issue #16, issue #241): opening a book must cost one
 * chapter, not the book. The book is sized like 300 pages and split into a
 * hundred chapters; opening it at the middle chapter measures the pages the
 * reader can show, and stops.
 *
 * The exact numbers are printed for the record and the assertions are loose (a
 * wall-clock bound tight enough to be meaningful would be flaky on shared CI),
 * but the shape is pinned: a regression to whole-book work shows up as a window
 * spanning the book instead of one chapter, and fails here.
 *
 * The measurement runs inside `setContent` because a `TextMeasurer` only exists
 * in a composition; the times therefore include that one frame, which is the
 * price of measuring the real layout rather than a mock.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // Paragraph count and viewport dimensions are the experiment's parameters.
class LongChapterPerformanceTest {

    private val chapterCount = 100
    private val paragraphs = 900
    private val blocksPerChapter = paragraphs / chapterCount

    private val chapter = ReaderText(
        title = "A three-hundred-page book",
        language = "en",
        blocks = List(paragraphs) { index ->
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

    private fun renderedStart(block: Int): Int =
        chapter.blocks.take(block).sumOf { blockText -> blockText.text.length + BLOCK_SEPARATOR.length }

    @Test
    @Suppress("LongMethod") // One linear measurement: chapter text, first page, first-page tokens, window.
    fun aLongBookIsOpenedWithoutMeasuringTheWholeBook() = runComposeUiTest {
        val renderer = ReaderRenderer(IcuTextSegmenter(), MasteryLookup { MasteryLevel.UNKNOWN })
        val styles = ReaderStyles.of(ReadingPreferences.DEFAULT, mastery = MasteryPalette.Light)
        val chapterStarts = (0 until chapterCount).map { index -> renderedStart(index * blocksPerChapter) }.toSet()

        // The anchor sits at the middle chapter's first block: the one a chapter
        // jump lands on, and the worst case the reader can open at.
        val anchorBlock = (chapterCount / 2) * blocksPerChapter
        val anchor = renderedStart(anchorBlock)
        val chapterEnd = renderedStart(anchorBlock + blocksPerChapter)

        val start = System.nanoTime()
        val chapterText = buildChapterText(chapter, styles)
        val chapterTextMs = millisSince(start)

        var window: List<ReaderPage> = emptyList()
        var firstPageTokensMs = 0L
        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val width = with(density) { 320.dp.roundToPx() }
            val height = with(density) { 560.dp.roundToPx() }
            val layout = ReaderLayout(measurer, styles.body, width, height)
            val pages = mutableStateListOf<ReaderPage>()
            LaunchedEffect(Unit) {
                val windowStart = System.nanoTime()
                fillWindow(chapterText, layout, chapterStarts, anchor, pages) {}
                window = pages.toList()
                println("long-book: window in ${millisSince(windowStart)}ms")

                val tokensStart = System.nanoTime()
                buildPageTokens(chapterText, window.first(), renderer, chapter, styles)
                firstPageTokensMs = millisSince(tokensStart)
            }
        }
        waitForIdle()

        println(
            "long-book: chars=${chapterText.length} chapters=$chapterCount windowPages=${window.size} " +
                "chapterText=${chapterTextMs}ms firstPageTokens=${firstPageTokensMs}ms",
        )
        assertEquals(anchor, window.first().start, "the window starts at the chapter the reader opened")
        assertTrue(window.size <= 4, "a window is a page and a few ahead, not the book: ${window.size}")
        assertTrue(window.last().end <= chapterEnd, "the window stays inside one chapter")
    }

    private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
