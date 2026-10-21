package app.lekto.core.text

import kotlinx.serialization.Serializable

/** Inline formatting carried from the source document, so the reader can render it. */
@Serializable
enum class InlineStyle {
    /** `<em>` / `<i>` — emphasis. */
    EMPHASIS,

    /** `<strong>` / `<b>` — strong emphasis. */
    STRONG,

    /** `<code>` / `<kbd>` / `<samp>` — code. */
    CODE,
}

/** A run of text that shares a single set of [InlineStyle]s. */
@Serializable
data class TextRun(val text: String, val styles: Set<InlineStyle> = emptySet())

/** The kind of block a [TextBlock] is, so the reader can style and paginate it. */
@Serializable
enum class BlockKind {
    PARAGRAPH,
    HEADING,
    LIST_ITEM,
}

/**
 * A block of inline [runs]: the unit a reader lays out and paginates. A block
 * never contains another block — nesting is flattened when the source is parsed
 * — so [text] is the block's whole readable content.
 */
@Serializable
data class TextBlock(val kind: BlockKind, val runs: List<TextRun>, val headingLevel: Int = 0) {
    /** The block's runs concatenated, whitespace already normalised by the parser. */
    val text: String get() = runs.joinToString(separator = "") { run -> run.text }
}

/**
 * A book parsed into the [TextBlock]s the reader renders, with the metadata the
 * library needs. It is the format-agnostic output every `BookTextParser` shares,
 * so EPUB, TXT and (later) PDF land in the same model.
 *
 * It is serialisable because it is what a book's derived parsed-text cache holds
 * (ADR-0005): re-opened from the cache rather than re-parsed.
 */
@Serializable
data class StructuredText(val title: String?, val language: String?, val blocks: List<TextBlock>) {
    /** The blocks as plain text, one per line: the shape the golden test pins. */
    fun plainText(): String = blocks.joinToString(separator = "\n") { block -> block.text }

    /**
     * The length of the text the reader renders: the blocks joined by a blank
     * line (issue #196). It is the denominator of **Reading progress**, so the
     * formula lives beside the blocks and the reader's own chapter text agrees
     * with it (`buildChapterText`).
     *
     * An empty book renders no text, so its length is 0 rather than a negative
     * separator count.
     */
    fun renderedLength(): Int =
        blocks.sumOf { block -> block.text.length } + BLOCK_SEPARATOR.length * (blocks.size - 1).coerceAtLeast(0)
}

/**
 * The text between two rendered blocks: a blank line. The reader lays the blocks
 * out with it (`buildChapterText`), and [StructuredText.renderedLength] counts it,
 * so the denominator of **Reading progress** cannot drift from the text on screen
 * (issue #196).
 */
const val BLOCK_SEPARATOR: String = "\n\n"
