package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.IMAGE_PLACEHOLDER
import app.lekto.core.text.ImageRef
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.ByteArrayInputStream

/**
 * One content document: the blocks it contributes to the book, the fragment
 * anchors it exposes (ADR-0037), named id or legacy `<a name>` to block index,
 * and the images it references, keyed by their path in the archive.
 */
internal data class XhtmlDocument(
    val blocks: List<TextBlock>,
    val anchors: Map<String, Int>,
    val images: Map<String, ByteArray> = emptyMap(),
)

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
 * Three pieces of structure the walk keeps rather than flattens: a `<br>` is an
 * `\n` inside its block (the author's own line break, not a new paragraph), a
 * `ul`/`ol` is walked as a list so every item keeps its marker, its rank and its
 * depth, and an `<img>` becomes a [BlockKind.IMAGE] block of its own.
 */
@Suppress("TooManyFunctions") // The walk, its inline pass and its helpers are one parser.
internal object XhtmlBlocks {

    /** Parses [bytes] into blocks, in document order. */
    fun of(bytes: ByteArray): List<TextBlock> = parse(bytes).blocks

    /**
     * Parses [bytes] into blocks, the fragment anchors the document exposes
     * (ADR-0037) and the images it references.
     *
     * [base] is the document's directory in the archive, which an image's `src`
     * resolves against; [readAsset] returns an archive entry's bytes, so this
     * walk never needs the archive itself and a test can hand it a map. An image
     * it cannot read — a remote `src`, a missing entry, a header it does not
     * know — degrades to its `alt`, never to a failure.
     */
    fun parse(bytes: ByteArray, base: String = "", readAsset: (String) -> ByteArray? = { null }): XhtmlDocument {
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        return Walk(base, readAsset).read(document.body())
    }

    /** One document's walk: the blocks, anchors and images it accumulates. */
    private class Walk(private val base: String, private val readAsset: (String) -> ByteArray?) {

        private val blocks = mutableListOf<TextBlock>()
        private val anchors = mutableMapOf<String, Int>()
        private val images = mutableMapOf<String, ByteArray>()

        fun read(body: Element): XhtmlDocument {
            descend(body)
            return XhtmlDocument(blocks, anchors, images)
        }

        /** Reads every child of [container] in order, keeping their place in the reading order. */
        private fun descend(container: Element) {
            for (child in container.children()) collectBlock(child, list = null)
        }

        @Suppress("CyclomaticComplexMethod") // The walk's dispatch: one branch per element class.
        private fun collectBlock(element: Element, list: ListContext?) {
            element.recordAnchors(blocks.size)
            val tag = element.normalName()
            when {
                tag in SKIPPED -> Unit

                tag == IMAGE_TAG -> collectImage(element)

                // A list is walked as a whole, so its items keep their marker, their
                // rank and their depth; a bare `<li>` (invalid markup) still gets one.
                tag in LIST_TAGS -> collectList(element, depth = (list?.depth ?: -1) + 1)

                tag == "li" -> collectItem(element, list ?: ListContext(ordered = false, depth = 0, ordinal = 1))

                tag in CONTAINERS -> descend(element)

                // A paragraph, a heading or a cell that holds a picture is split
                // around it: the image takes a block of its own, which is how a
                // zone of the page is reserved for it (#263).
                tag in BLOCKS -> if (element.holdsImage) collectMixed(element, tag) else collectLeaf(tag, element)

                else -> collectLoose(element)
            }
        }

        /** A block element with text of its own: one [TextBlock], or none when it holds no words. */
        private fun collectLeaf(tag: String, element: Element) {
            runsOf(element, blocks.size)
                .takeIf { runs -> runs.isNotEmpty() }
                ?.let { runs -> blocks += block(tag, runs) }
        }

        /**
         * A block element that holds an image: its text before the picture becomes
         * a block, the picture becomes one, and the text after it another.
         */
        private fun collectMixed(element: Element, tag: String) {
            val characters = mutableListOf<StyledChar>()

            fun flush() {
                val runs = toRuns(characters)
                characters.clear()
                if (runs.isNotEmpty()) blocks += block(tag, runs)
            }

            for (node in element.childNodes()) {
                when (node) {
                    is TextNode -> node.wholeText.forEach { character ->
                        characters += StyledChar(character, emptySet())
                    }

                    is Element -> if (node.normalName() == IMAGE_TAG) {
                        flush()
                        collectImage(node)
                    } else {
                        collectElement(node, emptySet(), characters, blocks.size, flattenBlocks = false)
                    }

                    else -> Unit
                }
            }
            flush()
        }

        /**
         * An element the format does not classify: usually an inline wrapper whose
         * text belongs in a paragraph. But a heading that writes its anchor
         * self-closing (`<a id="toc"/>Title`) leaves the HTML parser with an `<a>` it
         * never closes, and the rest of the chapter lands inside it — so descend when
         * the element holds blocks, or that chapter would be lost.
         */
        private fun collectLoose(element: Element) {
            if (element.wrapsBlocks) {
                descend(element)
            } else {
                runsOf(element, blocks.size)
                    .takeIf { runs -> runs.isNotEmpty() }
                    ?.let { runs -> blocks += TextBlock(BlockKind.PARAGRAPH, runs) }
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
        private fun collectList(list: Element, depth: Int) {
            val ordered = list.normalName() == "ol"
            var ordinal = list.attr("start").toIntOrNull() ?: 1
            for (child in list.children()) {
                if (child.normalName() != "li") {
                    collectBlock(child, ListContext(ordered, depth, ordinal))
                    continue
                }
                val marker = child.attr("value").toIntOrNull() ?: ordinal
                collectItem(child, ListContext(ordered, depth, marker))
                ordinal = marker + 1
            }
        }

        /**
         * One list item: its marker, then its own text, then any list nested inside it
         * one level deeper. The item's text flattens the block elements it wraps
         * (`<li><p>a</p><p>b</p></li>`), because the nested-block guard would
         * otherwise drop every word of such an item — a shape real EPUBs use often.
         */
        private fun collectItem(item: Element, list: ListContext) {
            item.recordAnchors(blocks.size)
            val marker = markerOf(list)
            val runs = runsOf(item, blocks.size, flattenBlocks = true)
            if (runs.isNotEmpty()) {
                // Real conversions often type the mark into the item itself
                // (`<li>• You pay a royalty fee…`); ours would double it.
                val keepsOwnMarker = runs.startsWithOwnMarker(list)
                blocks += TextBlock(
                    kind = BlockKind.LIST_ITEM,
                    runs = if (keepsOwnMarker) runs else listOf(TextRun(marker)) + runs,
                    listMarker = if (keepsOwnMarker) "" else marker,
                    listDepth = list.depth,
                )
            }
            item.children()
                .filter { child -> child.normalName() in LIST_TAGS }
                .forEach { child -> collectList(child, depth = list.depth + 1) }
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

        /**
         * Turns an `<img>` into a [BlockKind.IMAGE] block, or into a paragraph of its
         * `alt` when the picture cannot be read: a remote source, an entry the
         * archive does not carry, or a format whose header this build cannot size.
         *
         * Nothing here is allowed to fail the book: a broken picture costs the
         * picture, never the chapter.
         */
        private fun collectImage(element: Element) {
            val alt = element.attr("alt").trim()
            val image = imageOf(element.attr("src"))
            when {
                image != null -> {
                    images[image.path] = image.bytes
                    blocks += TextBlock(
                        kind = BlockKind.IMAGE,
                        runs = listOf(TextRun(IMAGE_PLACEHOLDER)),
                        image = ImageRef(name = imageAssetName(image.path), ratio = image.size.ratio, alt = alt),
                    )
                }

                alt.isNotEmpty() -> blocks += TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(alt)))
            }
        }

        /** The bytes and size of the image [src] names, or `null` when it is not one this build can show. */
        private fun imageOf(src: String): LoadedImage? {
            val local = src.isNotEmpty() &&
                !src.startsWith("//") &&
                REMOTE_PREFIXES.none { prefix -> src.startsWith(prefix) }
            val path = if (local) EpubArchive.resolve(base, src) else ""
            val bytes = path.takeIf { it.isNotEmpty() }?.let(readAsset)
            val size = bytes?.let(ImageHeader::sizeOf)
            return if (path.isEmpty() || bytes == null || size == null) {
                null
            } else {
                LoadedImage(path, bytes, size)
            }
        }

        private fun runsOf(element: Element, blockIndex: Int, flattenBlocks: Boolean = false): List<TextRun> {
            val characters = mutableListOf<StyledChar>()
            collectInline(element, emptySet(), characters, blockIndex, flattenBlocks)
            return toRuns(characters)
        }

        @Suppress("LongParameterList") // The walk's accumulator and anchor map travel together.
        private fun collectInline(
            element: Element,
            styles: Set<InlineStyle>,
            into: MutableList<StyledChar>,
            blockIndex: Int,
            flattenBlocks: Boolean,
        ) {
            for (node in element.childNodes()) {
                when (node) {
                    is TextNode -> node.wholeText.forEach { character -> into += StyledChar(character, styles) }
                    is Element -> collectElement(node, styles, into, blockIndex, flattenBlocks)
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
            flattenBlocks: Boolean,
        ) {
            element.recordAnchors(blockIndex)
            when (val tag = element.normalName()) {
                in SKIPPED, in LIST_TAGS, IMAGE_TAG -> Unit
                "br" -> into += StyledChar(LINE_BREAK, styles, hardBreak = true)
                in CONTAINERS, in BLOCKS -> if (flattenBlocks) flattenInto(element, styles, into, blockIndex)
                else -> collectInline(element, styles + styleOf(tag), into, blockIndex, flattenBlocks)
            }
        }

        /**
         * Appends a block element's text into its container's runs, kept apart by a
         * line break: the shape a list item takes when it wraps a paragraph.
         */
        private fun flattenInto(
            element: Element,
            styles: Set<InlineStyle>,
            into: MutableList<StyledChar>,
            blockIndex: Int,
        ) {
            into += StyledChar(LINE_BREAK, styles, hardBreak = true)
            collectInline(element, styles, into, blockIndex, flattenBlocks = true)
            into += StyledChar(LINE_BREAK, styles, hardBreak = true)
        }

        /**
         * Records the fragment names [element] carries as pointing at [blockIndex].
         * A named element inside a block belongs to that block; one that starts a
         * block belongs to the block it starts.
         */
        private fun Element.recordAnchors(blockIndex: Int) {
            id().takeIf { id -> id.isNotEmpty() }?.let { id -> anchors.putIfAbsent(id, blockIndex) }
            attr("name").takeIf { name -> normalName() == "a" && name.isNotEmpty() }
                ?.let { name -> anchors.putIfAbsent(name, blockIndex) }
        }
    }

    /** An image the walk resolved: its archive path, its bytes and the size its header declares. */
    private class LoadedImage(val path: String, val bytes: ByteArray, val size: ImageSize)

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

    private fun styleOf(tag: String): Set<InlineStyle> = when (tag) {
        "em", "i" -> setOf(InlineStyle.EMPHASIS)
        "strong", "b" -> setOf(InlineStyle.STRONG)
        "code", "kbd", "samp", "tt" -> setOf(InlineStyle.CODE)
        else -> emptySet()
    }

    /** Whether the element holds a block-level child, so the walk must descend into it. */
    private val Element.wrapsBlocks: Boolean
        get() = children().any { child -> child.isBlockTag || child.normalName() in LIST_TAGS }

    /** Whether the element holds an image anywhere below it, so it must be split around one. */
    private val Element.holdsImage: Boolean
        get() = getElementsByTag(IMAGE_TAG).isNotEmpty()

    private val Element.isBlockTag: Boolean
        get() = normalName() in BLOCKS || normalName() in CONTAINERS

    /** Elements descended into so their child blocks keep the reading order. */
    private val CONTAINERS = setOf(
        "body", "div", "section", "article", "blockquote",
        "table", "tbody", "thead", "tfoot", "tr", "nav", "header", "footer", "aside", "figure",
    )

    /** The lists, walked by [Walk.collectList] so each item keeps its marker and depth. */
    private val LIST_TAGS = setOf("ul", "ol")

    /** Leaf blocks: one [TextBlock] each. */
    private val BLOCKS = setOf(
        "p", "li", "dt", "dd", "pre", "figcaption", "td", "th",
        "h1", "h2", "h3", "h4", "h5", "h6",
    )

    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    /** Non-text elements whose content is never part of the book text. */
    private val SKIPPED = setOf("script", "style", "head", "title", "svg", "audio", "video", "iframe")

    private const val IMAGE_TAG = "img"

    /** Source prefixes that name no archive entry, so the image is left to its `alt`. */
    private val REMOTE_PREFIXES = listOf("http:", "https:", "data:")

    /** The mark an unordered list item starts with. */
    private const val BULLET = "•"

    /** The marks a conversion may type into an item itself, which the reader does not repeat. */
    private val BULLETS = setOf("•", "‣", "▪", "·", "∙")

    /** What a `<br>` leaves in a block: a hard line break, never a block boundary. */
    private const val LINE_BREAK = '\n'

    private data class StyledChar(
        val value: Char,
        val styles: Set<InlineStyle>,
        /** Whether the character is a hard break the source asked for, not whitespace it happened to contain. */
        val hardBreak: Boolean = false,
    ) {
        fun isSpace(): Boolean = value.isWhitespace() || Character.isSpaceChar(value)
    }
}

/**
 * The derived-asset name an archive image path maps to: a short hash of the path
 * plus its extension when that extension is a safe segment.
 *
 * The name must be a vault-safe segment, and a book's own path is not: a real
 * EPUB writes `images/Planche 1.png` or an accented name, which the derived store
 * refuses. Hashing also makes the name collision-free without caring what the
 * book called its files (issue #263).
 */
internal fun imageAssetName(path: String): String {
    val extension = path.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { candidate ->
            candidate.length in 1..MAX_EXTENSION && candidate.all { char -> char.isLetterOrDigit() }
        }
        ?: NO_EXTENSION
    return "${fnv1a(path)}.$extension"
}

/** The longest file extension worth carrying into an asset name; anything else becomes [NO_EXTENSION]. */
private const val MAX_EXTENSION = 5

/** The name a picture's extension takes when the book's own is missing or unusable. */
private const val NO_EXTENSION = "img"

/** A stable 32-bit hash of [value], in lowercase hex: the same path always names the same asset. */
private fun fnv1a(value: String): String {
    var hash = FNV_OFFSET_BASIS
    for (character in value) {
        hash = hash xor character.code
        hash *= FNV_PRIME
    }
    return hash.toUInt().toString(HEX_RADIX).padStart(HASH_DIGITS, '0')
}

/** FNV-1a's offset basis, as the signed Int the loop runs on. */
private const val FNV_OFFSET_BASIS = -0x7ee3623b // 0x811C9DC5

/** FNV-1a's 32-bit prime. */
private const val FNV_PRIME = 16777619

/** A 32-bit hash prints as this many hexadecimal digits. */
private const val HASH_DIGITS = 8

/** Hexadecimal, the base an asset name's hash prints in. */
private const val HEX_RADIX = 16
