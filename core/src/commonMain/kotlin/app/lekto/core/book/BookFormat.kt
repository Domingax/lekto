package app.lekto.core.book

import kotlinx.serialization.Serializable

/**
 * The book formats Lekto imports as first-class (CONTEXT.md, "Book"). PDF is
 * deliberately absent: it is the immediate follow-up, not MVP.
 */
@Serializable
enum class BookFormat {
    EPUB,
    TXT,
    ;

    companion object {
        /** The format [fileName] names by extension, or `null` when Lekto cannot import it. */
        fun ofFileName(fileName: String): BookFormat? =
            when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
                "epub" -> EPUB
                "txt", "text" -> TXT
                else -> null
            }
    }
}
