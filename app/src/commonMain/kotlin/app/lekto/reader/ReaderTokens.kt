package app.lekto.reader

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import app.lekto.core.MasteryLevel
import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.BlockKind
import app.lekto.core.text.IMAGE_PLACEHOLDER
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.TextBlock
import app.lekto.core.text.WordToken
import app.lekto.core.text.contextSentence
import app.lekto.core.text.tokenise

/**
 * A chapter rendered as text plus the words in it. [text] concatenates the
 * blocks with a blank line between them; each [WordToken] in [words] carries
 * offsets into [text], so a tap maps back to its word and a page slice keeps its
 * styling.
 */
class ReaderTokens(val text: AnnotatedString, val words: List<WordToken>)

/**
 * A word the reader tapped, with the **Context sentence** it was found in
 * (issue #22). The [token]'s offsets are into the chapter text, so the sentence
 * survives a page boundary; [contextSentence] is `null` when it cannot be read.
 */
data class WordTap(val token: WordToken, val contextSentence: String?)

/**
 * The whole chapter's word layer: every word coloured by mastery and tappable.
 * Used where the whole chapter is wanted at once (the sample, the golden, the
 * tokenisation tests); the reader builds only the visible page's layer with
 * [buildPageTokens], so opening a book does not tokenise every word in it.
 *
 * [selected] marks the word whose lookup panel is open, so it stays visibly
 * highlighted while the panel is up.
 */
@Suppress("LongParameterList") // The chapter, its renderer, its styles and the tap; a bundle would only hide that.
fun buildReaderTokens(
    chapter: ReaderText,
    renderer: ReaderRenderer,
    styles: ReaderStyles,
    selected: IntRange? = null,
): ReaderTokens {
    val builder = AnnotatedString.Builder()
    val blockStarts = appendBlocks(builder, chapter, styles)
    val markers = chapter.markerRanges()
    val words = mutableListOf<WordToken>()
    val writer = WordWriter(builder, styles, selected)
    chapter.blocks.forEachIndexed { index, block ->
        tokenise(block.text, chapter.language, renderer.segmenter, renderer.lemmas).words.forEach { token ->
            val word = token.shifted(blockStarts[index])
            if (markers.isMarker(word.start)) return@forEach
            words += word
            writer.write(word, renderer.mastery.levelOf(word.key))
        }
    }
    return ReaderTokens(builder.toAnnotatedString(), words)
}

/**
 * The chapter as one styled [AnnotatedString] with **no word layer**. It is what
 * pagination measures: colouring and links do not change a glyph's width, so the
 * line breaks are the same as the tokenised text's, and building it costs a
 * string copy rather than a tokenisation per word.
 */
fun buildChapterText(chapter: ReaderText, styles: ReaderStyles): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendBlocks(builder, chapter, styles)
    return builder.toAnnotatedString()
}

/**
 * The word layer for one [page]: [page] sliced out of [chapterText] — keeping
 * its block and inline styles — with every word in the slice coloured and
 * tappable ([onWordTap]). The returned offsets are local to the slice, so the
 * reader tokenises one page's words, not the whole book's, and can hit-test them
 * against the page's layout.
 *
 * The token handed to [onWordTap] is shifted back to the chapter's coordinates,
 * and its context sentence is cut from [chapterText], so a sentence that spans a
 * page break is not truncated (issue #22).
 *
 * Pages are cut at line boundaries, so a word is never split across two of them;
 * tokenising the slice finds the same words tokenising the chapter would.
 */
@Suppress("LongParameterList") // The chapter and its renderer are a dyad; bundling them would only hide that.
fun buildPageTokens(
    chapterText: AnnotatedString,
    page: ReaderPage,
    renderer: ReaderRenderer,
    chapter: ReaderText,
    styles: ReaderStyles,
    selected: IntRange? = null,
): ReaderTokens {
    val slice = chapterText.subSequence(page.start, page.end)
    val builder = AnnotatedString.Builder(slice)
    val markers = chapter.markerRanges()
    val words = mutableListOf<WordToken>()
    val writer = WordWriter(builder, styles, selected)
    tokenise(slice.text, chapter.language, renderer.segmenter, renderer.lemmas).words.forEach { token ->
        if (markers.isMarker(page.start + token.start)) return@forEach
        words += token
        writer.write(token, renderer.mastery.levelOf(token.key))
    }
    return ReaderTokens(builder.toAnnotatedString(), words)
}

/** Whether [offset] falls inside a list marker: a bullet or a rank is drawn, but is never a word. */
private fun List<IntRange>.isMarker(offset: Int): Boolean = any { range -> offset in range }

/** Appends each block, its block-level style and its runs' inline styles; returns each block's start offset. */
private fun appendBlocks(builder: AnnotatedString.Builder, chapter: ReaderText, styles: ReaderStyles): List<Int> {
    val blockStarts = mutableListOf<Int>()
    var cursor = 0
    chapter.blocks.forEachIndexed { index, block ->
        if (index > 0) {
            builder.append(BLOCK_SEPARATOR)
            cursor += BLOCK_SEPARATOR.length
        }
        blockStarts += cursor
        appendBlock(builder, block, cursor, styles)
        cursor += block.text.length
    }
    return blockStarts
}

/** Appends a block's text, its block-level style and its runs' inline styles. */
private fun appendBlock(builder: AnnotatedString.Builder, block: TextBlock, blockStart: Int, styles: ReaderStyles) {
    val image = block.image
    if (image != null) {
        // One inline placeholder instead of a character: the page draws the
        // picture in its rectangle, and the offsets stay the block's own.
        builder.appendInlineContent(id = image.name, alternateText = IMAGE_PLACEHOLDER)
        return
    }
    builder.append(block.text)
    if (block.kind == BlockKind.HEADING) {
        builder.addStyle(styles.heading.asSpanStyle(), blockStart, blockStart + block.text.length)
    }
    if (block.kind == BlockKind.LIST_ITEM) {
        builder.addStyle(styles.listParagraphOf(block.listDepth), blockStart, blockStart + block.text.length)
    }
    var runStart = blockStart
    block.runs.forEach { run ->
        val runStyle = run.styles.toSpanStyle()
        if (runStyle != SpanStyle()) builder.addStyle(runStyle, runStart, runStart + run.text.length)
        runStart += run.text.length
    }
}

/**
 * A list item's hanging indent: its marker starts at its level's indent, and the
 * lines it wraps to align one step further in, so the text of a multi-line item
 * lines up under the first word rather than under the bullet.
 */
private fun ReaderStyles.listParagraphOf(depth: Int): ParagraphStyle = ParagraphStyle(
    textIndent = TextIndent(
        firstLine = listIndent * depth.toFloat(),
        restLine = listIndent * (depth + 1).toFloat(),
    ),
)

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
 * Paints one word's mastery: its colour, its underline and, while it is being
 * selected, its wash. Highlighted words (levels 0–3) get a colour span and an
 * underline; a known word (level 4) gets neither, so it reads as normal text. A
 * word inside the [selected] range is additionally washed with
 * [selectionHighlight], so a tapped word — or every word of a selected phrase —
 * stays visible behind the open lookup panel.
 *
 * The word carries **no link**: a link per word is ~55 ms of text layout on
 * Android, paid on every page turn, so the page hit-tests the tap instead
 * ([readerSurfaceTap], ADR-0007).
 */
private class WordWriter(
    private val builder: AnnotatedString.Builder,
    private val styles: ReaderStyles,
    private val selected: IntRange?,
) {
    fun write(word: WordToken, level: MasteryLevel) {
        val colour = level.readerColorOr(styles.body.color, styles.mastery)
        val decoration = level.readerDecoration()
        val washed = selected != null && word.start >= selected.first && word.end <= selected.last
        val background = if (washed) selectionHighlight(styles.body.color) else Color.Unspecified
        builder.addStyle(
            SpanStyle(color = colour, textDecoration = decoration, background = background),
            word.start,
            word.end,
        )
    }
}

/** The lookup a tap on [word] opens, with the sentence it sits in ([pageStart] is the page's offset). */
internal fun wordTapOf(word: WordToken, pageStart: Int, chapterText: String): WordTap {
    val start = word.start + pageStart
    return WordTap(
        token = word.shifted(pageStart),
        contextSentence = contextSentence(chapterText, start, start + word.surface.length),
    )
}

/** The span-level fields of a block style, so a heading can be applied to a range. */
private fun TextStyle.asSpanStyle(): SpanStyle = SpanStyle(
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
)
