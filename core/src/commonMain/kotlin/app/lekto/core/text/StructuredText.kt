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

    /** A block that is one image, from a `<figure>`, a `<div>` or a paragraph of its own. */
    IMAGE,
}

/**
 * An image a [TextBlock] shows: the **Derived asset** name the reader loads (the
 * bytes live in the derived store, never in the **Vault**), the picture's
 * width÷height ratio — so a placeholder reserves the right rectangle before any
 * byte is decoded — and the source's `alt`, which is both the description a
 * screen reader reads and the text shown when the image cannot be drawn.
 */
@Serializable
data class ImageRef(val name: String, val ratio: Float, val alt: String = "")

/**
 * A block of inline [runs]: the unit a reader lays out and paginates. A block
 * never contains another block — nesting is flattened when the source is parsed
 * — so [text] is the block's whole readable content.
 *
 * A hard line break inside the source (`<br>`) is an `\n` in a run, not a block
 * of its own: the blank line between blocks stays the paragraph separator, so a
 * line break never doubles the spacing and never repeats a list marker.
 *
 * A [BlockKind.LIST_ITEM] carries its [listMarker] as the text of its first run,
 * so [text] is exactly what the reader draws and counts — **Reading progress**
 * and chapter offsets read the same characters. [listDepth] is the item's level in
 * a nested list, `0` at the top.
 */
@Serializable
data class TextBlock(
    val kind: BlockKind,
    val runs: List<TextRun>,
    val headingLevel: Int = 0,
    val listMarker: String = "",
    val listDepth: Int = 0,
    val image: ImageRef? = null,
) {
    /** The block's runs concatenated, whitespace already normalised by the parser. */
    val text: String get() = runs.joinToString(separator = "") { run -> run.text }

    /**
     * The characters of [listMarker] at the block's start, or an empty range on a
     * block that carries none. The reader leaves them out of its word layer, so a
     * bullet or a number is never tappable.
     */
    val markerRange: IntRange get() = 0 until listMarker.length
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
data class StructuredText(
    val title: String?,
    val language: String?,
    val blocks: List<TextBlock>,
    /**
     * The book's **Chapter list**: the chapters its original file declares, or the
     * ones inferred from its text when it declares none (ADR-0037, ADR-0041). It
     * is a derived asset beside the blocks. An empty list means the source
     * declared no structure and the parser has not learned to infer one.
     */
    val chapters: List<Chapter> = emptyList(),
) {
    /**
     * The blocks as plain text, one per line: the shape the golden test pins. An
     * image block reads `[image]` (or `[image: alt]`), because its placeholder
     * character is invisible in a diff — the golden is a text artefact, and a
     * change there must be readable.
     */
    fun plainText(): String = blocks.joinToString(separator = "\n") { block ->
        val image = block.image
        when {
            image == null -> block.text
            image.alt.isEmpty() -> "[image]"
            else -> "[image: ${image.alt}]"
        }
    }

    /**
     * The length of the text the reader renders: the blocks joined by a blank
     * line (issue #196). It is the denominator of **Reading progress**, so the
     * formula lives beside the blocks and the reader's own chapter text agrees
     * with it (`buildChapterText`).
     *
     * An empty book renders no text, so its length is 0 rather than a negative
     * separator count.
     */
    fun renderedLength(): Int = blocks.renderedLength()
}

/** The rendered length of [this] run of blocks: their text and the blank lines between them. */
fun List<TextBlock>.renderedLength(): Int =
    sumOf { block -> block.text.length } + BLOCK_SEPARATOR.length * (size - 1).coerceAtLeast(0)

/**
 * The text between two rendered blocks: a blank line. The reader lays the blocks
 * out with it (`buildChapterText`), and [StructuredText.renderedLength] counts it,
 * so the denominator of **Reading progress** cannot drift from the text on screen
 * (issue #196).
 */
const val BLOCK_SEPARATOR: String = "\n\n"

/**
 * The single character an image block occupies in the text the reader lays out.
 *
 * An image is drawn in place of one character — Compose's inline placeholder —
 * so the block still has a length: **Reading progress**, chapter offsets and the
 * page ranges count it like any other character, and no formula has to special-
 * case a picture (issue #263).
 */
const val IMAGE_PLACEHOLDER: String = "\uFFFC"
