package app.lekto.core.text

/**
 * The parser seam: it turns a book's original bytes into the [StructuredText]
 * the reader renders. EPUB implements it first; TXT and PDF follow behind the
 * same seam so the reader never learns the source format (ADR-0007).
 *
 * A format that carries binaries beside its text — an EPUB's images — returns
 * them as [ParsedBook.assets], because the bytes must reach the **Derived
 * asset** store (`CONTEXT.md`, ADR-0005) and a parser only ever sees the
 * original. The parser names them; the library writes them.
 */
fun interface BookTextParser {
    /** Parses [bytes] in the format this parser owns. */
    fun parse(bytes: ByteArray): ParsedBook
}

/**
 * What a parser produced: the [text] and the binary [assets] it extracted from
 * the original. A format with no binaries returns none, which is the common case
 * for a TXT.
 */
data class ParsedBook(val text: StructuredText, val assets: List<BookAsset> = emptyList())

/**
 * One binary a parser pulled out of the original: the [name] the parsed text
 * refers to, and its [bytes]. The name is the parser's to choose and must be a
 * safe derived-asset segment — lowercase hex and a short extension, never the
 * path the book happens to carry.
 */
class BookAsset(val name: String, val bytes: ByteArray)
