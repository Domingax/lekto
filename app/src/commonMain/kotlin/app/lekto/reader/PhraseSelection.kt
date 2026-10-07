package app.lekto.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import app.lekto.core.text.WordToken
import app.lekto.core.text.contextSentence

/**
 * A phrase the reader selected while reading: the [text] the user sees, its
 * [range] into the chapter text — so the panel and the reading position both use
 * the chapter's coordinates rather than the page's — and the [sentence] that
 * contains it, so a provider call can send the phrase in context (issue #89).
 * Like [WordToken], the range is half-open: `range.last` is the offset just past
 * the last character.
 */
data class PhraseSelection(val text: String, val range: IntRange, val sentence: String? = null)

/**
 * The page-local range covering the words from [anchor] to [current] inclusive
 * (in either order): from the earlier word's start to the later word's end, the
 * same half-open convention [WordToken] uses.
 */
internal fun phraseRangeAt(words: List<WordToken>, anchor: Int, current: Int): IntRange {
    val first = minOf(anchor, current)
    val last = maxOf(anchor, current)
    return words[first].start..words[last].end
}

/**
 * The index of the word under [position], or `null` when the position is not on
 * a word. With [snapToNearest] a position in the whitespace before or between
 * words resolves to the nearest word instead — when selecting a phrase, pressing
 * the gap beside a word should pick that word up, whereas a plain tap on the gap
 * must stay a page turn ([wordAt] keeps the strict resolution).
 */
@Suppress("ReturnCount", "LongParameterList") // Three resolution paths; the page's geometry travels together.
internal fun wordIndexAt(
    position: Offset,
    marginPx: Int,
    words: List<WordToken>,
    layout: TextLayoutResult?,
    snapToNearest: Boolean = false,
): Int? {
    val local = Offset(position.x - marginPx, position.y)
    val offset = layout
        ?.takeIf { result -> local.x in 0f..result.size.width.toFloat() && local.y in 0f..result.size.height.toFloat() }
        ?.getOffsetForPosition(local)
        ?: return null
    val exact = words.indexOfFirst { word -> offset in word.start until word.end }
    if (exact >= 0 || !snapToNearest) return exact.takeIf { index -> index >= 0 }
    return words.indexOfFirst { word -> word.start >= offset }.takeIf { index -> index >= 0 }
        ?: words.indexOfLast { word -> word.end <= offset }.takeIf { index -> index >= 0 }
}

/** Lifts a page-local [range] to the chapter coordinates and cuts the phrase [chapter] holds there. */
internal fun phraseInChapter(chapter: String, pageStart: Int, range: IntRange): PhraseSelection {
    val start = range.first + pageStart
    val end = range.last + pageStart
    return PhraseSelection(
        text = chapter.substring(start, end),
        range = start..end,
        sentence = contextSentence(chapter, start, end),
    )
}
