package app.lekto.core.book

import app.lekto.core.Seams
import app.lekto.core.epub.EpubParser
import app.lekto.core.text.TxtParser
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.EpubFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files

/**
 * The library over the real JVM pieces — the EPUB parser, the directory vault and
 * the derived store — so an import is proven to round-trip a genuinely awkward
 * EPUB to disk and back (issue #15).
 */
class JvmBookLibraryTest :
    FunSpec({

        val roots = mutableListOf<File>()

        fun newRoot(): File = Files.createTempDirectory("lekto-library").toFile().also(roots::add)

        /** The derived store is a sibling of the vault, never inside it (ADR-0010). */
        fun derivedBeside(root: File): DerivedAssetStore =
            DerivedAssetStore(JvmVaultFileSystem(File(root.parentFile, "${root.name}-derived")))

        fun libraryOver(root: File, derived: DerivedAssetStore = derivedBeside(root)) = VaultBookLibrary(
            vault = JsonVaultStore(JvmVaultFileSystem(root)),
            derived = derived,
            parsers = mapOf(BookFormat.EPUB to EpubParser(), BookFormat.TXT to TxtParser()),
            seams = Seams.system(),
            deviceId = DeviceId("device-test"),
        )

        afterSpec { roots.forEach(File::deleteRecursively) }

        test("a real EPUB imports, lists and opens with its parsed text") {
            val library = libraryOver(newRoot())

            val book = library.import("awkward.epub", EpubFixtures.awkward())

            library.books().map { it.id } shouldBe listOf(book.id)
            val session = library.open(book.id)
            session?.book shouldBe book
            session?.text?.blocks?.joinToString(" ") { it.text }.orEmpty() shouldContain "awkward"
        }

        test("a text file imports and opens with its paragraphs") {
            val library = libraryOver(newRoot())

            val book = library.import("article.txt", "Bonjour le monde.\n\nAu revoir.".encodeToByteArray())

            library.open(book.id)?.text?.plainText() shouldBe "Bonjour le monde.\nAu revoir."
            book.title shouldBe "article"
        }

        test("a book survives reopening after the derived cache is wiped") {
            val root = newRoot()
            val derived = derivedBeside(root)
            val library = libraryOver(root, derived)
            val book = library.import("awkward.epub", EpubFixtures.awkward())
            val before = library.open(book.id)?.text
            derived.clear()

            library.open(book.id)?.text shouldBe before
        }

        test("a JSON record and its binary attachment are both on disk") {
            val root = newRoot()
            val library = libraryOver(root)

            val book = library.import("awkward.epub", EpubFixtures.awkward())

            root.resolve("books/${book.id}.json").isFile shouldBe true
            root.resolve("_attachments/${book.id}").isFile shouldBe true
        }
    })
