package app.lekto.testkit

import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.TextBlock

/**
 * The character offset where [blockIndex]'s block starts in the text rendered
 * from [blocks]: every earlier block plus the separator between two blocks, the
 * same way the reader counts a page's offset (ADR-0037).
 *
 * It is a fixture rather than a helper in one test file, because the reader's
 * chapter tests run on both lanes and must measure the same text the same way
 * (docs/testing.md, "Naming and placement").
 */
fun blockStartOffset(blocks: List<TextBlock>, blockIndex: Int): Int =
    blocks.take(blockIndex).sumOf { block -> block.text.length + BLOCK_SEPARATOR.length }
