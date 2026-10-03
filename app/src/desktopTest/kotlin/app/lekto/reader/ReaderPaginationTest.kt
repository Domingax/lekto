package app.lekto.reader

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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
                layout = ReaderLayout(measurer, ReaderStyles.Reading.body, width, height),
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
            val layout = ReaderLayout(measurer, ReaderStyles.Reading.body, width, height)
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
                layout = ReaderLayout(measurer, ReaderStyles.Reading.body, 240, 160),
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
}
