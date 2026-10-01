package app.lekto.core.text

/**
 * The parser seam: it turns a book's original bytes into the [StructuredText]
 * the reader renders. EPUB implements it first; TXT and PDF follow behind the
 * same seam so the reader never learns the source format (ADR-0007).
 */
fun interface BookTextParser {
    /** Parses [bytes] in the format this parser owns. */
    fun parse(bytes: ByteArray): StructuredText
}
