package app.lekto.core.text

/**
 * The TXT [BookTextParser]: a plain-text file becomes paragraphs the reader
 * renders, with the structure the text carries inferred (CONTEXT.md, "Book";
 * issue #15, ADR-0041).
 *
 * A blank line separates paragraphs; a single newline inside one is a soft wrap
 * and becomes a space, so a hard-wrapped article reads as flowing prose. Runs of
 * whitespace collapse to one space, matching how the EPUB parser normalises its
 * text, and decoding is UTF-8.
 *
 * A TXT declares no language, so it is `null` until **Import** asks the reader
 * for one (ADR-0041). Its title and its chapter list are inferred from the text:
 * [TxtChapters] reads a heading off a paragraph that opens with a chapter
 * numbering, and [TxtTitle] reads the first line as the book's name when it
 * looks like one. A file with no recognisable heading is one chapter covering the
 * whole text, exactly as before.
 */
class TxtParser : BookTextParser {

    override fun parse(bytes: ByteArray): ParsedBook {
        val raw = rawParagraphs(bytes)
        val paragraphs = raw.map { paragraph -> paragraph.collapsed() }
        val headings = TxtChapters.headings(paragraphs)
        val headingBlocks = headings.mapTo(mutableSetOf()) { heading -> heading.blockIndex }
        return ParsedBook(
            StructuredText(
                title = TxtTitle.of(raw.firstOrNull()),
                language = null,
                blocks = paragraphs.mapIndexed { index, paragraph ->
                    blockOf(paragraph, isHeading = index in headingBlocks)
                },
                chapters = TxtChapters.chaptersOf(headings),
            ),
        )
    }

    private fun blockOf(text: String, isHeading: Boolean): TextBlock = if (isHeading) {
        TextBlock(BlockKind.HEADING, listOf(TextRun(text)), headingLevel = HEADING_LEVEL)
    } else {
        TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(text)))
    }

    /** The file's paragraphs before normalisation, so the title keeps its first line. */
    private fun rawParagraphs(bytes: ByteArray): List<String> = bytes
        .decodeToString()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(PARAGRAPH_BREAK)
        .map { paragraph -> paragraph.trim() }
        .filter { paragraph -> paragraph.isNotEmpty() }

    private companion object {
        /** A blank line: two line breaks with only whitespace between. */
        private val PARAGRAPH_BREAK = Regex("\n\\s*\n")

        /** A chapter heading is a first-level heading. */
        private const val HEADING_LEVEL = 1
    }
}

/** The paragraph's text with its line breaks and runs of whitespace collapsed. */
internal fun String.collapsed(): String = replace(WHITESPACE_RUN, " ").trim()

private val WHITESPACE_RUN = Regex("""\s+""")
