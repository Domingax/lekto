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
 * and containers (`div`, `blockquote`, `table`, …) are descended into so their
 * child blocks keep their place in the reading order.
 *
 * Two pieces of structure the walk keeps rather than flattens: a `<br>` is an
 * `\n` inside its block (the author's own line break, not a new paragraph), and a
 * `ul`/`ol` is walked as a list so every item keeps its marker, its rank and its
 * depth.
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
        for (child in container.children()) collectBlock(child, into, anchors, list = null)
    }

    @Suppress("LongParameterList") // The walk's accumulator, anchor map and list context travel together.
    private fun collectBlock(
        element: Element,
        into: MutableList<TextBlock>,
        anchors: MutableMap<String, Int>,
        list: ListContext?,
    ) {
        element.recordAnchors(into.size, anchors)
        val tag = element.normalName()
        when {
            tag in SKIPPED -> Unit

            // A list is walked as a whole, so its items keep their marker, their
            // rank and their depth; a bare `<li>` (invalid markup) still gets one.
            tag in LIST_TAGS -> collectList(element, into, anchors, depth = (list?.depth ?: -1) + 1)

            tag == "li" -> collectItem(
                element,
                into,
                anchors,
                list ?: ListContext(ordered = false, depth = 0, ordinal = 1),
            )

            tag in CONTAINERS -> descend(element, into, anchors)

            tag in BLOCKS -> collectLeaf(tag, element, into, anchors)

            else -> collectLoose(element, into, anchors)
        }
    }

    /** A block element with text of its own: one [TextBlock], or none when it holds no words. */
    private fun collectLeaf(
        tag: String,
        element: Element,
        into: MutableList<TextBlock>,
        anchors: MutableMap<String, Int>,
    ) {
        runsOf(element, into.size, anchors)
            .takeIf { runs -> runs.isNotEmpty() }
            ?.let { runs -> into += block(tag, runs) }
    }

    /**
     * An element the format does not classify: usually an inline wrapper whose
     * text belongs in a paragraph. But a heading that writes its anchor
     * self-closing (`<a id="toc"/>Title`) leaves the HTML parser with an `<a>` it
     * never closes, and the rest of the chapter lands inside it — so descend when
     * the element holds blocks, or that chapter would be lost.
     */
    private fun collectLoose(element: Element, into: MutableList<TextBlock>, anchors: MutableMap<String, Int>) {
        if (element.wrapsBlocks) {
            descend(element, into, anchors)
        } else {
            runsOf(element, into.size, anchors)
                .takeIf { runs -> runs.isNotEmpty() }
                ?.let { runs -> into += TextBlock(BlockKind.PARAGRAPH, runs) }
        }
    }

    /**
     * Where a list item sits: whether its list is ordered, the level it sits at
     * (0 at the top) and the rank its marker shows. The rank starts at the `ol`'s
     * `start` and is stepped per item, so an `li`'s own `value` can override it.
     */
    private data class ListContext(val ordered: Boolean, val depth: Int, val ordinal: Int)

    /**
     * Walks a `ul`/`ol` and turns each `li` into a [BlockKind.LIST_ITEM]. Siblings
     * the format does not allow in a list are walked as ordinary content rather
     * than dropped.
     */
    @Suppress("LongParameterList") // The walk's accumulator, anchor map and list context travel together.
    private fun collectList(
        list: Element,
        into: MutableList<TextBlock>,
        anchors: MutableMap<String, Int>,
        depth: Int,
    ) {
        val ordered = list.normalName() == "ol"
        var ordinal = list.attr("start").toIntOrNull() ?: 1
        for (child in list.children()) {
            if (child.normalName() != "li") {
                collectBlock(child, into, anchors, ListContext(ordered, depth, ordinal))
                continue
            }
            val marker = child.attr("value").toIntOrNull() ?: ordinal
            collectItem(child, into, anchors, ListContext(ordered, depth, marker))
            ordinal = marker + 1
        }
    }

    /**
     * One list item: its marker, then its own text, then any list nested inside it
     * one level deeper. The item's text flattens the block elements it wraps
     * (`<li><p>a</p><p>b</p></li>`), because the nested-block guard would
     * otherwise drop every word of such an item — a shape real EPUBs use often.
     */
    @Suppress("LongParameterList") // The walk's accumulator, anchor map and list context travel together.
    private fun collectItem(
        item: Element,
        into: MutableList<TextBlock>,
        anchors: MutableMap<String, Int>,
        list: ListContext,
    ) {
        item.recordAnchors(into.size, anchors)
        val marker = markerOf(list)
        val runs = runsOf(item, into.size, anchors, flattenBlocks = true)
        if (runs.isNotEmpty()) {
            // Real conversions often type the mark into the item itself
            // (`<li>• You pay a royalty fee…`); ours would double it.
            val keepsOwnMarker = runs.startsWithOwnMarker(list)
            into += TextBlock(
                kind = BlockKind.LIST_ITEM,
                runs = if (keepsOwnMarker) runs else listOf(TextRun(marker)) + runs,
                listMarker = if (keepsOwnMarker) "" else marker,
                listDepth = list.depth,
            )
        }
        item.children()
            .filter { child -> child.normalName() in LIST_TAGS }
            .forEach { child -> collectList(child, into, anchors, depth = list.depth + 1) }
    }

    /** The marker a list item starts with: a bullet, or its rank and a period. */
    private fun markerOf(list: ListContext): String = if (list.ordered) "${list.ordinal}. " else "$BULLET "

    /** Whether the item writes its own mark, which the reader then leaves alone. */
    private fun List<TextRun>.startsWithOwnMarker(list: ListContext): Boolean {
        val text = first().text
        return if (list.ordered) {
            text.startsWith("${list.ordinal}.") || text.startsWith("${list.ordinal})")
        } else {
            BULLETS.any { bullet -> text.startsWith(bullet) }
        }
    }

    /** Whether the element holds a block-level child, so the walk must descend into it. */
    private val Element.wrapsBlocks: Boolean
        get() = children().any { child ->
            child.normalName() in BLOCKS || child.normalName() in CONTAINERS || child.normalName() in LIST_TAGS
        }

    @Suppress("LongParameterList") // The walk's accumulator and anchor map travel together.
    private fun runsOf(
        element: Element,
        blockIndex: Int,
        anchors: MutableMap<String, Int>,
        flattenBlocks: Boolean = false,
    ): List<TextRun> {
        val characters = mutableListOf<StyledChar>()
        collectInline(element, emptySet(), characters, blockIndex, anchors, flattenBlocks)
        return toRuns(characters)
    }

    @Suppress("LongParameterList") // The walk's accumulator and anchor map travel together.
    private fun collectInline(
        element: Element,
        styles: Set<InlineStyle>,
        into: MutableList<StyledChar>,
        blockIndex: Int,
        anchors: MutableMap<String, Int>,
        flattenBlocks: Boolean,
    ) {
        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> node.wholeText.forEach { character -> into += StyledChar(character, styles) }
                is Element -> collectElement(node, styles, into, blockIndex, anchors, flattenBlocks)
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
        flattenBlocks: Boolean,
    ) {
        element.recordAnchors(blockIndex, anchors)
        when (val tag = element.normalName()) {
            in SKIPPED, in LIST_TAGS -> Unit
            "br" -> into += StyledChar(LINE_BREAK, styles, hardBreak = true)
            in CONTAINERS, in BLOCKS -> if (flattenBlocks) flattenInto(element, styles, into, blockIndex, anchors)
            else -> collectInline(element, styles + styleOf(tag), into, blockIndex, anchors, flattenBlocks)
        }
    }

    /**
     * Appends a block element's text into its container's runs, kept apart by a
     * line break: the shape a list item takes when it wraps a paragraph.
     */
    @Suppress("LongParameterList") // The walk's accumulator and anchor map travel together.
    private fun flattenInto(
        element: Element,
        styles: Set<InlineStyle>,
        into: MutableList<StyledChar>,
        blockIndex: Int,
        anchors: MutableMap<String, Int>,
    ) {
        into += StyledChar(LINE_BREAK, styles, hardBreak = true)
        collectInline(element, styles, into, blockIndex, anchors, flattenBlocks = true)
        into += StyledChar(LINE_BREAK, styles, hardBreak = true)
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
     *
     * A hard line break (`<br>`) survives as an `\n` instead of collapsing: it is
     * the author's own break, and it is what keeps a list or a poem's lines apart.
     * The spaces around it are dropped, and a break at the block's end adds no
     * empty line — the blank line between blocks is already there.
     */
    private fun toRuns(characters: List<StyledChar>): List<TextRun> {
        val runs = mutableListOf<TextRun>()
        var afterSpace = true
        for (character in characters) {
            when {
                character.hardBreak -> {
                    trimTrailingSpace(runs)
                    if (runs.isNotEmpty()) append(runs, LINE_BREAK.toString(), character.styles)
                    afterSpace = true
                }

                character.isSpace() -> {
                    if (!afterSpace) append(runs, " ", character.styles)
                    afterSpace = true
                }

                else -> {
                    append(runs, character.value.toString(), character.styles)
                    afterSpace = false
                }
            }
        }
        return withoutTrailingBreaks(withoutTrailingSpace(runs))
    }

    /** Drops the spaces at the end of the block, so a break does not keep one. */
    private fun trimTrailingSpace(runs: MutableList<TextRun>) {
        val last = runs.lastOrNull() ?: return
        if (!last.text.endsWith(" ")) return
        val text = last.text.trimEnd(' ')
        if (text.isEmpty()) runs.removeAt(runs.lastIndex) else runs[runs.lastIndex] = last.copy(text = text)
    }

    /** Drops the hard line breaks at the end of the block: they add no line of their own. */
    private fun withoutTrailingBreaks(runs: List<TextRun>): List<TextRun> {
        var trimmed = runs
        while (true) {
            val last = trimmed.lastOrNull() ?: return trimmed
            if (!last.text.endsWith(LINE_BREAK)) return trimmed
            val text = last.text.trimEnd(LINE_BREAK)
            trimmed = if (text.isEmpty()) trimmed.dropLast(1) else trimmed.dropLast(1) + last.copy(text = text)
        }
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
        else -> TextBlock(BlockKind.PARAGRAPH, runs)
    }

    /** Elements descended into so their child blocks keep the reading order. */
    private val CONTAINERS = setOf(
        "body", "div", "section", "article", "blockquote",
        "table", "tbody", "thead", "tfoot", "tr", "nav", "header", "footer", "aside", "figure",
    )

    /** The lists, walked by [collectList] so each item keeps its marker and depth. */
    private val LIST_TAGS = setOf("ul", "ol")

    /** The mark an unordered list item starts with. */
    private const val BULLET = "•"

    /** The marks a conversion may type into an item itself, which the reader does not repeat. */
    private val BULLETS = setOf("•", "‣", "▪", "·", "∙")

    /** What a `<br>` leaves in a block: a hard line break, never a block boundary. */
    private const val LINE_BREAK = '\n'

    /** Leaf blocks: one [TextBlock] each. */
    private val BLOCKS = setOf(
        "p", "li", "dt", "dd", "pre", "figcaption", "td", "th",
        "h1", "h2", "h3", "h4", "h5", "h6",
    )

    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    /** Non-text elements whose content is never part of the book text. */
    private val SKIPPED = setOf("script", "style", "head", "title", "img", "svg", "audio", "video", "iframe")

    private data class StyledChar(
        val value: Char,
        val styles: Set<InlineStyle>,
        /** Whether the character is a hard break the source asked for, not whitespace it happened to contain. */
        val hardBreak: Boolean = false,
    ) {
        fun isSpace(): Boolean = value.isWhitespace() || Character.isSpaceChar(value)
    }
}
