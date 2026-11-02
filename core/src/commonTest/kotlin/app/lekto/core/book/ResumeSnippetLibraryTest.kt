package app.lekto.core.book

import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.TxtParser
import app.lekto.core.text.renderedLength
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The **Resume snippet**'s lifecycle (issue #258): saved beside the book with
 * its position, replaced by the next page turn, removed with the book, and never
 * written to the **Vault**. The paragraph chosen at an offset is
 * [BookSnippetTest]'s concern.
 */
class ResumeSnippetLibraryTest :
    FunSpec({

        val paragraphs = listOf(
            TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("The lantern burned."))),
            TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("The boats came home."))),
        )

        fun newLibrary(
            vault: InMemoryVaultStore = InMemoryVaultStore(),
            derived: DerivedAssetStore = DerivedAssetStore(InMemoryVaultFileSystem()),
        ): VaultBookLibrary = VaultBookLibrary(
            vault = vault,
            derived = derived,
            parsers = mapOf(BookFormat.TXT to TxtParser()),
            tombstones = InMemoryTombstoneStore(),
            seams = deterministicSeams(),
            deviceId = DeviceId("device-a"),
        )

        fun secondParagraphOffset(blocks: List<TextBlock>): Int = blocks[0].text.length + BLOCK_SEPARATOR.length

        test("saving a position stores the paragraph it landed in beside the book") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.txt", "The lantern burned.\n\nThe boats came home.".encodeToByteArray())

            library.savePosition(ReadingPosition(book.id, secondParagraphOffset(paragraphs)), paragraphs)

            library.snippet(book.id) shouldBe "The boats came home."
            derived.get("book-snippet/${book.id}.txt")?.decodeToString() shouldBe "The boats came home."
        }

        test("the next save replaces the snippet") {
            val library = newLibrary()
            val book = library.import("Lantern.txt", "The lantern burned.\n\nThe boats came home.".encodeToByteArray())

            library.savePosition(ReadingPosition(book.id, secondParagraphOffset(paragraphs)), paragraphs)
            library.savePosition(ReadingPosition(book.id, 0), paragraphs)

            library.snippet(book.id) shouldBe "The lantern burned."
        }

        test("a position past the last paragraph removes the snippet") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.txt", "Only text.".encodeToByteArray())
            val blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Only text."))))
            library.savePosition(ReadingPosition(book.id, offset = 0), blocks)

            library.savePosition(ReadingPosition(book.id, blocks.renderedLength() + 1), blocks)

            library.snippet(book.id) shouldBe null
            derived.get("book-snippet/${book.id}.txt") shouldBe null
        }

        test("a legacy save without text removes the snippet") {
            val library = newLibrary()
            val book = library.import("Lantern.txt", "The lantern burned.".encodeToByteArray())
            library.savePosition(ReadingPosition(book.id, offset = 0), paragraphs)

            library.savePosition(ReadingPosition(book.id, offset = 0))

            library.snippet(book.id) shouldBe null
        }

        test("deleting a book removes its snippet with its text") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.txt", "The lantern burned.".encodeToByteArray())
            library.savePosition(ReadingPosition(book.id, offset = 0), paragraphs)

            library.delete(book.id)

            derived.paths() shouldBe emptyList()
            library.snippet(book.id) shouldBe null
        }

        test("a snippet never enters a vault export") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault = vault)
            val book = library.import("Lantern.txt", "Only text.".encodeToByteArray())
            val blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("SNIPPET-BYTES-MARKER"))))

            library.savePosition(ReadingPosition(book.id, offset = 0), blocks)

            library.snippet(book.id) shouldBe "SNIPPET-BYTES-MARKER"
            vault.exportBundle().decodeToString().contains("SNIPPET-BYTES-MARKER") shouldBe false
        }
    })
