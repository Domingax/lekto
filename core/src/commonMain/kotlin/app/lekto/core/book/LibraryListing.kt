package app.lekto.core.book

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
