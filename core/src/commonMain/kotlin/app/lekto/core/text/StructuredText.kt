package app.lekto.core.text

/** Inline formatting carried from the source document, so the reader can render it. */
enum class InlineStyle {
    /** `<em>` / `<i>` — emphasis. */
    EMPHASIS,

    /** `<strong>` / `<b>` — strong emphasis. */
    STRONG,

    /** `<code>` / `<kbd>` / `<samp>` — code. */
    CODE,
}

/** A run of text that shares a single set of [InlineStyle]s. */
data class TextRun(val text: String, val styles: Set<InlineStyle> = emptySet())

/** The kind of block a [TextBlock] is, so the reader can style and paginate it. */
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
data class TextBlock(val kind: BlockKind, val runs: List<TextRun>, val headingLevel: Int = 0) {
    /** The block's runs concatenated, whitespace already normalised by the parser. */
    val text: String get() = runs.joinToString(separator = "") { run -> run.text }
}

/**
 * A book parsed into the [TextBlock]s the reader renders, with the metadata the
 * library needs. It is the format-agnostic output every `BookTextParser` shares,
 * so EPUB, TXT and (later) PDF land in the same model.
 */
data class StructuredText(val title: String?, val language: String?, val blocks: List<TextBlock>) {
    /** The blocks as plain text, one per line: the shape the golden test pins. */
    fun plainText(): String = blocks.joinToString(separator = "\n") { block -> block.text }
}
