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
 * Headings, list items and pictures are skipped: a heading alone is not a
 * reminder of where the reader was, and a bullet is not prose. A blank
 * paragraph is skipped too, so an empty line never becomes the snippet. An
 * offset past the last paragraph names nothing rather than the last one seen.
 */
fun List<TextBlock>.paragraphAt(offset: Int): TextBlock? {
    var start = 0
    for (block in this) {
        val end = start + block.text.length
        if (block.kind == BlockKind.PARAGRAPH && block.text.isNotBlank() && end >= offset) return block
        start = end + BLOCK_SEPARATOR.length
    }
    return null
}
