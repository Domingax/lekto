package app.lekto.core.language

import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.text.baseLanguage
import app.lekto.testkit.testOfferedLanguage
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

/** A book read in [language], as the reader's library holds it. */
private fun book(language: String): Book =
    Book(id = "b-$language", title = "A book", language = language, format = BookFormat.TXT, fileName = "b.txt")

/**
 * The **Languages** surface's ordering (issue #146): by the number of `Book`s
 * the reader has in each, descending, then alphabetically by display name, with
 * languages holding no books last.
 */
@OptIn(ExperimentalKotest::class)
class LanguageOrderingPropertyTest :
    FunSpec({

        val codes = arrayOf("en", "fr", "de", "es", "pt", "xx")
        val offered = Arb.set(Arb.element(*codes), 0..4).map { chosen -> chosen.map(::testOfferedLanguage) }
        val books = Arb.list(Arb.element(*codes), 0..12)

        test("ordering keeps exactly the languages the catalogue offered") {
            checkAll(PropTestConfig(seed = 2026147, iterations = 300), offered, books) { languages, bookCodes ->
                val ordered = orderOfferedLanguages(languages, bookCodes.map(::book))

                ordered.toSet() shouldBe languages.toSet()
            }
        }

        test("a language with more books sorts before one with fewer, ties alphabetically") {
            checkAll(PropTestConfig(seed = 2026148, iterations = 300), offered, books) { languages, bookCodes ->
                val ordered = orderOfferedLanguages(languages, bookCodes.map(::book))
                val counts = bookCodes.groupingBy(::baseLanguage).eachCount()

                ordered.zipWithNext().all { (before, after) ->
                    val first = counts[before.language] ?: 0
                    val second = counts[after.language] ?: 0
                    first > second || (first == second && before.displayName <= after.displayName)
                } shouldBe true
            }
        }

        test("a language with no books is never listed before one that has a book") {
            checkAll(PropTestConfig(seed = 2026149, iterations = 300), offered, books) { languages, bookCodes ->
                val ordered = orderOfferedLanguages(languages, bookCodes.map(::book))
                val counts = bookCodes.groupingBy(::baseLanguage).eachCount()
                val firstWithoutBooks = ordered.indexOfFirst { candidate -> (counts[candidate.language] ?: 0) == 0 }

                (
                    firstWithoutBooks < 0 ||
                        ordered.drop(firstWithoutBooks).all { candidate ->
                            (counts[candidate.language] ?: 0) == 0
                        }
                    ) shouldBe
                    true
            }
        }
    })
