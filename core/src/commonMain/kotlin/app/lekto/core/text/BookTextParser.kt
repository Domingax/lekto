package app.lekto.core.text

/**
 * The parser seam: it turns a book's original bytes into the [StructuredText]
 * the reader renders. EPUB implements it first; TXT and PDF follow behind the
 * same seam so the reader never learns the source format (ADR-0007).
 *
 * A format that carries binaries beside its text — an EPUB's images and its
 * cover — returns them as [ParsedBook.assets] and [ParsedBook.cover], because
 * the bytes must reach the **Derived asset** store (`CONTEXT.md`, ADR-0005) and
 * a parser only ever sees the original. The parser names the images; the library
 * writes them.
 */
fun interface BookTextParser {
    /** Parses [bytes] in the format this parser owns. */
    fun parse(bytes: ByteArray): ParsedBook
}

/**
 * What a parser produced: the [text], the binary [assets] it extracted from the
 * original, and the book's [cover] image when it has one. A format with no
 * binaries returns none, which is the common case for a TXT.
 *
 * The [cover] is the single image a shelf may show as the book's art: raw bytes,
 * validated by the parser, and a **Derived asset** like the text beside it. It is
 * deliberately not one of [assets], because no block refers to it.
 */
data class ParsedBook(
    val text: StructuredText,
    val assets: List<BookAsset> = emptyList(),
    val cover: ByteArray? = null,
)

/**
 * One binary a parser pulled out of the original: the [name] the parsed text
 * refers to, and its [bytes]. The name is the parser's to choose and must be a
 * safe derived-asset segment — lowercase hex and a short extension, never the
 * path the book happens to carry.
 */
class BookAsset(val name: String, val bytes: ByteArray)
