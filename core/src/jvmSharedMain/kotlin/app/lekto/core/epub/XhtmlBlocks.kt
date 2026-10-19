package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.ByteArrayInputStream

/**
 * Turns one XHTML content document into [TextBlock]s.
 *
 * It uses jsoup's HTML parser rather than a strict XML one on purpose: real
 * EPUBs carry named HTML entities (`&nbsp;`, `&mdash;`), unclosed tags and
 * XHTML5 they never declared, and the HTML parser accepts all of it while a
 * validating XML parser rejects the book. The walk is block-first: a block
 * element becomes one [TextBlock] whose runs come from its inline descendants,
 * and containers (`div`, `blockquote`, `ul`, `table`, …) are descended into so
 * their child blocks keep their place in the reading order.
 */
internal object XhtmlBlocks {

    /** Parses [bytes] into blocks, in document order. */
    fun of(bytes: ByteArray): List<TextBlock> {
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        val blocks = mutableListOf<TextBlock>()
        descend(document.body(), blocks)
        return blocks
    }

    /** Reads every child of [container] in order, keeping their place in the reading order. */
    private fun descend(container: Element, into: MutableList<TextBlock>) {
        for (child in container.children()) collectBlock(child, into)
    }

    private fun collectBlock(element: Element, into: MutableList<TextBlock>) {
        val tag = element.normalName()
        when {
            tag in SKIPPED -> Unit

            tag in CONTAINERS -> descend(element, into)

            tag in BLOCKS -> runsOf(element).takeIf { it.isNotEmpty() }?.let { into += block(tag, it) }

            // An element the format does not classify: usually an inline wrapper
            // whose text belongs in a paragraph. But a heading that writes its
            // anchor self-closing (`<a id="toc"/>Title`) leaves the HTML parser
            // with an `<a>` it never closes, and the rest of the chapter lands
            // inside it — so descend when the element holds blocks, or that
            // chapter would be lost.
            element.wrapsBlocks -> descend(element, into)

            else -> runsOf(element).takeIf { it.isNotEmpty() }?.let { into += TextBlock(BlockKind.PARAGRAPH, it) }
        }
    }

    /** Whether the element holds a block-level child, so the walk must descend into it. */
    private val Element.wrapsBlocks: Boolean
        get() = children().any { child -> child.normalName() in BLOCKS || child.normalName() in CONTAINERS }

    private fun runsOf(element: Element): List<TextRun> {
        val characters = mutableListOf<StyledChar>()
        collectInline(element, emptySet(), characters)
        return toRuns(characters)
    }

    private fun collectInline(element: Element, styles: Set<InlineStyle>, into: MutableList<StyledChar>) {
        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> node.wholeText.forEach { character -> into += StyledChar(character, styles) }
                is Element -> collectElement(node, styles, into)
                else -> Unit
            }
        }
    }

    private fun collectElement(element: Element, styles: Set<InlineStyle>, into: MutableList<StyledChar>) {
        when (val tag = element.normalName()) {
            in CONTAINERS, in BLOCKS, in SKIPPED -> Unit
            "br" -> into += StyledChar(' ', styles)
            else -> collectInline(element, styles + styleOf(tag), into)
        }
    }

    private fun styleOf(tag: String): Set<InlineStyle> = when (tag) {
        "em", "i" -> setOf(InlineStyle.EMPHASIS)
        "strong", "b" -> setOf(InlineStyle.STRONG)
        "code", "kbd", "samp", "tt" -> setOf(InlineStyle.CODE)
        else -> emptySet()
    }

    /**
     * Collapses every whitespace run — including the non-breaking space jsoup
     * decodes `&nbsp;` to — to a single space, trims the block's ends, and
     * re-groups the characters into runs of one style.
     */
    private fun toRuns(characters: List<StyledChar>): List<TextRun> {
        val runs = mutableListOf<TextRun>()
        var afterSpace = true
        for (character in characters) {
            if (character.isSpace()) {
                if (!afterSpace) append(runs, " ", character.styles)
                afterSpace = true
            } else {
                append(runs, character.value.toString(), character.styles)
                afterSpace = false
            }
        }
        return withoutTrailingSpace(runs)
    }

    private fun append(runs: MutableList<TextRun>, text: String, styles: Set<InlineStyle>) {
        val last = runs.lastOrNull()
        if (last != null && last.styles == styles) {
            runs[runs.lastIndex] = last.copy(text = last.text + text)
        } else {
            runs += TextRun(text, styles)
        }
    }

    private fun withoutTrailingSpace(runs: List<TextRun>): List<TextRun> {
        val last = runs.lastOrNull() ?: return runs
        return when {
            !last.text.endsWith(" ") -> runs
            last.text.isBlank() -> runs.dropLast(1)
            else -> runs.dropLast(1) + last.copy(text = last.text.trimEnd())
        }
    }

    private fun block(tag: String, runs: List<TextRun>): TextBlock = when {
        tag in HEADINGS -> TextBlock(BlockKind.HEADING, runs, headingLevel = tag.substring(1).toInt())
        tag == "li" -> TextBlock(BlockKind.LIST_ITEM, runs)
        else -> TextBlock(BlockKind.PARAGRAPH, runs)
    }

    /** Elements descended into so their child blocks keep the reading order. */
    private val CONTAINERS = setOf(
        "body", "div", "section", "article", "blockquote", "ul", "ol",
        "table", "tbody", "thead", "tfoot", "tr", "nav", "header", "footer", "aside", "figure",
    )

    /** Leaf blocks: one [TextBlock] each. */
    private val BLOCKS = setOf(
        "p", "li", "dt", "dd", "pre", "figcaption", "td", "th",
        "h1", "h2", "h3", "h4", "h5", "h6",
    )

    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    /** Non-text elements whose content is never part of the book text. */
    private val SKIPPED = setOf("script", "style", "head", "title", "img", "svg", "audio", "video", "iframe")

    private data class StyledChar(val value: Char, val styles: Set<InlineStyle>) {
        fun isSpace(): Boolean = value.isWhitespace() || Character.isSpaceChar(value)
    }
}
