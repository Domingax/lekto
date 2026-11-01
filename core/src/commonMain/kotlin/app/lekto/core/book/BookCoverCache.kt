package app.lekto.core.book

import app.lekto.core.vault.DerivedAssetStore

/**
 * The device-local store of a book's cover image (issue #259): the raw bytes the
 * EPUB parser extracted, keyed by book id, beside [BookTextCache] and under the
 * same **Derived asset** rule (CONTEXT.md, ADR-0005).
 *
 * A cover is regenerable from the **Book original**, so it never enters the
 * **Vault** and never syncs (ADR-0016). Keeping the path here means the library
 * only asks for "the cover for this book", and a delete or a re-parse cannot
 * forget one half of the pair.
 */
internal class BookCoverCache(private val derived: DerivedAssetStore) {

    /** Stores [bytes] as [bookId]'s cover. */
    fun put(bookId: String, bytes: ByteArray) {
        derived.put(pathOf(bookId), bytes)
    }

    /** Removes [bookId]'s cover. */
    fun remove(bookId: String) {
        derived.remove(pathOf(bookId))
    }

    private fun pathOf(bookId: String): String = "book-cover/$bookId"
}
