package app.lekto.core.language

import app.lekto.core.book.Book
import app.lekto.core.text.baseLanguage

/**
 * The offered **Reading language**s in the order the **Languages** surface lists
 * them (issue #146): by the number of `Book`s the reader has in each, descending,
 * then alphabetically by [OfferedLanguage.displayName]. A language with no books
 * counts zero, so it sorts after every language that has one, alphabetically
 * among its peers.
 *
 * A book's language is reduced to its base tag, so `en-US` and `en` count
 * together (CONTEXT.md, "Reading language").
 */
fun orderOfferedLanguages(languages: List<OfferedLanguage>, books: List<Book>): List<OfferedLanguage> {
    val counts = languageBookCounts(books)
    return languages.sortedWith(
        compareByDescending<OfferedLanguage> { offered -> counts[baseLanguage(offered.language)] ?: 0 }
            .thenBy { offered -> offered.displayName },
    )
}

/**
 * The **Reading language**s the reader actually has books in, most-read first
 * (issue #214): the Settings index's **Reading languages** value.
 *
 * It is the same order the **Languages** surface applies to the languages it
 * offers — most books first, then the language's name — so the index and the
 * screen it opens never disagree. A book that names no language is left out, and
 * a language's subtag is folded to its base before it is counted. A tie falls
 * back to the English name, not the tag, so `es` reads as Spanish rather than
 * sorting as a string.
 */
fun readingLanguagesOf(books: List<Book>): List<String> {
    val counts = languageBookCounts(books)
    return counts.keys.sortedWith(
        compareByDescending<String> { language -> counts.getValue(language) }
            .thenBy(::readingLanguageName),
    )
}

/**
 * How many books the reader has in each **Reading language**, keyed by the base
 * tag. Both orders above count the same way, so they cannot drift apart.
 */
private fun languageBookCounts(books: List<Book>): Map<String, Int> = books.mapNotNull(Book::language)
    .map(::baseLanguage)
    .filter { language -> language.isNotBlank() }
    .groupingBy { language -> language }
    .eachCount()
