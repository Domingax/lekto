package app.lekto.core.book

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
import app.lekto.core.vault.Tombstone
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultFileSystem
import app.lekto.core.vault.VaultStore
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TestClock
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

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
            BookFormat.EPUB to BookTextParser { ParsedBook(epubText) },
            BookFormat.TXT to TxtParser(),
        )

        fun newLibrary(
            vault: InMemoryVaultStore = InMemoryVaultStore(),
            derived: DerivedAssetStore = DerivedAssetStore(InMemoryVaultFileSystem()),
            tombstones: TombstoneStore = InMemoryTombstoneStore(),
            seams: app.lekto.core.Seams = deterministicSeams(),
        ): VaultBookLibrary = VaultBookLibrary(
            vault = vault,
            derived = derived,
            parsers = parsers,
            tombstones = tombstones,
            seams = seams,
            deviceId = DeviceId("device-a"),
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

            val book = library.import("Article.txt", "Un texte.\n\nLa suite.".encodeToByteArray())

            library.books() shouldContainExactly listOf(book)
            book.title shouldBe "Article"
            book.format shouldBe BookFormat.TXT
            book.language shouldBe null
        }

        test("a title the text reads as wins over the file name") {
            val library = newLibrary()

            val book = library.import(
                "mousquetaires.txt",
                "Les Trois Mousquetaires\n\nChapter I\n\nUn texte.".encodeToByteArray(),
            )

            book.title shouldBe "Les Trois Mousquetaires"
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

        test("a book that was never opened has no saved position and opens at the start") {
            val library = newLibrary()
            val book = library.import("Lantern.epub", byteArrayOf(1))

            library.position(book.id) shouldBe null
            library.open(book.id)?.position shouldBe null
        }

        test("a saved position round-trips through the vault and the session") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault)
            val book = library.import("Lantern.epub", byteArrayOf(1))

            library.savePosition(ReadingPosition(book.id, offset = 1234))

            library.position(book.id) shouldBe ReadingPosition(book.id, 1234)
            library.open(book.id)?.position shouldBe ReadingPosition(book.id, 1234)
            vault.get(ReadingPositionRecord.idOf(book.id))?.kind shouldBe ReadingPositionRecord.KIND
        }

        test("the reading position is its own record and never replaces the book") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault)
            val book = library.import("Lantern.epub", byteArrayOf(1))

            library.savePosition(ReadingPosition(book.id, offset = 10))

            vault.get(book.id)?.kind shouldBe BookRecord.KIND
            library.books() shouldContainExactly listOf(book)
        }

        test("the listing counts a saved position, even at its first page, as read") {
            val clock = TestClock()
            val library = newLibrary(seams = deterministicSeams(clock = clock))
            val alpha = library.import("Alpha.txt", "Text.".encodeToByteArray())
            val bravo = library.import("Bravo.txt", "Text.".encodeToByteArray())
            val charlie = library.import("Charlie.txt", "Text.".encodeToByteArray())
            val delta = library.import("Delta.txt", "Text.".encodeToByteArray())

            library.savePosition(ReadingPosition(alpha.id, offset = 10))
            clock.advanceBy(1.minutes)
            library.savePosition(ReadingPosition(bravo.id, offset = 10))
            clock.advanceBy(1.minutes)
            library.savePosition(ReadingPosition(charlie.id, offset = 10))
            clock.advanceBy(1.minutes)
            // Read last, then turned back to its first page: still the most recent.
            library.savePosition(ReadingPosition(delta.id, offset = 0))

            library.listing().lastRead.map { book -> book.title } shouldBe listOf("Delta", "Charlie", "Bravo")
            library.listing().others.map { book -> book.title } shouldBe listOf("Alpha")
        }

        test("the listing orders books by when their position last moved") {
            val clock = TestClock()
            val library = newLibrary(seams = deterministicSeams(clock = clock))
            val alpha = library.import("Alpha.txt", "Text.".encodeToByteArray())
            val bravo = library.import("Bravo.txt", "Text.".encodeToByteArray())
            val charlie = library.import("Charlie.txt", "Text.".encodeToByteArray())
            val delta = library.import("Delta.txt", "Text.".encodeToByteArray())

            library.savePosition(ReadingPosition(delta.id, offset = 10))
            clock.advanceBy(1.minutes)
            library.savePosition(ReadingPosition(alpha.id, offset = 10))
            clock.advanceBy(1.minutes)
            library.savePosition(ReadingPosition(charlie.id, offset = 10))
            clock.advanceBy(1.minutes)
            library.savePosition(ReadingPosition(bravo.id, offset = 10))

            library.listing().lastRead.map { book -> book.title } shouldBe listOf("Bravo", "Charlie", "Alpha")
            library.listing().others.map { book -> book.title } shouldBe listOf("Delta")
        }

        test("a book with an unreadable position record is not recently read") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            vault.put(
                ReadingPositionRecord.of(
                    ReadingPosition(book.id, 5),
                    deterministicSeams().clock.now(),
                    DeviceId("device-a"),
                ).copy(body = kotlinx.serialization.json.JsonObject(emptyMap())),
            )

            library.listing().lastRead shouldBe emptyList()
            library.listing().others shouldContainExactly listOf(book)
        }

        test("a book with an unreadable progress record still opens") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            val broken = ReadingPositionRecord.of(
                ReadingPosition(book.id, 5),
                deterministicSeams().clock.now(),
                DeviceId("device-a"),
            ).copy(body = kotlinx.serialization.json.JsonObject(emptyMap()))
            vault.put(broken)

            library.position(book.id) shouldBe null
            library.open(book.id)?.position shouldBe null
            library.open(book.id)?.book shouldBe book
        }

        test("opening re-parses and re-caches when the derived cache is gone") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            derived.clear()

            library.open(book.id)?.text shouldBe epubText
            derived.paths() shouldContainExactly listOf(
                "book-text/${book.id}.json",
                "book-text/${book.id}.length",
            )
        }

        test("a cache written by another format version is re-parsed and rewritten") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            val stale = StructuredText(title = "Stale", language = null, blocks = emptyList())
            val json = VaultCodec.json.encodeToString(StructuredText.serializer(), stale)
            val previous = BookTextCache.FORMAT_VERSION - 1
            derived.put("book-text/${book.id}.json", """{"formatVersion":$previous,"text":$json}""".encodeToByteArray())

            library.open(book.id)?.text shouldBe epubText
            derived.get("book-text/${book.id}.json")?.decodeToString()
                ?.contains("\"formatVersion\": ${BookTextCache.FORMAT_VERSION}") shouldBe true
        }

        test("the parsed-text cache writes and removes the rendered length with the text") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))

            derived.get("book-text/${book.id}.length")?.decodeToString()?.toInt() shouldBe
                epubText.renderedLength()

            library.delete(book.id)

            derived.paths() shouldBe emptyList()
        }

        test("the listing reports a positioned book's progress without decoding its text") {
            // A derived store that would fail if listing read the parsed text: only
            // the tiny length entry may be opened (issue #196).
            val derived = DerivedAssetStore(TextBlindFileSystem(InMemoryVaultFileSystem()))
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 5))

            library.listing().progressOf(book) shouldBe
                BookProgress.Measured(ReadingProgress(offset = 5, length = epubText.renderedLength()).percent)
        }

        test("a positioned book with no length entry is unmeasured until it is measured") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 6))
            derived.remove("book-text/${book.id}.length")

            library.listing().progressOf(book) shouldBe BookProgress.Unmeasured

            library.measure(book.id) shouldBe ReadingProgress(offset = 6, length = epubText.renderedLength())
            derived.get("book-text/${book.id}.length")?.decodeToString()?.toInt() shouldBe epubText.renderedLength()
            library.listing().progressOf(book) shouldBe BookProgress.Measured(
                ReadingProgress(offset = 6, length = epubText.renderedLength()).percent,
            )
        }

        test("measuring a book with no reading position changes nothing") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1))

            library.measure(book.id) shouldBe null
            library.listing().progressOf(book) shouldBe BookProgress.NotStarted
        }

        test("a book whose text cannot be parsed stays unmeasured, never Not started") {
            val vault = InMemoryVaultStore()
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val book = newLibrary(vault = vault, derived = derived).import("Lantern.epub", byteArrayOf(1))
            newLibrary(vault = vault, derived = derived).savePosition(ReadingPosition(book.id, offset = 3))
            derived.clear()
            val broken = VaultBookLibrary(
                vault = vault,
                derived = derived,
                parsers = mapOf(BookFormat.EPUB to BookTextParser { error("not an epub") }),
                tombstones = InMemoryTombstoneStore(),
                seams = deterministicSeams(),
                deviceId = DeviceId("device-a"),
            )

            broken.measure(book.id) shouldBe null
            broken.listing().progressOf(book) shouldBe BookProgress.Unmeasured
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
                tombstones = InMemoryTombstoneStore(),
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
                tombstones = InMemoryTombstoneStore(),
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
            library.import("Alpha.txt", "Text.".encodeToByteArray())

            library.books().map { it.title } shouldContainExactly listOf("Alpha", "An EPUB")
        }

        test("deleting a book removes its record, its original, its cached text and its position") {
            val vault = InMemoryVaultStore()
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val library = newLibrary(vault = vault, derived = derived)
            val book = library.import("Lantern.epub", byteArrayOf(1, 2, 3))
            library.savePosition(ReadingPosition(book.id, offset = 42))

            library.delete(book.id)

            library.books() shouldBe emptyList()
            vault.get(book.id) shouldBe null
            vault.getAttachment(book.id) shouldBe null
            vault.get(ReadingPositionRecord.idOf(book.id)) shouldBe null
            derived.paths() shouldBe emptyList()
        }

        test("deleting a book leaves a tombstone for the book and for its position") {
            val vault = InMemoryVaultStore()
            val tombstones = InMemoryTombstoneStore()
            val library = newLibrary(vault = vault, tombstones = tombstones)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 7))

            library.delete(book.id)

            tombstones.get(book.id) shouldNotBe null
            tombstones.get(ReadingPositionRecord.idOf(book.id)) shouldNotBe null
        }

        test("deleting a book with no reading position leaves only the book's tombstone") {
            val tombstones = InMemoryTombstoneStore()
            val library = newLibrary(tombstones = tombstones)
            val book = library.import("Lantern.epub", byteArrayOf(1))

            library.delete(book.id)

            tombstones.all().map { tombstone -> tombstone.id } shouldContainExactly listOf(book.id)
        }

        test("deleting a book leaves the other books untouched") {
            val vault = InMemoryVaultStore()
            val library = newLibrary(vault = vault)
            val kept = library.import("Alpha.epub", byteArrayOf(1))
            val removed = library.import("Lantern.epub", byteArrayOf(2))

            library.delete(removed.id)

            library.books() shouldContainExactly listOf(kept)
            vault.getAttachment(kept.id) shouldBe byteArrayOf(1)
        }

        test("deleting an unknown book is a no-op and leaves no tombstone") {
            val tombstones = InMemoryTombstoneStore()

            newLibrary(tombstones = tombstones).delete("absent")

            tombstones.all() shouldBe emptyList()
        }

        test("a deleted book stays gone after a restart over the same vault") {
            val vault = InMemoryVaultStore()
            val book = newLibrary(vault = vault).import("Lantern.epub", byteArrayOf(1))

            newLibrary(vault = vault).delete(book.id)

            newLibrary(vault = vault).books() shouldBe emptyList()
        }

        test("a failure before the last step leaves the book listed and re-deletable") {
            val vault = InMemoryVaultStore()
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val tombstones = InMemoryTombstoneStore()
            val library = newLibrary(vault = vault, derived = derived, tombstones = tombstones)
            val book = library.import("Lantern.epub", byteArrayOf(1))
            library.savePosition(ReadingPosition(book.id, offset = 3))
            val failing = VaultBookLibrary(
                vault = FailingRemoval(vault, book.id),
                derived = derived,
                parsers = parsers,
                tombstones = tombstones,
                seams = deterministicSeams(),
                deviceId = DeviceId("device-a"),
            )

            // The book record's removal is the last step and it fails: the book
            // must stay fully listed, its position gone and both tombstones — the
            // write-ahead marks — already written.
            assertFailsWith<IllegalStateException> { failing.delete(book.id) }

            library.books() shouldContainExactly listOf(book)
            vault.get(ReadingPositionRecord.idOf(book.id)) shouldBe null
            tombstones.get(ReadingPositionRecord.idOf(book.id)) shouldNotBe null
            tombstones.get(book.id) shouldNotBe null

            // Replaying the delete over a working vault completes it.
            library.delete(book.id)

            library.books() shouldBe emptyList()
            vault.get(book.id) shouldBe null
            tombstones.get(book.id) shouldNotBe null
        }

        test("a failed tombstone write leaves the book listed and is completed by a replay") {
            val vault = InMemoryVaultStore()
            val tombstones = InMemoryTombstoneStore()
            val book = newLibrary(vault = vault, tombstones = tombstones).import("Lantern.epub", byteArrayOf(1))
            val failing = newLibrary(vault = vault, tombstones = FailingTombstoneWrite(tombstones, book.id))

            // The book's tombstone is written before its record is removed, so a
            // failed write leaves the record for the replay to key on.
            assertFailsWith<IllegalStateException> { failing.delete(book.id) }

            vault.get(book.id) shouldNotBe null
            tombstones.get(book.id) shouldBe null

            newLibrary(vault = vault, tombstones = tombstones).delete(book.id)

            vault.get(book.id) shouldBe null
            tombstones.get(book.id) shouldNotBe null
        }
    })

/**
 * A derived store that refuses to read the parsed text, so a test can prove the
 * Library's listing reads only the length entry (issue #196).
 */
private class TextBlindFileSystem(private val backing: VaultFileSystem) : VaultFileSystem by backing {
    override fun read(path: String): ByteArray? {
        check(!path.endsWith(".json")) { "listing decoded the parsed text: $path" }
        return backing.read(path)
    }
}

/** A vault whose [remove] fails for one id, so a delete can be made to fail part-way. */
private class FailingRemoval(private val backing: VaultStore, private val failingId: String) : VaultStore by backing {
    override fun remove(id: String) {
        if (id == failingId) error("disk full")
        backing.remove(id)
    }
}

/** A tombstone store whose [put] fails for one id, so a delete can be made to fail on its tombstone. */
private class FailingTombstoneWrite(private val backing: TombstoneStore, private val failingId: String) :
    TombstoneStore by backing {
    override fun put(tombstone: Tombstone) {
        if (tombstone.id == failingId) error("disk full")
        backing.put(tombstone)
    }
}

private fun failedImportLeavesNothing(
    library: VaultBookLibrary,
    vault: InMemoryVaultStore,
    derived: DerivedAssetStore,
) {
    library.books() shouldBe emptyList()
    vault.all() shouldBe emptyList()
    derived.paths() shouldBe emptyList()
}
