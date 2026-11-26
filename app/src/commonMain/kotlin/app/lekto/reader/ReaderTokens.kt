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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
    val blockStarts = appendBlocks(builder, chapter, styles, ImageLayout(0.sp, 0.sp))
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
fun buildChapterText(
    chapter: ReaderText,
    styles: ReaderStyles,
    imageLayout: ImageLayout = ImageLayout(0.sp, 0.sp),
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendBlocks(builder, chapter, styles, imageLayout)
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
private fun appendBlocks(
    builder: AnnotatedString.Builder,
    chapter: ReaderText,
    styles: ReaderStyles,
    imageLayout: ImageLayout,
): List<Int> {
    val blockStarts = mutableListOf<Int>()
    var cursor = 0
    chapter.blocks.forEachIndexed { index, block ->
        if (index > 0) {
            builder.append(BLOCK_SEPARATOR)
            cursor += BLOCK_SEPARATOR.length
        }
        blockStarts += cursor
        appendBlock(builder, block, cursor, styles, imageLayout)
        cursor += block.text.length
    }
    return blockStarts
}

/** Appends a block's text, its block-level style and its runs' inline styles. */
@Suppress("LongParameterList") // The block, its offset, the styles and the pictures' geometry.
private fun appendBlock(
    builder: AnnotatedString.Builder,
    block: TextBlock,
    blockStart: Int,
    styles: ReaderStyles,
    imageLayout: ImageLayout,
) {
    val image = block.image
    if (image != null) {
        // One inline placeholder instead of a character: the page draws the
        // picture in its rectangle, and the offsets stay the block's own.
        val placeholder = image.placeholderIn(imageLayout)
        builder.appendInlineContent(id = image.name, alternateText = IMAGE_PLACEHOLDER)
        // The body style's fixed line height would squeeze the picture's line and
        // let the text after it draw *behind* the picture: a paragraph style on
        // this one character gives the line the picture's own height (issue #263).
        builder.addStyle(
            ParagraphStyle(lineHeight = placeholder.height),
            blockStart,
            blockStart + IMAGE_PLACEHOLDER.length,
        )
        return
    }
    builder.append(block.text)
    appendKindStyle(builder, block, blockStart, styles)
    var runStart = blockStart
    block.runs.forEach { run ->
        val runStyle = run.styles.toSpanStyle(styles.body.fontSize)
        if (runStyle != SpanStyle()) builder.addStyle(runStyle, runStart, runStart + run.text.length)
        runStart += run.text.length
    }
}

/** Appends the paragraph and span styles a block's kind adds beyond the body. */
private fun appendKindStyle(
    builder: AnnotatedString.Builder,
    block: TextBlock,
    blockStart: Int,
    styles: ReaderStyles,
) {
    val blockEnd = blockStart + block.text.length
    when (block.kind) {
        BlockKind.HEADING -> {
            builder.addStyle(styles.heading.asSpanStyle(), blockStart, blockEnd)
            // The body is justified, but a heading is not: a title that wraps keeps
            // its ragged end, as a printed book's heading does.
            builder.addStyle(ParagraphStyle(textAlign = styles.heading.textAlign), blockStart, blockEnd)
        }

        BlockKind.LIST_ITEM -> builder.addStyle(styles.listParagraphOf(block.listDepth), blockStart, blockEnd)

        // The author's own ornament, centred on a line of its own.
        BlockKind.SCENE_BREAK -> builder.addStyle(
            ParagraphStyle(textAlign = TextAlign.Center),
            blockStart,
            blockEnd,
        )

        // A caption is set apart from the rows: italic and centred (#264).
        BlockKind.CAPTION -> {
            builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), blockStart, blockEnd)
            builder.addStyle(ParagraphStyle(textAlign = TextAlign.Center), blockStart, blockEnd)
        }

        // Preformatted text keeps the source's line breaks, so it is drawn
        // monospace from the start edge: justifying it would stretch each line
        // across the measure and destroy the shape it was written in.
        BlockKind.PREFORMATTED -> {
            builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace), blockStart, blockEnd)
            builder.addStyle(ParagraphStyle(textAlign = TextAlign.Start), blockStart, blockEnd)
        }

        // A row is held to the start edge: justification would stretch its
        // cells apart, and the row already reads as one line (#264).
        BlockKind.TABLE_ROW -> builder.addStyle(
            ParagraphStyle(textAlign = TextAlign.Start),
            blockStart,
            blockEnd,
        )

        else -> Unit
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

/** A superscript or subscript's size, as a share of the body's. */
private const val SUPERSCRIPT_RATIO: Float = 0.75f

/** Small print's size, as a share of the body's. */
private const val SMALL_RATIO: Float = 0.85f

/** The emphasis a run carries, as a span the reader renders. */
private fun Set<InlineStyle>.toSpanStyle(bodySize: TextUnit): SpanStyle = SpanStyle(
    fontStyle = if (InlineStyle.EMPHASIS in this) FontStyle.Italic else null,
    fontWeight = if (InlineStyle.STRONG in this) FontWeight.Bold else null,
    fontFamily = if (InlineStyle.CODE in this) FontFamily.Monospace else null,
    fontSize = fontSizeOf(bodySize),
    baselineShift = baselineShift(),
)

/**
 * The baseline a run sits on: a superscript or a subscript moves it, and a run
 * that is both takes the superscript — an overlap no book means literally.
 */
private fun Set<InlineStyle>.baselineShift(): BaselineShift? = when {
    InlineStyle.SUPERSCRIPT in this -> BaselineShift.Superscript
    InlineStyle.SUBSCRIPT in this -> BaselineShift.Subscript
    else -> null
}

/**
 * The size a run renders at: superscript and subscript at [SUPERSCRIPT_RATIO]
 * of the body, small print at [SMALL_RATIO]. A small superscript keeps the
 * smaller ratio, so the two never multiply into unreadable type.
 */
private fun Set<InlineStyle>.fontSizeOf(bodySize: TextUnit): TextUnit = when {
    InlineStyle.SUPERSCRIPT in this || InlineStyle.SUBSCRIPT in this -> bodySize * SUPERSCRIPT_RATIO
    InlineStyle.SMALL in this -> bodySize * SMALL_RATIO
    else -> TextUnit.Unspecified
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
