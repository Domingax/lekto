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
 * the whole chapter, and more than one page for a long one.
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
            )
        }
        waitForIdle()

        assertTrue(pages.size > 1, "a long chapter should span several pages")
        assertEquals(0, pages.first().start)
        assertEquals(passage.length, pages.last().end)
        pages.zipWithNext().forEach { (page, next) -> assertEquals(page.end, next.start, "pages must be contiguous") }
        pages.forEach { page -> assertTrue(page.length > 0, "a page must not be empty") }
    }

    @Test
    fun anEmptyChapterIsOneEmptyPage() = runComposeUiTest {
        lateinit var pages: List<ReaderPage>
        setContent {
            val measurer = rememberTextMeasurer()
            pages = paginateChapter(
                text = AnnotatedString(""),
                layout = ReaderLayout(measurer, ReaderStyles.Reading.body, 240, 160),
            )
        }
        waitForIdle()

        assertEquals(listOf(ReaderPage(0, 0)), pages)
    }
}
