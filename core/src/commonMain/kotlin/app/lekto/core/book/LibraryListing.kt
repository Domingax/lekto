package app.lekto.core.book

import app.lekto.core.text.normaliseSurface
import kotlin.time.Instant

/**
 * The **Library**'s books, split the way the screen shows them (CONTEXT.md,
 * "Library"; issue #188): the few **Last read** first, then every other book by
 * title.
 *
 * An empty listing is an empty vault. The split is derived from the records
 * every time rather than stored, so a sync that moves a position reorders the
 * list without a second rule.
 */
data class LibraryListing(
    /** The books whose **Last read** is known, most recent first; never more than [LAST_READ_LIMIT]. */
    val lastRead: List<Book> = emptyList(),
    /** Every other book, in title order. */
    val others: List<Book> = emptyList(),
) {
    /**
     * The books whose title matches [query] (issue #173), in the order the screen
     * shows them.
     *
     * A query that is blank once trimmed returns every listed book, the leading
     * group first: the Library's own order. A non-blank query matches a case- and
     * accent-insensitive **substring** of the title — `emile` finds `Émile` — and
     * **flattens** the two groups, because the `Continue reading` split is for
     * browsing, not for finding. The title is the only field matched: the file
     * name and the language are not on the row, so a book found by one would
     * surprise the reader.
     */
    fun matching(query: String): List<Book> {
        val needle = folded(query)
        if (needle.isEmpty()) return lastRead + others
        return (lastRead + others)
            .filter { book -> folded(book.title).contains(needle) }
            .inTitleOrder()
    }

    companion object {

        /** How many of the most recently read books lead the Library (CONTEXT.md, "Library"). */
        const val LAST_READ_LIMIT: Int = 3

        /**
         * Splits [books] into the recently read and the rest.
         *
         * A book is recently read when [lastReadByBook] carries its id. Both
         * groups are ordered by title, ignoring case, so the result does not
         * depend on the order [books] arrives in; the last-read group is then
         * ordered by [lastReadByBook], most recent first. Equal last-read
         * instants keep the title order, including the tie at [LAST_READ_LIMIT].
         */
        fun of(books: List<Book>, lastReadByBook: Map<String, Instant>): LibraryListing {
            val byTitle = books.inTitleOrder()
            val lastRead = byTitle
                .filter { book -> lastReadByBook.containsKey(book.id) }
                .sortedWith(
                    compareByDescending<Book> { book -> lastReadByBook.getValue(book.id) }
                        .thenBy { book -> book.title.lowercase() },
                )
                .take(LAST_READ_LIMIT)
            val lastReadIds = lastRead.mapTo(mutableSetOf()) { book -> book.id }
            return LibraryListing(lastRead = lastRead, others = byTitle.filterNot { book -> book.id in lastReadIds })
        }
    }
}

/** The books in title order, ignoring case: the Library's order for everything but the last read. */
internal fun List<Book>.inTitleOrder(): List<Book> = sortedBy { book -> book.title.lowercase() }

/**
 * The Latin letters an accented or ligated character folds to, so a reader who
 * types `emile` finds `Émile`. Casing is folded by [folded] first, so only the
 * lower-case forms are named here.
 */
private val FOLDED_LATIN: Map<Char, String> = mapOf(
    'à' to "a", 'á' to "a", 'â' to "a", 'ã' to "a", 'ä' to "a", 'å' to "a",
    'ç' to "c",
    'è' to "e", 'é' to "e", 'ê' to "e", 'ë' to "e",
    'ì' to "i", 'í' to "i", 'î' to "i", 'ï' to "i",
    'ñ' to "n",
    'ò' to "o", 'ó' to "o", 'ô' to "o", 'õ' to "o", 'ö' to "o",
    'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u",
    'ý' to "y", 'ÿ' to "y",
    'æ' to "ae", 'œ' to "oe", 'ß' to "ss",
)

/**
 * [text] folded for search: its canonical form, lower case (both from
 * [normaliseSurface], the seam word identity already uses), with diacritics
 * dropped.
 */
private fun folded(text: String): String = buildString(text.length) {
    normaliseSurface(text.trim()).forEach { char -> append(FOLDED_LATIN[char] ?: char) }
}
