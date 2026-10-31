package app.lekto.reader

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.lekto.core.reading.ReadingPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The paginator, asserted as invariants rather than an exact page count (which
 * would move with the machine's fonts): contiguous, ordered pages that cover
 * the whole chapter, and more than one page for a long one. Pagination is lazy
 * (issue #16), so the last assertion compares the first page taken alone with
 * the first page of the whole sequence — the page must not depend on laying out
 * the rest of the book.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // Fixed pixel sizes make the pagination assertion deterministic.
class ReaderPaginationTest {

    private val passage = buildString {
        repeat(80) { append("the quick brown fox jumps over the lazy dog and then keeps going. ") }
    }

    private val defaultStyles = ReaderStyles.of(ReadingPreferences.DEFAULT, mastery = MasteryPalette.Light)

    @Test
    fun aLongChapterSplitsIntoContiguousPages() = runComposeUiTest {
        lateinit var pages: List<ReaderPage>
        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val width = with(density) { 240.dp.roundToPx() }
            val height = with(density) { 160.dp.roundToPx() }
            val text = remember { AnnotatedString(passage) }
            pages = paginateChapter(
                text = text,
                layout = ReaderLayout(measurer, defaultStyles.body, width, height),
            ).toList()
        }
        waitForIdle()

        assertTrue(pages.size > 1, "a long chapter should span several pages")
        assertEquals(0, pages.first().start)
        assertEquals(passage.length, pages.last().end)
        pages.zipWithNext().forEach { (page, next) -> assertEquals(page.end, next.start, "pages must be contiguous") }
        pages.forEach { page -> assertTrue(page.length > 0, "a page must not be empty") }
    }

    @Test
    fun theFirstPageIsFoundWithoutLayingOutTheRest() = runComposeUiTest {
        lateinit var firstPage: ReaderPage
        lateinit var allPages: List<ReaderPage>
        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val width = with(density) { 240.dp.roundToPx() }
            val height = with(density) { 160.dp.roundToPx() }
            val text = remember { AnnotatedString(passage) }
            val layout = ReaderLayout(measurer, defaultStyles.body, width, height)
            firstPage = paginateChapter(text, layout).first()
            allPages = paginateChapter(text, layout).toList()
        }
        waitForIdle()

        assertTrue(allPages.size > 1)
        assertEquals(allPages.first(), firstPage, "the lazily found first page must match the full pagination")
    }

    @Test
    fun anEmptyChapterIsOneEmptyPage() = runComposeUiTest {
        lateinit var pages: List<ReaderPage>
        setContent {
            val measurer = rememberTextMeasurer()
            pages = paginateChapter(
                text = AnnotatedString(""),
                layout = ReaderLayout(measurer, defaultStyles.body, 240, 160),
            ).toList()
        }
        waitForIdle()

        assertEquals(listOf(ReaderPage(0, 0)), pages)
    }

    @Test
    fun thePageForAnOffsetIsTheOneContainingIt() {
        val pages = listOf(ReaderPage(0, 100), ReaderPage(100, 250), ReaderPage(250, 400))

        assertEquals(0, pageIndexFor(pages, 0))
        assertEquals(0, pageIndexFor(pages, 99))
        assertEquals(1, pageIndexFor(pages, 100))
        assertEquals(1, pageIndexFor(pages, 249))
        assertEquals(2, pageIndexFor(pages, 399))
        assertEquals(2, pageIndexFor(pages, 400), "an offset past the end clamps to the last page")
    }

    @Test
    fun aBiggerReadingPreferencePaginatesIntoMorePages() = runComposeUiTest {
        lateinit var smallPages: List<ReaderPage>
        lateinit var largePages: List<ReaderPage>
        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val height = with(density) { 160.dp.roundToPx() }
            val text = remember { AnnotatedString(passage) }
            fun pagesAt(size: Float): List<ReaderPage> {
                val styles = ReaderStyles.of(ReadingPreferences(size = size), mastery = MasteryPalette.Light)
                val width = with(density) { (240.dp - styles.horizontalMargin * 2).roundToPx() }
                return paginateChapter(text, ReaderLayout(measurer, styles.body, width, height)).toList()
            }
            smallPages = pagesAt(ReadingPreferences.MIN_SIZE)
            largePages = pagesAt(ReadingPreferences.MAX_SIZE)
        }
        waitForIdle()

        assertTrue(
            largePages.size > smallPages.size,
            "a bigger text must take more pages: ${smallPages.size} at ${ReadingPreferences.MIN_SIZE} vs " +
                "${largePages.size} at ${ReadingPreferences.MAX_SIZE}",
        )
    }

    /**
     * The blank-page regression (issue #239): the reader paginated the whole book
     * before the composition could draw, so a long book stayed empty for as long
     * as its pagination took. The frame the reader needs is the one that covers
     * the reading anchor; the rest of the book may follow afterwards.
     */
    @Test
    fun theFrameIsAwaitedOnceTheAnchorPageIsMeasured() = runComposeUiTest {
        val book = AnnotatedString(
            buildString {
                repeat(400) { append("the quick brown fox jumps over the lazy dog and then keeps going. ") }
            },
        )
        val anchor = book.length / 2
        var pagesWhenCovered = -1
        var frames = 0
        var total = -1

        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val width = with(density) { 240.dp.roundToPx() }
            val height = with(density) { 160.dp.roundToPx() }
            val layout = ReaderLayout(measurer, defaultStyles.body, width, height)
            LaunchedEffect(Unit) {
                val pages = mutableStateListOf<ReaderPage>()
                fillPages(
                    text = book,
                    layout = layout,
                    chapterStarts = emptySet(),
                    paginationStart = anchor,
                    pages = pages,
                ) {
                    frames++
                    if (pagesWhenCovered == -1 && pages.last().end > anchor) pagesWhenCovered = pages.size
                }
                total = pages.size
            }
        }
        waitForIdle()

        assertTrue(
            pagesWhenCovered in 1 until total,
            "the frame that covers the anchor must come before the whole book: " +
                "covered=$pagesWhenCovered frames=$frames total=$total anchor=$anchor",
        )
        assertTrue(frames > 1, "the loop keeps handing frames back, so the reader stays responsive")
    }
}
