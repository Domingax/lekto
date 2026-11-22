package app.lekto.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordToken

/**
 * The dotted rule under a highlighted word (docs/design.md §2.2): 1 dp thick,
 * a 1 dp dash and a 2 dp gap, so it reads as a rule rather than an underline.
 * Exposed to the text-free planches so they can draw the same stroke; the guide's
 * page uses [pageRules] itself rather than a parallel drawing.
 */
internal val RULE_THICKNESS = 1.dp
private val RULE_DASH = 1.dp
private val RULE_GAP = 2.dp

/** The dash the page strokes its rules with, at [density]: the one source for the page and its planches. */
internal fun dottedRuleEffect(density: Density): PathEffect = PathEffect.dashPathEffect(
    floatArrayOf(with(density) { RULE_DASH.toPx() }, with(density) { RULE_GAP.toPx() }),
)

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
 * The dotted rule under [word], in the page's layout coordinates: empty when the
 * word is not laid out.
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

/**
 * The page's dotted rules, computed once per page and style and remembered on the
 * page's word layer plus the layout the text just reported — so a frame strokes
 * the paths instead of asking for a box per word (issue #281).
 */
@Composable
internal fun rememberPageRules(
    layout: TextLayoutResult?,
    tokens: ReaderTokens?,
    document: ReaderDocument,
    styles: ReaderStyles,
): PageRules = remember(layout, tokens, document.renderer.mastery, styles.mastery, document.masteryRevision) {
    pageRules(
        layout = layout,
        words = tokens?.words.orEmpty(),
        levelOf = { word -> document.renderer.mastery.levelOf(word.key) },
        ramp = styles.mastery,
    )
}

/**
 * Strokes [rules] behind the page's text, in the text's own coordinates: call it
 * inside the text's horizontal inset. The stroke is remembered for the density it
 * draws at, and every rule of a colour is one path, so a frame costs one draw per
 * level and no layout query at all (issue #281).
 */
internal fun Modifier.pageRules(rules: PageRules): Modifier = composed {
    val density = LocalDensity.current
    val stroke = remember(density) {
        Stroke(
            width = with(density) { RULE_THICKNESS.toPx() },
            pathEffect = dottedRuleEffect(density),
        )
    }
    drawBehind {
        rules.groups.forEach { group -> drawPath(group.path, group.colour, style = stroke) }
    }
}

/**
 * The page's text and its dotted rules in one coordinate space (issue #277): the
 * box that applies [horizontalInset] is the box that strokes [rules], so a rule's
 * left edge is its word's measured left edge, not a second inset that can drift.
 * The reader's page and the guide's embedded block both draw through this, so the
 * two cannot diverge.
 */
@Composable
internal fun PageRulesSurface(
    rules: PageRules,
    horizontalInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalInset)
            .pageRules(rules),
        content = content,
    )
}
