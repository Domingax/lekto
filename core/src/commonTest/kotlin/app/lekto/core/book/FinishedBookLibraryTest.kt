package app.lekto.core.book

import app.lekto.core.Seams
import app.lekto.core.sync.TombstoneStore
import app.lekto.core.text.BlockKind
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.ParsedBook
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.TxtParser
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TestClock
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration.Companion.minutes

/**
 * The **Finished** declaration in the vault (issue #200; ADR-0038): marking and
 * undoing it, the tombstone an undo leaves, the **Reading state** each book is
 * given, and the rule that a finished book never leads the **Library**.
 *
 * The suite is separate from [VaultBookLibraryTest] because it is a second fact
 * beside the reading position, proved on its own; the vault and derived stores
 * are the in-memory fakes, and EPUB is a fake parser.
 */
class FinishedBookLibraryTest :
    FunSpec({

        val epubText = StructuredText(
            title = "An EPUB",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Hello world")))),
        )
        val parsers = mapOf(
            BookFormat.EPUB to BookTextParser { ParsedBook(epubText) },
            BookFormat.TXT to TxtParser(),
        )

        fun newLibrary(
            vault: InMemoryVaultStore = InMemoryVaultStore(),
            derived: DerivedAssetStore = DerivedAssetStore(InMemoryVaultFileSystem()),
            tombstones: TombstoneStore = InMemoryTombstoneStore(),
            seams: Seams = deterministicSeams(),
        ): VaultBookLibrary = VaultBookLibrary(
            vault = vault,
            derived = derived,
            parsers = parsers,
            tombstones = tombstones,
            seams = seams,
            deviceId = DeviceId("device-a"),
        )

        test("marking a book finished writes its own record and leaves the position untouched") {
            val clock = TestClock()
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault = vault, seams = deterministicSeams(clock = clock))
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 42))
            val lastRead = vault.get(ReadingPositionRecord.idOf(book.id))?.updatedAt
            clock.advanceBy(1.minutes)

            library.markFinished(book.id)

            val record = vault.get(FinishedBookRecord.idOf(book.id))
            record?.kind shouldBe FinishedBookRecord.KIND
            record?.id shouldBe "${book.id}-finished"
            library.finished(book.id) shouldBe FinishedBook(book.id, clock.now())
            library.position(book.id) shouldBe ReadingPosition(book.id, 42)
            vault.get(ReadingPositionRecord.idOf(book.id))?.updatedAt shouldBe lastRead
            library.listing().readingStateOf(book) shouldBe ReadingState.FINISHED
        }

        test("declaring a finished book unfinished removes the record and leaves a tombstone") {
            val tombstones = InMemoryTombstoneStore()
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault = vault, tombstones = tombstones)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 42))
            library.markFinished(book.id)

            library.markUnfinished(book.id)

            library.finished(book.id) shouldBe null
            vault.get(FinishedBookRecord.idOf(book.id)) shouldBe null
            tombstones.get(FinishedBookRecord.idOf(book.id)) shouldNotBe null
            library.position(book.id) shouldBe ReadingPosition(book.id, 42)
            library.listing().readingStateOf(book) shouldBe ReadingState.IN_PROGRESS
        }

        test("declaring an unfinished book unfinished leaves no tombstone") {
            val tombstones = InMemoryTombstoneStore()
            val library = newLibrary(tombstones = tombstones)
            val book = library.import("Lantern.epub", byteArrayOf(1))

            library.markUnfinished(book.id)

            tombstones.all() shouldBe emptyList()
            library.finished(book.id) shouldBe null
        }

        test("a vault with no finished record reads Unread and In progress, never Finished") {
            val library = newLibrary()
            val unread = library.import("Alpha.txt", "Text.".encodeToByteArray())
            val started = library.import("Bravo.txt", "Text.".encodeToByteArray())
            library.savePosition(ReadingPosition(started.id, offset = 3))

            library.finished(unread.id) shouldBe null
            library.listing().readingStateOf(unread) shouldBe ReadingState.UNREAD
            library.listing().readingStateOf(started) shouldBe ReadingState.IN_PROGRESS
        }

        test("the reading state is Finished whatever the position says") {
            val library = newLibrary()
            val unread = library.import("Alpha.txt", "Text.".encodeToByteArray())
            val started = library.import("Bravo.txt", "Text.".encodeToByteArray())
            library.savePosition(ReadingPosition(started.id, offset = 30))
            library.markFinished(unread.id)
            library.markFinished(started.id)

            val listing = library.listing()

            listing.readingStateOf(unread) shouldBe ReadingState.FINISHED
            listing.readingStateOf(started) shouldBe ReadingState.FINISHED
        }

        test("a finished book is never in the leading group, even when it was read last") {
            val clock = TestClock()
            val library = newLibrary(seams = deterministicSeams(clock = clock))
            val alpha = library.import("Alpha.txt", "Text.".encodeToByteArray())
            val bravo = library.import("Bravo.txt", "Text.".encodeToByteArray())
            val charlie = library.import("Charlie.txt", "Text.".encodeToByteArray())

            library.savePosition(ReadingPosition(alpha.id, offset = 1))
            clock.advanceBy(1.minutes)
            library.savePosition(ReadingPosition(bravo.id, offset = 1))
            clock.advanceBy(1.minutes)
            library.savePosition(ReadingPosition(charlie.id, offset = 1))
            library.markFinished(charlie.id)

            val listing = library.listing()

            listing.lastRead.map { book -> book.title } shouldBe listOf("Bravo", "Alpha")
            listing.others shouldContainExactly listOf(charlie)
        }

        test("deleting a book removes its finished record and leaves its tombstone") {
            val vault = InMemoryVaultStore()
            val tombstones = InMemoryTombstoneStore()
            val library = newLibrary(vault = vault, tombstones = tombstones)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.markFinished(book.id)

            library.delete(book.id)

            vault.get(FinishedBookRecord.idOf(book.id)) shouldBe null
            tombstones.get(FinishedBookRecord.idOf(book.id)) shouldNotBe null
            tombstones.get(book.id) shouldNotBe null
        }

        test("a finished declaration survives a reopened session and is never revoked by rereading") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault = vault)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.markFinished(book.id)
            val declaredAt = library.finished(book.id)!!.finishedAt

            newLibrary(vault = vault).open(book.id)?.finished shouldBe FinishedBook(book.id, declaredAt)
            library.savePosition(ReadingPosition(book.id, offset = 1))

            newLibrary(vault = vault).finished(book.id) shouldBe FinishedBook(book.id, declaredAt)
        }
    })
