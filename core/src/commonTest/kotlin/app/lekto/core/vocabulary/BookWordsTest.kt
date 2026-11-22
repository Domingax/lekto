package app.lekto.core.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

/**
 * The words a **Book** gave the reader (issue #280): the count of the entries
 * whose history names the book, and how many of those are **Known**. The counts
 * come from the entries alone, so a book read before the history existed answers
 * none at all rather than a false zero (ADR-0050).
 */
@OptIn(ExperimentalKotest::class)
class BookWordsTest :
    FunSpec({

        fun entry(
            surface: String,
            books: List<String> = emptyList(),
            mastery: MasteryLevel = MasteryLevel.UNKNOWN,
        ): VocabularyEntry = VocabularyEntry(
            key = WordKey("en", surface),
            surface = surface,
            mastery = mastery,
            bookIds = books,
        )

        test("a book counts the words whose history names it") {
            val entries = listOf(
                entry("lantern", books = listOf("b1")),
                entry("harbour", books = listOf("b2")),
                entry("evening", books = listOf("b1", "b2")),
            )

            BookWords.of(entries, "b1") shouldBe BookWords(total = 2, known = 0)
        }

        test("a word saved from two books counts for both") {
            val entries = listOf(entry("lantern", books = listOf("b1", "b2")))

            BookWords.of(entries, "b1").total shouldBe 1
            BookWords.of(entries, "b2").total shouldBe 1
        }

        test("the known count counts only the book's words that are known") {
            val entries = listOf(
                entry("lantern", books = listOf("b1"), mastery = MasteryLevel.KNOWN),
                entry("harbour", books = listOf("b1"), mastery = MasteryLevel.LEARNED),
                entry("evening", books = listOf("b2"), mastery = MasteryLevel.KNOWN),
            )

            BookWords.of(entries, "b1") shouldBe BookWords(total = 2, known = 1)
        }

        test("a word with no book history is never counted") {
            val entries = listOf(entry("lantern"))

            BookWords.of(entries, "b1") shouldBe BookWords(total = 0, known = 0)
        }

        test("a book with no words is empty rather than a zero-word line") {
            BookWords.of(emptyList(), "b1").isEmpty shouldBe true
            BookWords(total = 1, known = 0).isEmpty shouldBe false
        }

        test("the counts are the entries' own, whatever the book id and the entries") {
            val books = Arb.list(Arb.string(), range = 0..3)
            val levels = Arb.element(*MasteryLevel.entries.toTypedArray())

            forAll(
                PropTestConfig(seed = 20261122, iterations = 300),
                Arb.list(Arb.string(), range = 0..8),
                books,
                books,
                levels,
                Arb.string(),
            ) { surfaces, firstBooks, secondBooks, mastery, bookId ->
                val entries = surfaces.mapIndexed { index, surface ->
                    VocabularyEntry(
                        key = WordKey("en", surface),
                        surface = surface,
                        mastery = mastery,
                        bookIds = if (index % 2 == 0) firstBooks else secondBooks,
                    )
                }

                val words = BookWords.of(entries, bookId)
                val fromBook = entries.filter { entry -> bookId in entry.bookIds }

                words.total == fromBook.size &&
                    words.known == fromBook.count { entry -> entry.mastery == MasteryLevel.KNOWN } &&
                    words.known <= words.total
            }
        }
    })
