package app.lekto.core.book

import app.lekto.core.sync.TombstoneStore
import app.lekto.core.text.BlockKind
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.Chapter
import app.lekto.core.text.ParsedBook
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TextBlindFileSystem
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The Library naming the **Chapter** a **Reading position** sits in (issue
 * #260): the chapter starts are a **Derived asset** written beside the parsed
 * text, so listing a shelf names a chapter without decoding a book.
 */
class VaultBookLibraryChapterTest :
    FunSpec({

        // Two chapters, so a position can name one: "One" starts at block 1
        // (offset 14) and "Two" at block 2 (offset 19).
        val chapteredText = StructuredText(
            title = "A Chaptered Book",
            language = "en",
            blocks = listOf(
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Front matter"))),
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("One"))),
                TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Two"))),
            ),
            chapters = listOf(Chapter("One", startBlock = 1), Chapter("Two", startBlock = 2)),
        )

        fun library(
            derived: DerivedAssetStore = DerivedAssetStore(InMemoryVaultFileSystem()),
            tombstones: TombstoneStore = InMemoryTombstoneStore(),
            text: StructuredText = chapteredText,
        ): VaultBookLibrary = VaultBookLibrary(
            vault = InMemoryVaultStore(),
            derived = derived,
            parsers = mapOf(BookFormat.EPUB to BookTextParser { ParsedBook(text) }),
            tombstones = tombstones,
            seams = deterministicSeams(),
            deviceId = DeviceId("device-a"),
        )

        test("the listing names the chapter a positioned book is in") {
            val library = library()
            val book = library.import("Lantern.epub", byteArrayOf(1))

            library.savePosition(ReadingPosition(book.id, offset = 15))
            library.listing().chapterTitleOf(book) shouldBe "One"

            library.savePosition(ReadingPosition(book.id, offset = 0))
            library.listing().chapterTitleOf(book) shouldBe null

            library.savePosition(ReadingPosition(book.id, offset = 19))
            library.listing().chapterTitleOf(book) shouldBe "Two"
        }

        test("a book with one chapter names no chapter") {
            val single = StructuredText(
                title = "A Plain Book",
                language = "en",
                blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Only text")))),
                chapters = listOf(Chapter("A Plain Book", startBlock = 0)),
            )
            val library = library(text = single)
            val book = library.import("Plain.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 4))

            library.listing().chapterTitleOf(book) shouldBe null
        }

        test("the listing reads the chapter index, never the parsed text") {
            val derived = DerivedAssetStore(TextBlindFileSystem(InMemoryVaultFileSystem()))
            val library = library(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 15))

            library.listing().chapterTitleOf(book) shouldBe "One"
        }

        test("opening a book cached before the chapter index writes it") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = library(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 15))
            derived.remove("book-text/${book.id}.chapters")

            library.listing().chapterTitleOf(book) shouldBe null

            library.open(book.id)
            library.listing().chapterTitleOf(book) shouldBe "One"
        }

        test("deleting a book removes its chapter index") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = library(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))

            derived.get("book-text/${book.id}.chapters") shouldNotBe null

            library.delete(book.id)

            derived.paths() shouldBe emptyList()
        }
    })
