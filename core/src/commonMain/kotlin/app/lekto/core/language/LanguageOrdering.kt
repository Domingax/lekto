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
    val counts = books.mapNotNull(Book::language)
        .map(::baseLanguage)
        .filter { language -> language.isNotBlank() }
        .groupingBy { language -> language }
        .eachCount()
    return languages.sortedWith(
        compareByDescending<OfferedLanguage> { offered -> counts[baseLanguage(offered.language)] ?: 0 }
            .thenBy { offered -> offered.displayName },
    )
}
