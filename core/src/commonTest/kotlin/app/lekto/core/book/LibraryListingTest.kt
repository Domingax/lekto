package app.lekto.core.book

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

/**
 * The Library's ordering rule (issue #188): the three books whose reading
 * position moved most recently lead, then every other book by title.
 */
class LibraryListingTest :
    FunSpec({

        fun book(title: String): Book = Book(
            id = title.lowercase(),
            title = title,
            language = "en",
            format = BookFormat.TXT,
            fileName = "$title.txt",
        )

        /** A distinct last-read instant per reading order, so ties are explicit. */
        fun moment(minute: Int): Instant = Instant.fromEpochMilliseconds(minute * 60_000L)

        test("the three most recently read books lead, most recent first") {
            val alpha = book("Alpha")
            val bravo = book("Bravo")
            val charlie = book("Charlie")
            val delta = book("Delta")
            val echo = book("Echo")

            val listing = LibraryListing.of(
                books = listOf(charlie, alpha, echo, bravo, delta),
                lastReadByBook = mapOf(
                    alpha.id to moment(4),
                    echo.id to moment(3),
                    bravo.id to moment(2),
                    delta.id to moment(1),
                ),
            )

            listing.lastRead.map { it.title } shouldBe listOf("Alpha", "Echo", "Bravo")
            listing.others.map { it.title } shouldBe listOf("Charlie", "Delta")
        }

        test("the rest keep title order, whatever order the books arrive in") {
            val alpha = book("Alpha")
            val charlie = book("Charlie")
            val echo = book("Echo")

            val listing = LibraryListing.of(listOf(echo, alpha, charlie), emptyMap())

            listing.lastRead shouldBe emptyList()
            listing.others.map { it.title } shouldBe listOf("Alpha", "Charlie", "Echo")
        }

        test("books read at the same moment order by title, including the third place") {
            val alpha = book("Alpha")
            val bravo = book("Bravo")
            val charlie = book("Charlie")
            val delta = book("Delta")
            val readTogether = listOf(alpha, bravo, charlie, delta).associate { it.id to moment(1) }

            val listing = LibraryListing.of(listOf(delta, charlie, bravo, alpha), readTogether)

            listing.lastRead.map { it.title } shouldBe listOf("Alpha", "Bravo", "Charlie")
            listing.others.map { it.title } shouldBe listOf("Delta")
        }

        test("an empty vault lists nothing") {
            LibraryListing.of(emptyList(), emptyMap()) shouldBe LibraryListing()
        }

        test("a finished book falls into the title-ordered rest and never leads") {
            val alpha = book("Alpha")
            val bravo = book("Bravo")
            val charlie = book("Charlie")

            val listing = LibraryListing.of(
                books = listOf(alpha, bravo, charlie),
                lastReadByBook = mapOf(
                    alpha.id to moment(2),
                    charlie.id to moment(3),
                ),
                readingStateByBook = mapOf(
                    alpha.id to ReadingState.IN_PROGRESS,
                    charlie.id to ReadingState.FINISHED,
                ),
            )

            listing.lastRead.map { it.title } shouldBe listOf("Alpha")
            listing.others.map { it.title } shouldBe listOf("Bravo", "Charlie")
            listing.readingStateOf(charlie) shouldBe ReadingState.FINISHED
        }

        test("a listing without a state map reads a positioned book as In progress and the rest as Unread") {
            val started = book("Started")
            val unread = book("Unread")
            val listing = LibraryListing.of(
                books = listOf(started, unread),
                lastReadByBook = mapOf(started.id to moment(1)),
                progressByBook = mapOf(started.id to BookProgress.Measured(10)),
            )

            listing.readingStateOf(started) shouldBe ReadingState.IN_PROGRESS
            listing.readingStateOf(unread) shouldBe ReadingState.UNREAD
        }
    })
