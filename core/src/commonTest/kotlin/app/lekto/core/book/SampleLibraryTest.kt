package app.lekto.core.book

import app.lekto.core.sample.Sample
import app.lekto.core.sample.SampleCatalogContent
import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.VaultFileSystem
import app.lekto.testkit.InMemorySampleDismissedStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.testSample
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * The materialised **Sample book** (issue #278): the catalogue entry written to
 * the derived store under the stable identity `sample:<language>`, opened as a
 * normal reading session, and removed for good by one dismissal. Every case holds
 * the sample outside the **Vault**: it is a derived file, so nobody's export can
 * carry it and no sync target can receive it.
 */
class SampleLibraryTest :
    FunSpec({

        test("materialising a language writes the sample under its encoded path") {
            val files = InMemoryVaultFileSystem()
            val library = sampleLibrary(files)

            library.materialise("fr")

            // The identity `sample:fr` is not a legal file path (VaultPaths rejects
            // ':'), so the derived file encodes the language and nothing else.
            files.listFiles() shouldBe listOf("samples/fr.json")
        }

        test("the materialised sample carries the catalogue entry's identity and text") {
            val library = sampleLibrary(InMemoryVaultFileSystem())
            val sample = sampleLibrarySample("fr")

            val row = library.materialise("fr")
            val session = library.open(sample.identity)

            row?.book?.id shouldBe Sample.identityOf("fr")
            row?.book?.title shouldBe sample.title
            row?.book?.language shouldBe "fr"
            row?.book?.author shouldBe sample.author
            row?.progress shouldBe BookProgress.NotStarted
            row?.state shouldBe ReadingState.UNREAD
            session?.text?.blocks shouldBe sample.paragraphs.map { paragraph ->
                TextBlock(BlockKind.PARAGRAPH, listOf(app.lekto.core.text.TextRun(paragraph)))
            }
        }

        test("a second language materialises nothing: one sample, forever") {
            val files = InMemoryVaultFileSystem()
            val library = sampleLibrary(files)

            library.materialise("en")
            val again = library.materialise("fr")

            again?.book?.id shouldBe Sample.identityOf("en")
            files.listFiles() shouldBe listOf("samples/en.json")
        }

        test("a language the catalogue does not ship materialises nothing") {
            val files = InMemoryVaultFileSystem()

            sampleLibrary(files).materialise("xx") shouldBe null

            files.listFiles().shouldBeEmpty()
        }

        test("dismissing records the language, deletes the files and keeps the identity reserved") {
            val files = InMemoryVaultFileSystem()
            val dismissed = InMemorySampleDismissedStore()
            val library = sampleLibrary(files, dismissed)
            library.materialise("fr")

            library.dismiss(Sample.identityOf("fr")) shouldBe true

            library.row() shouldBe null
            files.listFiles().shouldBeEmpty()
            dismissed.load() shouldBe SampleDismissed("fr")
            // A build that ships the sample again writes nothing either.
            library.materialise("fr") shouldBe null
            files.listFiles().shouldBeEmpty()
        }

        test("a dismissal on a fresh install stops the sample from ever being written") {
            val files = InMemoryVaultFileSystem()
            val library = sampleLibrary(files, InMemorySampleDismissedStore(SampleDismissed("de")))

            library.materialise("de") shouldBe null
            library.row() shouldBe null

            files.listFiles().shouldBeEmpty()
        }

        test("a page turn is kept on the sample and never in the vault") {
            val files = InMemoryVaultFileSystem()
            val library = sampleLibrary(files, paragraphs = listOf("0123456789"))
            library.materialise("en")

            library.savePosition(ReadingPosition(Sample.identityOf("en"), 5)) shouldBe true

            library.row()?.progress shouldBe BookProgress.Measured(50)
            library.row()?.state shouldBe ReadingState.IN_PROGRESS
            library.open(Sample.identityOf("en"))?.position shouldBe ReadingPosition(Sample.identityOf("en"), 5)
            files.listFiles() shouldBe listOf("samples/en.json")
        }

        test("a position for a vault book is not the sample's to keep") {
            val files = InMemoryVaultFileSystem()
            val library = sampleLibrary(files)
            library.materialise("en")

            library.savePosition(ReadingPosition("b1", 5)) shouldBe false

            library.open("b1") shouldBe null
            library.row()?.progress shouldBe BookProgress.NotStarted
        }

        test("finishing the sample is a derived fact, not a vault declaration") {
            val files = InMemoryVaultFileSystem()
            val library = sampleLibrary(files)
            library.materialise("en")

            library.markFinished(Sample.identityOf("en"), finished = true) shouldBe true

            library.row()?.state shouldBe ReadingState.FINISHED
            library.open(Sample.identityOf("en"))?.finished?.bookId shouldBe Sample.identityOf("en")
            files.listFiles() shouldBe listOf("samples/en.json")
        }

        test("a corrupt materialised sample leaves no row and never a half-written one") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically("samples/en.json", "not a sample at all".encodeToByteArray())

            val library = sampleLibrary(files)

            library.row() shouldBe null
            library.open(Sample.identityOf("en")) shouldBe null
            // The corrupt file still counts as the one sample: the shelf never
            // invents a second row, and never points at the broken one.
            library.materialise("fr") shouldBe null
            files.listFiles() shouldBe listOf("samples/en.json")
        }

        test("a failed write leaves no row behind") {
            val files = WriteFailingFileSystem()
            val library = sampleLibrary(files)

            shouldThrow<IllegalStateException> { library.materialise("fr") }

            library.row() shouldBe null
            files.delegate.listFiles().shouldBeEmpty()
        }

        test("opening a sample this device does not hold returns nothing") {
            sampleLibrary(InMemoryVaultFileSystem()).open(Sample.identityOf("ja")) shouldBe null
        }

        test("the catalogue's sample is readable before it is materialised") {
            val library = sampleLibrary(InMemoryVaultFileSystem())

            library.sample("fr")?.language shouldBe "fr"
            library.sample("xx") shouldBe null
            library.row() shouldBe null
        }

        test("the sample's book is a text book the reader opens like any other") {
            val library = sampleLibrary(InMemoryVaultFileSystem())

            val row = library.materialise("en")
            val session = library.open(Sample.identityOf("en"))

            row?.book?.format shouldBe BookFormat.TXT
            row?.book?.fileName shouldBe "sample-en.txt"
            session?.book shouldBe row?.book
            session?.text?.language shouldBe "en"
        }
    })

/** A library over [files] shipping one sample per [sampleLanguages], with [paragraphs] as every sample's text. */
private fun sampleLibrary(
    files: VaultFileSystem,
    dismissed: SampleDismissedStore = InMemorySampleDismissedStore(),
    paragraphs: List<String> = listOf("A calm boat waited near the rocks."),
): SampleLibrary = SampleLibrary(
    catalogue = SampleCatalogContent(
        listOf("en", "de", "fr", "ja").map { language -> testSample(language = language, paragraphs = paragraphs) },
    ),
    derived = DerivedAssetStore(files),
    dismissed = dismissed,
)

private fun sampleLibrarySample(language: String): Sample =
    testSample(language = language, paragraphs = listOf("A calm boat waited near the rocks."))

/** A filesystem whose write throws, standing in for a full or read-only disk. */
private class WriteFailingFileSystem(val delegate: VaultFileSystem = InMemoryVaultFileSystem()) :
    VaultFileSystem by delegate {

    override fun writeAtomically(path: String, bytes: ByteArray) {
        error("the disk is full")
    }
}
