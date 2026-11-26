package app.lekto.core.book

import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock

/**
 * The first **Paragraph** at or after [offset], or `null` when the text holds
 * none from there on (issue #258).
 *
 * It is what the **Resume snippet** names after a position save: the reader
 * lands on a page, and this finds the words at or below it. Ranges are the
 * rendered text's — blocks joined by [BLOCK_SEPARATOR], exactly as pagination
 * counts them — so the offset the reader saved and the block chosen here agree.
 *
 * A quote's paragraph and a term-and-definition pair are prose, so they are named
 * like a paragraph (issue #265). Headings, list items, pictures, preformatted
 * text and scene breaks are skipped: a heading alone is not a reminder of where
 * the reader was, and a bullet is not prose. A blank paragraph is skipped too, so
 * an empty line never becomes the snippet. An offset past the last paragraph
 * names nothing rather than the last one seen.
 */
fun List<TextBlock>.paragraphAt(offset: Int): TextBlock? {
    var start = 0
    for (block in this) {
        val end = start + block.text.length
        if (block.isProse() && block.text.isNotBlank() && end >= offset) return block
        start = end + BLOCK_SEPARATOR.length
    }
    return null
}

/** Whether a block's words can stand as the **Resume snippet**. */
private fun TextBlock.isProse(): Boolean =
    kind == BlockKind.PARAGRAPH || kind == BlockKind.QUOTE || kind == BlockKind.DEFINITION
