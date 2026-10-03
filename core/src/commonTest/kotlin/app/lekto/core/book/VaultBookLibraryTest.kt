package app.lekto.core.book

import app.lekto.core.text.BlockKind
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.TxtParser
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.assertFailsWith

/**
 * The import-and-library domain behaviour (issue #15).
 *
 * EPUB is a fake parser here because the real one is JVM-only and already
 * golden-tested; TXT runs the real [TxtParser]. The vault and derived stores are
 * the in-memory fakes, so the suite proves the domain's orchestration on the
 * fast loop.
 */
class VaultBookLibraryTest :
    FunSpec({

        val epubText = StructuredText(
            title = "An EPUB",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Hello world")))),
        )
        val parsers = mapOf(
            BookFormat.EPUB to BookTextParser { epubText },
            BookFormat.TXT to TxtParser(),
        )

        fun newLibrary(
            vault: InMemoryVaultStore = InMemoryVaultStore(),
            derived: DerivedAssetStore = DerivedAssetStore(InMemoryVaultFileSystem()),
            deviceId: DeviceId = DeviceId("device-a"),
        ): VaultBookLibrary = VaultBookLibrary(
            vault = vault,
            derived = derived,
            parsers = parsers,
            seams = deterministicSeams(),
            deviceId = deviceId,
        )

        test("importing an EPUB stores it in the vault and lists it in the library") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault)

            val book = library.import("Lantern.epub", byteArrayOf(1, 2, 3))

            library.books() shouldContainExactly listOf(book)
            book.title shouldBe "An EPUB"
            book.format shouldBe BookFormat.EPUB
            book.language shouldBe "en"
            vault.get(book.id)?.kind shouldBe BookRecord.KIND
            vault.getAttachment(book.id) shouldBe byteArrayOf(1, 2, 3)
        }

        test("importing a plain-text file stores it in the vault and lists it in the library") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault)

            val book = library.import("Article.txt", "Un\n\ntexte.".encodeToByteArray())

            library.books() shouldContainExactly listOf(book)
            book.title shouldBe "Article"
            book.format shouldBe BookFormat.TXT
            book.language shouldBe null
        }

        test("opening a book starts a reading session with its parsed text") {
            val library = newLibrary()
            val book = library.import("Lantern.epub", byteArrayOf(1))

            val session = library.open(book.id)

            session?.book shouldBe book
            session?.text shouldBe epubText
        }

        test("opening an unknown book starts no session") {
            newLibrary().open("absent") shouldBe null
        }

        test("opening re-parses and re-caches when the derived cache is gone") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            derived.clear()

            library.open(book.id)?.text shouldBe epubText
            derived.paths() shouldContainExactly listOf("book-text/${book.id}.json")
        }

        test("import reports progress from zero to one, never backwards") {
            val progress = mutableListOf<ImportProgress>()
            newLibrary().import("Lantern.epub", byteArrayOf(1)) { progress += it }

            progress.first().fraction shouldBe 0f
            progress.last().fraction shouldBe 1f
            progress.zipWithNext().all { (earlier, later) -> later.fraction >= earlier.fraction } shouldBe true
        }

        test("an unsupported file type is refused and leaves the vault empty") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault)

            val failure = assertFailsWith<ImportException> { library.import("Notes.pdf", byteArrayOf(1)) }

            failure.message!!.contains("Notes.pdf") shouldBe true
            library.books() shouldBe emptyList()
            vault.all() shouldBe emptyList()
        }

        test("a parser failure is reported and leaves the vault empty") {
            val vault = InMemoryVaultStore()
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val broken = VaultBookLibrary(
                vault = vault,
                derived = derived,
                parsers = mapOf(BookFormat.EPUB to BookTextParser { error("not an epub") }),
                seams = deterministicSeams(),
                deviceId = DeviceId("device-a"),
            )

            val failure = assertFailsWith<ImportException> { broken.import("Broken.epub", byteArrayOf(1)) }

            failure.message!!.contains("Broken.epub") shouldBe true
            failedImportLeavesNothing(broken, vault, derived)
        }

        test("a storage failure is reported and leaves nothing behind") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val backing = InMemoryVaultStore()
            val failingVault = object : app.lekto.core.vault.VaultStore by backing {
                override fun putAttachment(id: String, bytes: ByteArray) = error("disk full")
            }
            val library = VaultBookLibrary(
                vault = failingVault,
                derived = derived,
                parsers = parsers,
                seams = deterministicSeams(),
                deviceId = DeviceId("device-a"),
            )

            assertFailsWith<ImportException> { library.import("Lantern.epub", byteArrayOf(1)) }

            backing.all() shouldBe emptyList()
            derived.paths() shouldBe emptyList()
        }

        test("books are listed ordered by title regardless of import order") {
            val library = newLibrary()
            library.import("Zeta.epub", byteArrayOf(1))
            library.import("Alpha.txt", "text".encodeToByteArray())

            library.books().map { it.title } shouldContainExactly listOf("Alpha", "An EPUB")
        }
    })

private fun failedImportLeavesNothing(
    library: VaultBookLibrary,
    vault: InMemoryVaultStore,
    derived: DerivedAssetStore,
) {
    library.books() shouldBe emptyList()
    vault.all() shouldBe emptyList()
    derived.paths() shouldBe emptyList()
}
