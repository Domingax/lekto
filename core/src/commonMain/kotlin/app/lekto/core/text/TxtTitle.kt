package app.lekto.core.text

/**
 * The title a plain text does not declare (ADR-0041): the first non-empty line,
 * taken as the book's name when it reads like one.
 *
 * A first line reads like a title when it is short and does not end like a
 * sentence; a line that opens a chapter is never one, because that is structure
 * rather than a name. Anything else is left `null`, so the library falls back to
 * the file name instead of inventing a title the reader then has to repair.
 */
internal object TxtTitle {

    /** The title [paragraph] opens with, or `null` when its first line is prose. */
    fun of(paragraph: String?): String? = paragraph
        ?.substringBefore('\n')
        ?.collapsed()
        ?.takeIf { line -> line.isNotEmpty() && readsLikeTitle(line) }

    private fun readsLikeTitle(line: String): Boolean = line.length <= TITLE_MAX_LENGTH &&
        line.last() !in TERMINAL_PUNCTUATION &&
        !TxtChapters.looksLikeHeading(line)

    /** Longer than this, the first line is prose. */
    private const val TITLE_MAX_LENGTH = 60

    /** A title does not end like a sentence. */
    private const val TERMINAL_PUNCTUATION = ".,;:!?…。！？"
}
