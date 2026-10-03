package app.lekto.core.text

/**
 * The TXT [BookTextParser]: a plain-text file becomes paragraphs the reader
 * renders (CONTEXT.md, "Book"; issue #15).
 *
 * A blank line separates paragraphs; a single newline inside one is a soft wrap
 * and becomes a space, so a hard-wrapped article reads as flowing prose. Runs of
 * whitespace collapse to one space, matching how the EPUB parser normalises its
 * text, and decoding is UTF-8. A TXT file carries no title or language, so both
 * are `null` and the library fills the title from the file name.
 */
class TxtParser : BookTextParser {

    override fun parse(bytes: ByteArray): StructuredText {
        val text = bytes.decodeToString().replace("\r\n", "\n").replace('\r', '\n')
        val blocks = text.split(PARAGRAPH_BREAK)
            .map { paragraph -> paragraph.replace(WHITESPACE, " ").trim() }
            .filter { paragraph -> paragraph.isNotEmpty() }
            .map { paragraph -> TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(paragraph))) }
        return StructuredText(title = null, language = null, blocks = blocks)
    }

    private companion object {
        /** A blank line: two line breaks with only whitespace between. */
        private val PARAGRAPH_BREAK = Regex("\n\\s*\n")

        /** A run of any whitespace, collapsed to a single space. */
        private val WHITESPACE = Regex("\\s+")
    }
}
