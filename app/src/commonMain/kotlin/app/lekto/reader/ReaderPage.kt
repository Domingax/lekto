@file:Suppress("MagicNumber") // Line and character indices are zero-based; the 0/1 literals are conventional.

package app.lekto.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/**
 * A half-open range of characters in the chapter's [AnnotatedString] that fits
 * one viewport. Pages are contiguous and ordered: page *n* ends where page
 * *n+1* begins, so no character is dropped or rendered twice.
 */
data class ReaderPage(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

/**
 * Everything pagination needs to lay a chapter out: the [measurer], the [style]
 * to measure with, and the viewport's pixel [width] and [height].
 */
data class ReaderLayout(val measurer: TextMeasurer, val style: TextStyle, val width: Int, val height: Int)

/** The first window to measure when looking for one page's worth of text. */
private const val FIRST_WINDOW = 1024

/**
 * Splits [text] into viewport-sized pages for [layout], **one page at a time**.
 *
 * Each element is found by measuring only a bounded run of text from the
 * previous page's end — never the whole chapter — so opening a long book lays
 * out its first page, not its every page (issue #16; the chunking the
 * paginated-reader spike names as the production path). The caller that wants
 * the whole book can still `toList()` the sequence.
 *
 * Pages are cut at line boundaries: a line taller than the viewport gets a page
 * of its own rather than looping forever. Measuring a run longer than the page
 * guarantees the cut line is a natural wrap and not the end of the measured
 * window, so the result is the same as laying out the whole chapter at once.
 */
fun paginateChapter(text: AnnotatedString, layout: ReaderLayout): Sequence<ReaderPage> = sequence {
    when {
        text.isEmpty() -> yield(ReaderPage(0, 0))

        layout.width <= 0 || layout.height <= 0 -> yield(ReaderPage(0, text.length))

        else -> {
            var start = 0
            while (start < text.length) {
                val page = nextPage(text, layout, start)
                yield(page)
                start = page.end
            }
        }
    }
}

/**
 * The index of the page in [pages] that contains character [offset], or the last
 * page once [offset] is past everything measured so far — which lets a reader
 * opening a partially paginated book at a saved offset show the best page it has.
 */
internal fun pageIndexFor(pages: List<ReaderPage>, offset: Int): Int {
    if (pages.isEmpty()) return 0
    val containing = pages.indexOfFirst { page -> offset < page.end }
    return if (containing == -1) pages.lastIndex else containing
}

/**
 * The page that begins at [start]: grow a window until it overflows the viewport
 * (or reaches the text's end), then cut at the first line that does not fit.
 */
private fun nextPage(text: AnnotatedString, layout: ReaderLayout, start: Int): ReaderPage {
    var window = FIRST_WINDOW
    while (true) {
        val end = (start + window).coerceAtMost(text.length)
        val result = layout.measurer.measure(
            text = text.subSequence(start, end),
            style = layout.style,
            constraints = Constraints(maxWidth = layout.width, maxHeight = Int.MAX_VALUE),
        )
        if (end == text.length || result.size.height > layout.height) {
            return cutPage(result, start, end, layout.height)
        }
        window *= 2
    }
}

/** The first line that would overflow [height], kept as a page range. */
private fun cutPage(result: TextLayoutResult, start: Int, end: Int, height: Int): ReaderPage {
    val pageTop = result.getLineTop(0)
    for (line in 1 until result.lineCount) {
        if (result.getLineBottom(line) - pageTop > height) {
            val cut = result.getLineStart(line)
            if (cut > 0) return ReaderPage(start, start + cut)
            break
        }
    }
    return ReaderPage(start, end)
}
