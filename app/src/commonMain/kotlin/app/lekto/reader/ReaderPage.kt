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

/**
 * Splits [text] into viewport-sized pages for [layout].
 *
 * The chapter is laid out once at the layout's width with unbounded height, then
 * cut at line boundaries: lines are accumulated until the next would overflow
 * the height. Measuring the whole chapter once keeps pages stable as the
 * viewport scrolls, at the cost of a layout proportional to the chapter — the
 * spike report records the measured cost and when chunking becomes necessary. A
 * line taller than the viewport is never split: it gets a page of its own rather
 * than looping forever.
 */
fun paginateChapter(text: AnnotatedString, layout: ReaderLayout): List<ReaderPage> = when {
    text.isEmpty() -> listOf(ReaderPage(0, 0))
    layout.width <= 0 || layout.height <= 0 -> listOf(ReaderPage(0, text.length))
    else -> splitIntoPages(measure(text, layout), text.length, layout.height)
}

private fun measure(text: AnnotatedString, layout: ReaderLayout): TextLayoutResult = layout.measurer.measure(
    text = text,
    style = layout.style,
    constraints = Constraints(maxWidth = layout.width, maxHeight = Int.MAX_VALUE),
)

/** Walks the lines and cuts a page whenever the next line would overflow the height. */
private fun splitIntoPages(layout: TextLayoutResult, textLength: Int, height: Int): List<ReaderPage> {
    val pages = mutableListOf<ReaderPage>()
    var pageFirstLine = 0
    var pageTop = layout.getLineTop(0)
    for (line in 1 until layout.lineCount) {
        if (layout.getLineBottom(line) - pageTop > height) {
            pages += ReaderPage(layout.getLineStart(pageFirstLine), layout.getLineStart(line))
            pageFirstLine = line
            pageTop = layout.getLineTop(line)
        }
    }
    pages += ReaderPage(layout.getLineStart(pageFirstLine), textLength)
    return pages
}
