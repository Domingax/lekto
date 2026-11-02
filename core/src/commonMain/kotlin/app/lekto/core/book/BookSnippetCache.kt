package app.lekto.core.book

import app.lekto.core.vault.DerivedAssetStore

/**
 * The device-local store of a book's **Resume snippet** (issue #258): the first
 * paragraph at the saved **Reading position**, keyed by book id, beside
 * [BookTextCache] and under the same **Derived asset** rule (CONTEXT.md,
 * ADR-0005).
 *
 * Every device derives the same snippet from the same book and offset, so it
 * never enters the **Vault** and never syncs. Keeping the path here means the
 * library only asks for "the snippet for this book", and a delete cannot forget
 * it while removing the text.
 */
internal class BookSnippetCache(private val derived: DerivedAssetStore) {

    /** The stored snippet for [bookId], or `null` when there is none. */
    fun snippetOf(bookId: String): String? = derived.get(pathOf(bookId))?.decodeToString()

    /** Stores [snippet] as [bookId]'s resume line. */
    fun put(bookId: String, snippet: String) {
        derived.put(pathOf(bookId), snippet.encodeToByteArray())
    }

    /** Removes [bookId]'s snippet. */
    fun remove(bookId: String) {
        derived.remove(pathOf(bookId))
    }

    private fun pathOf(bookId: String): String = "book-snippet/$bookId.txt"
}
