package app.lekto.reader

import androidx.compose.ui.graphics.Color
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
fun buildReaderTokens(
    chapter: ReaderChapter,
    renderer: ReaderRenderer,
    selected: IntRange? = null,
    onWordTap: (WordTap) -> Unit = {},
): ReaderTokens {
    val builder = AnnotatedString.Builder()
    val blockStarts = appendBlocks(builder, chapter, renderer.styles)
    val text = chapter.blocks.joinToString(separator = "\n\n") { block -> block.text }
    val words = mutableListOf<WordToken>()
    val writer = WordWriter(builder, renderer.styles, selected, onWordTap, offset = 0, text = text)
    chapter.blocks.forEachIndexed { index, block ->
        tokenise(block.text, chapter.language, renderer.segmenter, renderer.lemmas).words.forEach { token ->
            val word = token.shifted(blockStarts[index])
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
fun buildChapterText(chapter: ReaderChapter, styles: ReaderStyles): AnnotatedString {
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
    chapter: ReaderChapter,
    selected: IntRange? = null,
    onWordTap: (WordTap) -> Unit = {},
): ReaderTokens {
    val slice = chapterText.subSequence(page.start, page.end)
    val builder = AnnotatedString.Builder(slice)
    val words = mutableListOf<WordToken>()
    val writer = WordWriter(builder, renderer.styles, selected, onWordTap, page.start, chapterText.text)
    tokenise(slice.text, chapter.language, renderer.segmenter, renderer.lemmas).words.forEach { token ->
        words += token
        writer.write(token, renderer.mastery.levelOf(token.key))
    }
    return ReaderTokens(builder.toAnnotatedString(), words)
}

/** Appends each block, its block-level style and its runs' inline styles; returns each block's start offset. */
private fun appendBlocks(builder: AnnotatedString.Builder, chapter: ReaderChapter, styles: ReaderStyles): List<Int> {
    val blockStarts = mutableListOf<Int>()
    var cursor = 0
    chapter.blocks.forEachIndexed { index, block ->
        if (index > 0) {
            builder.append("\n\n")
            cursor += 2
        }
        blockStarts += cursor
        appendBlock(builder, block, cursor, styles)
        cursor += block.text.length
    }
    return blockStarts
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
 * as normal text. The [selected] word is additionally washed with
 * [SelectionHighlight], so the word stays visible behind the open lookup panel.
 * The link carries the same colour so the platform's default link tint never
 * overrides the mastery palette.
 *
 * [offset] maps the local word to the chapter's coordinates for the tap and the
 * selection test, while the span and link stay local; [text] is the chapter,
 * used to cut the tapped word's context sentence (issue #22).
 */
@Suppress("LongParameterList") // The writer's inputs are the builder's state; a bundle would only hide that.
private class WordWriter(
    private val builder: AnnotatedString.Builder,
    private val styles: ReaderStyles,
    private val selected: IntRange?,
    private val onWordTap: (WordTap) -> Unit,
    private val offset: Int,
    private val text: String,
) {
    fun write(word: WordToken, level: MasteryLevel) {
        val colour = level.readerColorOr(styles.body.color)
        val decoration = level.readerDecoration()
        val globalStart = word.start + offset
        val globalEnd = word.end + offset
        val background = if (selected == globalStart..globalEnd) SelectionHighlight else Color.Unspecified
        val span = SpanStyle(color = colour, textDecoration = decoration, background = background)
        builder.addStyle(span, word.start, word.end)
        builder.addLink(
            LinkAnnotation.Clickable(
                tag = "word:${word.start}",
                styles = TextLinkStyles(style = span),
                linkInteractionListener = {
                    onWordTap(
                        WordTap(
                            token = word.shifted(offset),
                            contextSentence = contextSentence(text, globalStart, globalEnd),
                        ),
                    )
                },
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
