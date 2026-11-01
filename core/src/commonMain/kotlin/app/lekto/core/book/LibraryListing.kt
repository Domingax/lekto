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
    /**
     * Each book's **Reading progress** as its row shows it, keyed by book id
     * (issue #196). Only books with a **Reading position** have an entry; one
     * whose rendered length is not known yet maps to [BookProgress.Unmeasured],
     * so the row can show `…` without falling back to `Not started`.
     *
     * It is a map beside the books rather than a field on [Book] because it is
     * derived per device: the position is in the vault, the length is a
     * **Derived asset** (ADR-0005).
     */
    val progressByBook: Map<String, BookProgress> = emptyMap(),

    /**
     * Each book's **Reading state**, keyed by book id (issue #200). A finished
     * book is in the listing — it falls into the title-ordered rest — but never
     * leads the **Library**, because that group is for books the reader can
     * continue (ADR-0038).
     */
    val readingStateByBook: Map<String, ReadingState> = emptyMap(),
) {

    /** What [book]'s row says about its **Reading progress**, defaulting to `Not started`. */
    fun progressOf(book: Book): BookProgress = progressByBook[book.id] ?: BookProgress.NotStarted

    /**
     * [book]'s **Reading state**. A listing built before the states existed, or
     * one whose map omits the book, falls back to the position it already knows:
     * a book with progress is **In progress**, and one without is **Unread**.
     * **Finished** is only ever the declared fact, never guessed.
     */
    fun readingStateOf(book: Book): ReadingState = stateOf(book.id, readingStateByBook, progressByBook)

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
         * A book is recently read when [lastReadByBook] carries its id and its
         * [ReadingState] is not **Finished**: the leading group is for books the
         * reader can continue, so a finished book falls into the title-ordered
         * rest even if it was the last book read (ADR-0038). Both groups are
         * ordered by title, ignoring case, so the result does not depend on the
         * order [books] arrives in; the last-read group is then ordered by
         * [lastReadByBook], most recent first. Equal last-read instants keep the
         * title order, including the tie at [LAST_READ_LIMIT].
         *
         * [progressByBook] carries each book's **Reading progress** (issue #196);
         * an empty map leaves every row at `Not started`, which is what a vault
         * with no positions holds. [readingStateByBook] carries the
         * declaration-derived state; a book missing from it falls back to the
         * progress map ([readingStateOf]).
         */
        fun of(
            books: List<Book>,
            lastReadByBook: Map<String, Instant>,
            progressByBook: Map<String, BookProgress> = emptyMap(),
            readingStateByBook: Map<String, ReadingState> = emptyMap(),
        ): LibraryListing {
            val byTitle = books.inTitleOrder()
            val lastRead = byTitle
                .filter { book ->
                    lastReadByBook.containsKey(book.id) &&
                        stateOf(book.id, readingStateByBook, progressByBook) != ReadingState.FINISHED
                }
                .sortedWith(
                    compareByDescending<Book> { book -> lastReadByBook.getValue(book.id) }
                        .thenBy { book -> book.title.lowercase() },
                )
                .take(LAST_READ_LIMIT)
            val lastReadIds = lastRead.mapTo(mutableSetOf()) { book -> book.id }
            return LibraryListing(
                lastRead = lastRead,
                others = byTitle.filterNot { book -> book.id in lastReadIds },
                progressByBook = progressByBook,
                readingStateByBook = readingStateByBook,
            )
        }
    }
}

/** The books in title order, ignoring case: the Library's order for everything but the last read. */
internal fun List<Book>.inTitleOrder(): List<Book> = sortedBy { book -> book.title.lowercase() }

/**
 * The state a book with [id] is in, from its declared states or, for a listing
 * built without one, from its **Reading position**: progress means **In progress**,
 * and absent progress means **Unread**. **Finished** is only ever declared.
 */
private fun stateOf(id: String, states: Map<String, ReadingState>, progress: Map<String, BookProgress>): ReadingState =
    states[id] ?: if (progress.containsKey(id)) ReadingState.IN_PROGRESS else ReadingState.UNREAD

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
