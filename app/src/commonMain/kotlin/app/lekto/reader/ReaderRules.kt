package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordToken

/**
 * A word's dotted rule in the page's own text coordinates: from [left] to
 * [right] on the line whose bottom is [y]. The rule is what tells a reader who
 * cannot read the mastery hue that a word is being learned, so it is drawn by the
 * page rather than carried by the text as a [androidx.compose.ui.text.style.TextDecoration]
 * (docs/design.md §2.2, issue #281).
 */
internal data class RuleSpan(val left: Float, val right: Float, val y: Float)

/**
 * One colour's rules on a page, already collected into [path]: Compose strokes a
 * path in one paint, so a page carries one path per level colour it shows — four
 * at most — rather than one path per word.
 */
internal class RuleGroup(val colour: Color, val path: Path)

/** The page's dotted rules, one group per level colour, ready to be stroked in a draw pass. */
internal class PageRules(val groups: List<RuleGroup>)

/**
 * The dotted rule under [word], in the page's layout coordinates, or `null` when
 * the word is not laid out.
 *
 * The rule hangs at the bottom of the word's line box, so every word on a line
 * shares one height — a per-glyph bottom would make it zigzag under descenders.
 * A word the text breaks across two lines gets one span per line, the way the
 * text itself breaks it.
 */
internal fun ruleUnder(layout: TextLayoutResult, word: WordToken): List<RuleSpan> {
    if (word.start >= word.end || word.end > layout.layoutInput.text.length) return emptyList()
    val firstLine = layout.getLineForOffset(word.start)
    val lastLine = layout.getLineForOffset(word.end - 1)
    return (firstLine..lastLine).mapNotNull { line -> ruleOnLine(layout, line, word) }
}

/** [word]'s part on [line]: where it starts and ends there, at that line's bottom. */
private fun ruleOnLine(layout: TextLayoutResult, line: Int, word: WordToken): RuleSpan? {
    val start = maxOf(word.start, layout.getLineStart(line))
    val end = minOf(word.end, layout.getLineEnd(line))
    if (start >= end) return null
    val left = layout.getHorizontalPosition(start, usePrimaryDirection = true)
    val right = layout.getHorizontalPosition(end, usePrimaryDirection = true)
    return RuleSpan(left = minOf(left, right), right = maxOf(left, right), y = layout.getLineBottom(line))
}

/**
 * Every highlighted word's rule on the page, grouped by the colour that paints
 * it. A known word (level 4) has no colour and so no rule. The spans come from
 * the layout the page already keeps for hit-testing, so adding the rules costs no
 * text layout; the caller builds the paths once per page and style and only
 * strokes them (issue #251's lesson, issue #281).
 */
internal fun pageRules(
    layout: TextLayoutResult?,
    words: List<WordToken>,
    levelOf: (WordToken) -> MasteryLevel,
    ramp: MasteryRamp,
): PageRules {
    if (layout == null) return PageRules(emptyList())
    val paths = mutableMapOf<Color, Path>()
    words.forEach { word ->
        val colour = levelOf(word).readerColor(ramp)
        if (colour == Color.Unspecified) return@forEach
        val path = paths.getOrPut(colour) { Path() }
        ruleUnder(layout, word).forEach { rule ->
            path.moveTo(rule.left, rule.y)
            path.lineTo(rule.right, rule.y)
        }
    }
    return PageRules(paths.map { (colour, path) -> RuleGroup(colour, path) })
}
