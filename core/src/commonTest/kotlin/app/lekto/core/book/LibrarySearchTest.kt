package app.lekto.core.book

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.forAll
import kotlin.time.Instant

/** A book whose id and file name are derived from its title, so a case is short to write. */
private fun book(title: String, language: String = "en", fileName: String = "$title.txt"): Book =
    Book(id = "$title/$fileName", title = title, language = language, format = BookFormat.TXT, fileName = fileName)

/** Titles built from ASCII pieces, so the property's independent filter and the folded one agree. */
private val asciiTitles: Arb<String> = Arb.list(
    Arb.element("Alpha", "beta ", "Gemma", "delta", "-", " "),
    0..4,
).map { pieces -> pieces.joinToString("") }

/** Queries over that alphabet: substrings, full titles, case variants, blanks and misses. */
private val queries: Arb<String> = Arb.element("a", "ALPHA", "lph", "Gemma", "delta", " ", "-", "zz")

/**
 * The Library's search (issue #173): a title query, matched as a case- and
 * accent-insensitive substring, flattening the `Continue reading` split into one
 * title-ordered list.
 */
@OptIn(ExperimentalKotest::class)
class LibrarySearchTest :
    FunSpec({

        test("a blank query lists every book in the order the screen shows them") {
            val read = book("Zebra")
            val other = book("Alpha")

            LibraryListing(lastRead = listOf(read), others = listOf(other)).matching("")
                .map { it.title } shouldBe listOf("Zebra", "Alpha")
        }

        test("a query matches a substring of the title anywhere in it") {
            val listing = LibraryListing(others = listOf(book("The Lantern Keeper")))

            listing.matching("lant").map { it.title } shouldBe listOf("The Lantern Keeper")
        }

        test("a query ignores case") {
            val listing = LibraryListing(others = listOf(book("The Lantern Keeper")))

            listing.matching("LANTERN").map { it.title } shouldBe listOf("The Lantern Keeper")
        }

        test("a query ignores accents, whichever side carries them") {
            val accented = LibraryListing(others = listOf(book("Émile")))
            val plain = LibraryListing(others = listOf(book("Emile")))

            accented.matching("emile").map { it.title } shouldBe listOf("Émile")
            accented.matching("EMILE").map { it.title } shouldBe listOf("Émile")
            plain.matching("émile").map { it.title } shouldBe listOf("Emile")
        }

        test("a query never matches the file name or the language") {
            val listing = LibraryListing(
                others = listOf(book("Lantern Keeper", language = "fr", fileName = "proust.epub")),
            )

            listing.matching("proust") shouldBe emptyList()
            listing.matching("fr") shouldBe emptyList()
        }

        test("a query flattens the two groups into title order") {
            val listing = LibraryListing(
                lastRead = listOf(book("Zebra")),
                others = listOf(book("Alpha"), book("Bravo")),
            )

            listing.matching("a").map { it.title } shouldBe listOf("Alpha", "Bravo", "Zebra")
        }

        test("a query trims its outer whitespace, and a blank one behaves as no query") {
            val listing = LibraryListing(others = listOf(book("The Lantern Keeper")))

            listing.matching("  lantern  ").map { it.title } shouldBe listOf("The Lantern Keeper")
            listing.matching("   ").map { it.title } shouldBe listOf("The Lantern Keeper")
        }

        test("a query keeps exactly the books whose title contains it, whatever order the vault returns") {
            forAll(
                PropTestConfig(seed = 20261020, iterations = 300),
                Arb.list(asciiTitles, 0..8),
                queries,
            ) { titles, query ->
                val books = titles.mapIndexed { index, title ->
                    Book("b$index", title, "en", BookFormat.TXT, "f$index.txt")
                }
                val read = books
                    .filterIndexed { index, _ -> index % 2 == 0 }
                    .associate { book -> book.id to Instant.fromEpochMilliseconds(book.id.removePrefix("b").toLong()) }
                val listing = LibraryListing.of(books, read)
                // A blank query keeps the screen order; a real one flattens it into
                // title order (issue #173).
                val shown = listing.lastRead + listing.others
                val expected = if (query.trim().isEmpty()) {
                    shown
                } else {
                    shown
                        .filter { book -> book.title.contains(query.trim(), ignoreCase = true) }
                        .sortedBy { book -> book.title.lowercase() }
                }

                listing.matching(query).map { book -> book.id } == expected.map { book -> book.id }
            }
        }
    })
