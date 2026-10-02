package app.lekto.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.lekto.core.MasteryLevel
import app.lekto.core.text.BlockKind
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.TextBlock
import app.lekto.core.text.WordToken
import app.lekto.core.text.tokenise

/**
 * A chapter rendered as text plus the words in it. [text] concatenates the
 * blocks with a blank line between them; each [WordToken] in [words] carries
 * absolute offsets into [text], so a tap maps back to its word and a page slice
 * keeps its styling.
 */
class ReaderTokens(val text: AnnotatedString, val words: List<WordToken>)

/**
 * The word-token layer: turns the parser's [chapter] into one [AnnotatedString]
 * in which every word found by the [renderer]'s segmenter is coloured by its
 * mastery level and is tappable ([onWordTap]), every run keeps its inline
 * emphasis, and every block carries its heading or body style.
 *
 * Pure and free of composition, so it is unit tested directly: the semantics of
 * "every word coloured and tappable" are asserted on the returned string, not
 * through a rendered frame.
 */
fun buildReaderTokens(
    chapter: ReaderChapter,
    renderer: ReaderRenderer,
    onWordTap: (WordToken) -> Unit = {},
): ReaderTokens {
    val builder = AnnotatedString.Builder()
    val words = mutableListOf<WordToken>()
    val writer = WordWriter(builder, renderer.styles, onWordTap)
    var cursor = 0
    chapter.blocks.forEachIndexed { index, block ->
        if (index > 0) {
            builder.append("\n\n")
            cursor += 2
        }
        val blockStart = cursor
        appendBlock(builder, block, blockStart, renderer.styles)
        cursor += block.text.length

        tokenise(block.text, chapter.language, renderer.segmenter).words.forEach { token ->
            val word = token.shifted(blockStart)
            words += word
            writer.write(word, renderer.mastery.levelOf(word.surface, chapter.language))
        }
    }
    return ReaderTokens(text = builder.toAnnotatedString(), words = words)
}

/** Appends a block's text, its block-level style and its runs' inline styles. */
private fun appendBlock(builder: AnnotatedString.Builder, block: TextBlock, blockStart: Int, styles: ReaderStyles) {
    builder.append(block.text)
    if (block.kind == BlockKind.HEADING) {
        builder.addStyle(styles.heading.asSpanStyle(), blockStart, blockStart + block.text.length)
    }
    var runStart = blockStart
    block.runs.forEach { run ->
        val runStyle = run.styles.toSpanStyle()
        if (runStyle != SpanStyle()) builder.addStyle(runStyle, runStart, runStart + run.text.length)
        runStart += run.text.length
    }
}

/** The emphasis a run carries, as a span the reader renders. */
private fun Set<InlineStyle>.toSpanStyle(): SpanStyle = buildSpanStyle(
    italic = InlineStyle.EMPHASIS in this,
    bold = InlineStyle.STRONG in this,
    monospace = InlineStyle.CODE in this,
)

private fun buildSpanStyle(italic: Boolean, bold: Boolean, monospace: Boolean): SpanStyle {
    var style = SpanStyle()
    if (italic) style = style.copy(fontStyle = FontStyle.Italic)
    if (bold) style = style.copy(fontWeight = FontWeight.Bold)
    if (monospace) style = style.copy(fontFamily = FontFamily.Monospace)
    return style
}

/**
 * Colours and links one word at a time. Highlighted words (levels 0–3) get a
 * colour span and an underline; a known word (level 4) gets neither, so it reads
 * as normal text. The link carries the same colour so the platform's default
 * link tint never overrides the mastery palette.
 */
private class WordWriter(
    private val builder: AnnotatedString.Builder,
    private val styles: ReaderStyles,
    private val onWordTap: (WordToken) -> Unit,
) {
    fun write(word: WordToken, level: MasteryLevel) {
        val colour = level.readerColorOr(styles.body.color)
        val decoration = level.readerDecoration()
        builder.addStyle(SpanStyle(color = colour, textDecoration = decoration), word.start, word.end)
        builder.addLink(
            LinkAnnotation.Clickable(
                tag = "word:${word.start}",
                styles = TextLinkStyles(style = SpanStyle(color = colour, textDecoration = decoration)),
                linkInteractionListener = { onWordTap(word) },
            ),
            word.start,
            word.end,
        )
    }
}

/** The span-level fields of a block style, so a heading can be applied to a range. */
private fun TextStyle.asSpanStyle(): SpanStyle = SpanStyle(
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
)
