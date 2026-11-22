package app.lekto.core.vocabulary

import app.lekto.core.MasteryLevel

/**
 * What one **Book** gave the reader, counted from the **Vocabulary** (issue #280;
 * ADR-0050): [total] is the entries whose book history names it, and [known] is
 * how many of those are at the **Known** level.
 *
 * The two numbers are the end-of-book state's only claim about the reader's
 * words, and they are read from the entries alone — no record counts a book's
 * words, so deleting a book cannot disturb them and no book id is stored twice.
 * A book read before the history existed names none, which is why [isEmpty] asks
 * the panel to say nothing rather than `0 words`.
 */
data class BookWords(val total: Int, val known: Int) {

    /** Whether this book gave the reader no word at all, so the panel draws no line. */
    val isEmpty: Boolean get() = total == 0

    companion object {

        /** The words [entries] record as saved from [bookId]. */
        fun of(entries: List<VocabularyEntry>, bookId: String): BookWords {
            val fromBook = entries.filter { entry -> bookId in entry.bookIds }
            return BookWords(
                total = fromBook.size,
                known = fromBook.count { entry -> entry.mastery == MasteryLevel.KNOWN },
            )
        }
    }
}
