package app.lekto.core.language

import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Settings index's **Reading languages** value (issue #214): the languages
 * the reader has books in, most-read first, with no empty entry and no subtag
 * counted twice. The tie-break is the language's name — the **Languages**
 * surface's own order — so the index and the screen it opens agree.
 */
class ReadingLanguagesTest {

    @Test
    fun `the languages with the most books lead`() {
        val books = listOf(
            book("a", "fr"),
            book("b", "fr"),
            book("c", "en"),
        )

        assertEquals(listOf("fr", "en"), readingLanguagesOf(books))
    }

    @Test
    fun `a tie falls back to the language name`() {
        // `es` sorts before `fr` as a tag, but Spanish comes after French by name —
        // the order the Languages surface lists them in.
        val books = listOf(book("a", "es"), book("b", "fr"))

        assertEquals(listOf("fr", "es"), readingLanguagesOf(books))
    }

    @Test
    fun `a book that names no language is left out`() {
        val books = listOf(book("a", null), book("b", "en"))

        assertEquals(listOf("en"), readingLanguagesOf(books))
    }

    @Test
    fun `a subtag is folded to its base before it is counted`() {
        val books = listOf(book("a", "en-GB"), book("b", "en-US"), book("c", "fr"))

        assertEquals(listOf("en", "fr"), readingLanguagesOf(books))
    }

    @Test
    fun `no books means no languages`() {
        assertEquals(emptyList(), readingLanguagesOf(emptyList()))
    }

    @Test
    fun `the Languages surface keeps the same count-first order`() {
        val offered = listOf(
            OfferedLanguage("en", "English", difficulty = null, dictionaries = emptyList()),
            OfferedLanguage("fr", "French", difficulty = null, dictionaries = emptyList()),
        )
        val books = listOf(book("a", "fr"), book("b", "en"), book("c", "en"))

        assertEquals(listOf("en", "fr"), orderOfferedLanguages(offered, books).map { it.language })
        assertEquals(listOf("en", "fr"), readingLanguagesOf(books))
    }
}

/** A book with a title and an optional language; the rest of the record the row never reads. */
private fun book(id: String, language: String?): Book = Book(
    id = id,
    title = "Book $id",
    language = language,
    format = BookFormat.EPUB,
    fileName = "$id.epub",
)
