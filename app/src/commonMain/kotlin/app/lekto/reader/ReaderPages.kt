package app.lekto.reader

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.AnnotatedString

/** How many pages to measure between frames, so a long chapter never blocks the UI. */
private const val PAGE_YIELD_EVERY = 8

/** How many pages past the reader's own to measure, so a turn never waits. */
private const val PAGES_AHEAD = 3

/** The start of the chapter holding [offset], or 0 before the first one. */
internal fun chapterStartAt(offset: Int, chapterStarts: Set<Int>): Int =
    chapterStarts.filter { start -> start <= offset }.maxOrNull() ?: 0

/** The start of the chapter after [start], or `null` when [start] opens the last one. */
internal fun chapterEndAfter(start: Int, chapterStarts: Set<Int>): Int? =
    chapterStarts.filter { next -> next > start }.minOrNull()

/**
 * Measures the chapter holding [anchor], from its start to a few pages past the
 * anchor, and no further. A chapter starts on a new page (ADR-0037), so its
 * start is the boundary the paginator can trust: every page before the anchor is
 * known exactly, `previous` never has to guess, and the cost of opening a book
 * is bounded by one chapter rather than by the book (ADR-0040, issue #241).
 *
 * The pages already measured stay in [pages] while the reader stays inside the
 * chapter, so turning a page measures the pages it reveals, not the chapter
 * again. A frame is asked for once the anchor's page exists, and every
 * [PAGE_YIELD_EVERY] pages before that, so a long chapter never blocks the UI
 * (issue #239).
 */
@Suppress("LongParameterList") // The window's inputs are the text, its layout, its bounds and its state list.
internal suspend fun fillWindow(
    text: AnnotatedString,
    layout: ReaderLayout,
    chapterStarts: Set<Int>,
    anchor: Int,
    pages: SnapshotStateList<ReaderPage>,
    awaitFrame: suspend () -> Unit,
) {
    // A chapter entry can point past the last block; clamping keeps the window
    // from being empty forever, which would leave the reader with no page at all.
    val start = chapterStartAt(anchor, chapterStarts).coerceAtMost(text.lastIndex.coerceAtLeast(0))
    if (pages.firstOrNull()?.start != start) pages.clear()
    if (text.isEmpty()) {
        pages += ReaderPage(0, 0)
        return
    }
    val chapter = Chapter(text, layout, chapterEndAfter(start, chapterStarts) ?: text.length)
    var measured = pages.lastOrNull()?.end ?: start
    var sinceFrame = 0
    while (measured <= anchor && measured < chapter.end) {
        measured = chapter.measure(pages, measured)
        if (++sinceFrame >= PAGE_YIELD_EVERY) {
            sinceFrame = 0
            awaitFrame()
        }
    }
    // The anchor's page exists: the reader can draw, and the pages ahead may
    // follow without holding its frame back (issue #239).
    awaitFrame()
    val lookaheadEnd = pages.indexOfLast { page -> page.start <= anchor } + 1 + PAGES_AHEAD
    while (pages.size < lookaheadEnd && measured < chapter.end) {
        measured = chapter.measure(pages, measured)
    }
}

/** One chapter of the rendered text, measured a page at a time. */
private class Chapter(private val text: AnnotatedString, private val layout: ReaderLayout, val end: Int) {
    /** Measures the page beginning at [from], appends it to [pages], and returns where it ends. */
    fun measure(pages: MutableList<ReaderPage>, from: Int): Int {
        val page = paginateChapter(text.subSequence(from, end), layout).first()
        pages += ReaderPage(page.start + from, page.end + from)
        return page.end + from
    }
}
