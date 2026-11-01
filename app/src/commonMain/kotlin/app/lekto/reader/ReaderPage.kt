@file:Suppress("MagicNumber") // Line and character indices are zero-based; the 0/1 literals are conventional.

package app.lekto.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
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
 * to measure with, the viewport's pixel [width] and [height], and the
 * [placeholders] an image block occupies, so the rectangle is measured exactly
 * where the page will draw it.
 */
data class ReaderLayout(
    val measurer: TextMeasurer,
    val style: TextStyle,
    val width: Int,
    val height: Int,
    val placeholders: List<AnnotatedString.Range<Placeholder>> = emptyList(),
)

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
 *
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
            placeholders = layout.placeholders.within(start, end),
            constraints = Constraints(maxWidth = layout.width, maxHeight = Int.MAX_VALUE),
        )
        if (end == text.length || result.size.height > layout.height) {
            return cutPage(result, start, end, layout.height)
        }
        window *= 2
    }
}

/** The placeholders a window holds, rebased on the window's own start. */
private fun List<AnnotatedString.Range<Placeholder>>.within(
    start: Int,
    end: Int,
): List<AnnotatedString.Range<Placeholder>> = mapNotNull { range ->
    if (range.end <= start || range.start >= end) {
        null
    } else {
        AnnotatedString.Range(range.item, range.start - start, range.end - start)
    }
}

/**
 * The first line that would overflow [height], kept as a page range. A line is
 * measured from the window's own origin, so a page that starts with a tall
 * picture counts the picture's whole height.
 *
 * When the very first line is taller than the page — a plate that fills it — the
 * page is that line alone. Returning the whole window instead would clip
 * everything the line hides, and the text would be lost: the page after it starts
 * where the window ended.
 */
private fun cutPage(result: TextLayoutResult, start: Int, end: Int, height: Int): ReaderPage {
    if (result.size.height <= height) return ReaderPage(start, end)

    val overflowing = (1 until result.lineCount).firstOrNull { line -> result.getLineBottom(line) > height }
    val cut = overflowing
        ?.let { line -> result.getLineStart(line) }
        ?.takeIf { lineStart -> lineStart > 0 }
        // The first line alone fills the page: it is the page, up to its last
        // visible character. A trailing line break is left to the next page, or
        // measuring the page would count an empty line it never draws.
        ?: withoutBreaks(result.layoutInput.text, firstLineEnd(result, result.layoutInput.text.length))
    return ReaderPage(start, start + cut)
}

/** Where the first line ends; the text's own end when it is the only line. */
private fun firstLineEnd(result: TextLayoutResult, fallback: Int): Int =
    if (result.lineCount > 1) result.getLineStart(1) else fallback

/** How much of the window to keep: a trailing line break is left to the next page. */
private fun withoutBreaks(window: AnnotatedString, end: Int): Int {
    var cut = end
    while (cut > 1 && window[cut - 1] == '\n') cut--
    return cut
}
