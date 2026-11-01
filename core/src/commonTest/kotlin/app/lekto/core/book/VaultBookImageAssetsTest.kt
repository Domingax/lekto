package app.lekto.core.book

import app.lekto.core.text.BlockKind
import app.lekto.core.text.BookAsset
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.ParsedBook
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * A book's images are **Derived assets** (ADR-0005): written with its text,
 * removed with it, and re-derived from the original when the cache is wiped.
 * They never enter the **Vault**, so nothing here may write a record.
 */
class VaultBookImageAssetsTest :
    FunSpec({

        val picture = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val text = StructuredText(
            title = "An EPUB",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Hello world")))),
        )
        val parsers = mapOf<BookFormat, BookTextParser>(
            BookFormat.EPUB to BookTextParser {
                ParsedBook(text, listOf(BookAsset("abc.png", picture)))
            },
        )

        fun libraryOver(files: InMemoryVaultFileSystem, vault: InMemoryVaultStore = InMemoryVaultStore()) =
            VaultBookLibrary(
                vault = vault,
                derived = DerivedAssetStore(files),
                parsers = parsers,
                tombstones = InMemoryTombstoneStore(),
                seams = deterministicSeams(),
                deviceId = DeviceId("device-a"),
            )

        test("importing a book writes its images beside its text, never in the vault") {
            val files = InMemoryVaultFileSystem()
            val vault = InMemoryVaultStore()
            val library = libraryOver(files, vault)

            val book = library.import("Book.epub", byteArrayOf(1, 2, 3))

            DerivedAssetStore(files).get("book-images/${book.id}/abc.png") shouldBe picture
            vault.all().map { record -> record.kind } shouldBe listOf(BookRecord.KIND)
        }

        test("deleting a book removes its images with it") {
            val files = InMemoryVaultFileSystem()
            val library = libraryOver(files)
            val book = library.import("Book.epub", byteArrayOf(1, 2, 3))

            library.delete(book.id)

            DerivedAssetStore(files).get("book-images/${book.id}/abc.png") shouldBe null
        }

        test("a wiped cache re-derives the images from the original") {
            val files = InMemoryVaultFileSystem()
            val library = libraryOver(files)
            val book = library.import("Book.epub", byteArrayOf(1, 2, 3))
            DerivedAssetStore(files).clear()

            library.open(book.id)

            DerivedAssetStore(files).get("book-images/${book.id}/abc.png") shouldBe picture
        }

        test("a derived image path cannot escape the store") {
            val files = InMemoryVaultFileSystem()
            val library = libraryOver(files)

            library.import("Book.epub", byteArrayOf(1, 2, 3))

            files.listFiles().any { path -> path.contains("..") } shouldBe false
        }
    })
