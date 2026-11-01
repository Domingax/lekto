package app.lekto.core.book

import app.lekto.core.text.BlockKind
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.ParsedBook
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VaultFileSystem
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.test.assertFailsWith

/**
 * A book's cover is a **Derived asset** (ADR-0005): written with its text,
 * removed with it, and re-derived from the original when the cache is wiped
 * (issue #259). It never enters the **Vault**, so nothing here may write a
 * record, and a failed import leaves none.
 */
class VaultBookCoverTest :
    FunSpec({

        val cover = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val text = StructuredText(
            title = "An EPUB",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Hello world")))),
        )

        fun parsers(cover: ByteArray? = null) = mapOf<BookFormat, BookTextParser>(
            BookFormat.EPUB to BookTextParser { ParsedBook(text, cover = cover) },
        )

        fun libraryOver(
            files: InMemoryVaultFileSystem,
            vault: InMemoryVaultStore = InMemoryVaultStore(),
            parsers: Map<BookFormat, BookTextParser> = parsers(cover),
        ) = VaultBookLibrary(
            vault = vault,
            derived = DerivedAssetStore(files),
            parsers = parsers,
            tombstones = InMemoryTombstoneStore(),
            seams = deterministicSeams(),
            deviceId = DeviceId("device-a"),
        )

        test("importing a book writes its cover beside its text, never in the vault") {
            val files = InMemoryVaultFileSystem()
            val vault = InMemoryVaultStore()
            val library = libraryOver(files, vault)

            val book = library.import("Book.epub", byteArrayOf(1, 2, 3))

            DerivedAssetStore(files).get("book-cover/${book.id}")?.contentEquals(cover) shouldBe true
            vault.all().map { record -> record.kind } shouldBe listOf(BookRecord.KIND)
        }

        test("a book with no cover writes none") {
            val files = InMemoryVaultFileSystem()
            val library = libraryOver(files, parsers = parsers(cover = null))

            val book = library.import("Book.epub", byteArrayOf(1, 2, 3))

            DerivedAssetStore(files).get("book-cover/${book.id}") shouldBe null
        }

        test("a cover never enters a vault export") {
            val marker = "COVER-BYTES-MARKER"
            val vault = InMemoryVaultStore()
            val library = libraryOver(
                InMemoryVaultFileSystem(),
                vault,
                parsers = parsers(cover = marker.encodeToByteArray()),
            )

            library.import("Book.epub", byteArrayOf(1, 2, 3))

            vault.exportBundle().decodeToString().contains(marker) shouldBe false
        }

        test("deleting a book removes its cover with its text") {
            val files = InMemoryVaultFileSystem()
            val library = libraryOver(files)
            val book = library.import("Book.epub", byteArrayOf(1, 2, 3))

            library.delete(book.id)

            DerivedAssetStore(files).paths() shouldBe emptyList()
        }

        test("a wiped cache re-derives the cover from the original") {
            val files = InMemoryVaultFileSystem()
            val library = libraryOver(files)
            val book = library.import("Book.epub", byteArrayOf(1, 2, 3))
            DerivedAssetStore(files).clear()

            library.open(book.id)

            DerivedAssetStore(files).get("book-cover/${book.id}")?.contentEquals(cover) shouldBe true
        }

        test("a storage failure removes a cover written before it") {
            val backing = InMemoryVaultFileSystem()
            val failing = object : VaultFileSystem by backing {
                override fun writeAtomically(path: String, bytes: ByteArray) {
                    if (path.startsWith("book-text/")) error("disk full")
                    backing.writeAtomically(path, bytes)
                }
            }
            val vault = InMemoryVaultStore()
            val library = VaultBookLibrary(
                vault = vault,
                derived = DerivedAssetStore(failing),
                parsers = parsers(cover),
                tombstones = InMemoryTombstoneStore(),
                seams = deterministicSeams(),
                deviceId = DeviceId("device-a"),
            )

            assertFailsWith<ImportException> { library.import("Book.epub", byteArrayOf(1, 2, 3)) }

            vault.all() shouldBe emptyList()
            backing.listFiles() shouldBe emptyList()
        }

        test("a cover that cannot be stored never fails the import") {
            val backing = InMemoryVaultFileSystem()
            val failing = object : VaultFileSystem by backing {
                override fun writeAtomically(path: String, bytes: ByteArray) {
                    if (path.startsWith("book-cover/")) error("disk full")
                    backing.writeAtomically(path, bytes)
                }
            }
            val vault = InMemoryVaultStore()
            val library = VaultBookLibrary(
                vault = vault,
                derived = DerivedAssetStore(failing),
                parsers = parsers(cover),
                tombstones = InMemoryTombstoneStore(),
                seams = deterministicSeams(),
                deviceId = DeviceId("device-a"),
            )

            val book = library.import("Book.epub", byteArrayOf(1, 2, 3))

            library.books() shouldBe listOf(book)
            library.open(book.id)?.text shouldBe text
            backing.listFiles().none { path -> path.startsWith("book-cover/") } shouldBe true
        }
    })
