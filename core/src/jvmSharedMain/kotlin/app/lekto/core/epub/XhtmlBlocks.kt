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
 * One content document: the blocks it contributes to the book and the fragment
 * anchors it exposes (ADR-0037), named id or legacy `<a name>` to block index.
 */
internal data class XhtmlDocument(val blocks: List<TextBlock>, val anchors: Map<String, Int>)

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
@Suppress("TooManyFunctions") // The walk, its inline pass and its two style helpers are one parser.
internal object XhtmlBlocks {

    /** Parses [bytes] into blocks, in document order. */
    fun of(bytes: ByteArray): List<TextBlock> = parse(bytes).blocks

    /**
     * Parses [bytes] into blocks and the fragment anchors the document exposes
     * (ADR-0037): an element's `id` (or a legacy `<a name>`) mapped to the block
     * that holds it, so a TOC fragment can become a block index.
     */
    fun parse(bytes: ByteArray): XhtmlDocument {
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        val blocks = mutableListOf<TextBlock>()
        val anchors = mutableMapOf<String, Int>()
        descend(document.body(), blocks, anchors)
        return XhtmlDocument(blocks, anchors)
    }

    /** Reads every child of [container] in order, keeping their place in the reading order. */
    private fun descend(container: Element, into: MutableList<TextBlock>, anchors: MutableMap<String, Int>) {
        for (child in container.children()) collectBlock(child, into, anchors)
    }

    private fun collectBlock(element: Element, into: MutableList<TextBlock>, anchors: MutableMap<String, Int>) {
        element.recordAnchors(into.size, anchors)
        val tag = element.normalName()
        when {
            tag in SKIPPED -> Unit

            tag in CONTAINERS -> descend(element, into, anchors)

            tag in BLOCKS -> runsOf(element, into.size, anchors)
                .takeIf { it.isNotEmpty() }
                ?.let { runs -> into += block(tag, runs) }

            // An element the format does not classify: usually an inline wrapper
            // whose text belongs in a paragraph. But a heading that writes its
            // anchor self-closing (`<a id="toc"/>Title`) leaves the HTML parser
            // with an `<a>` it never closes, and the rest of the chapter lands
            // inside it — so descend when the element holds blocks, or that
            // chapter would be lost.
            element.wrapsBlocks -> descend(element, into, anchors)

            else -> runsOf(element, into.size, anchors).takeIf { it.isNotEmpty() }
                ?.let { into += TextBlock(BlockKind.PARAGRAPH, it) }
        }
    }

    /** Whether the element holds a block-level child, so the walk must descend into it. */
    private val Element.wrapsBlocks: Boolean
        get() = children().any { child -> child.normalName() in BLOCKS || child.normalName() in CONTAINERS }

    private fun runsOf(element: Element, blockIndex: Int, anchors: MutableMap<String, Int>): List<TextRun> {
        val characters = mutableListOf<StyledChar>()
        collectInline(element, emptySet(), characters, blockIndex, anchors)
        return toRuns(characters)
    }

    @Suppress("LongParameterList") // The walk's accumulator and anchor map travel together.
    private fun collectInline(
        element: Element,
        styles: Set<InlineStyle>,
        into: MutableList<StyledChar>,
        blockIndex: Int,
        anchors: MutableMap<String, Int>,
    ) {
        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> node.wholeText.forEach { character -> into += StyledChar(character, styles) }
                is Element -> collectElement(node, styles, into, blockIndex, anchors)
                else -> Unit
            }
        }
    }

    @Suppress("LongParameterList") // The walk's accumulator and anchor map travel together.
    private fun collectElement(
        element: Element,
        styles: Set<InlineStyle>,
        into: MutableList<StyledChar>,
        blockIndex: Int,
        anchors: MutableMap<String, Int>,
    ) {
        element.recordAnchors(blockIndex, anchors)
        when (val tag = element.normalName()) {
            in CONTAINERS, in BLOCKS, in SKIPPED -> Unit
            "br" -> into += StyledChar(' ', styles)
            else -> collectInline(element, styles + styleOf(tag), into, blockIndex, anchors)
        }
    }

    /**
     * Records the fragment names [element] carries as pointing at [blockIndex].
     * A named element inside a block belongs to that block; one that starts a
     * block belongs to the block it starts.
     */
    private fun Element.recordAnchors(blockIndex: Int, anchors: MutableMap<String, Int>) {
        id().takeIf { id -> id.isNotEmpty() }?.let { id -> anchors.putIfAbsent(id, blockIndex) }
        attr("name").takeIf { name -> normalName() == "a" && name.isNotEmpty() }
            ?.let { name -> anchors.putIfAbsent(name, blockIndex) }
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
