package app.lekto.core.book

import app.lekto.core.text.BlockKind
import app.lekto.core.text.Chapter
import app.lekto.core.text.ChapterStart
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.VaultCodec
import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The parsed-text cache's versioned envelope (ADR-0037, issue #198): a current
 * entry round-trips with its chapter list, and an entry written by an older or
 * unknown shape is a miss so the book is re-parsed rather than misread.
 */
class BookTextCacheTest :
    FunSpec({

        fun cache(): Pair<BookTextCache, DerivedAssetStore> {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            return BookTextCache(derived) to derived
        }

        val text = StructuredText(
            title = "Book",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("One.")))),
            chapters = listOf(Chapter("First", startBlock = 0)),
        )

        test("a stored text round-trips with its chapters") {
            val (cache, _) = cache()

            cache.put("book", text)

            cache.textOf("book") shouldBe text
        }

        test("a stored text's chapter starts are written beside it") {
            val (cache, derived) = cache()

            cache.put("book", text)

            cache.chaptersOf("book") shouldBe listOf(ChapterStart("First", startOffset = 0))
            derived.get("book-text/book.chapters") shouldNotBe null
        }

        test("chapter starts written by another format version are a miss") {
            val (cache, derived) = cache()
            derived.put("book-text/book.chapters", """{"formatVersion":99,"chapters":[]}""".encodeToByteArray())

            cache.chaptersOf("book") shouldBe null
        }

        test("an index a cache lost is rebuilt on demand, and an existing one is kept") {
            val (cache, derived) = cache()
            val other = text.copy(chapters = listOf(Chapter("Other", startBlock = 0)))
            cache.put("book", text)
            val original = derived.get("book-text/book.chapters")!!.decodeToString()

            cache.ensureChapters("book", other)

            derived.get("book-text/book.chapters")!!.decodeToString() shouldBe original

            derived.remove("book-text/book.chapters")
            cache.ensureChapters("book", other)

            cache.chaptersOf("book") shouldBe listOf(ChapterStart("Other", startOffset = 0))
        }

        test("a book with no chapter list writes an empty index and keeps it") {
            val (cache, _) = cache()
            cache.put("book", text.copy(chapters = emptyList()))

            cache.chaptersOf("book") shouldBe emptyList()

            cache.ensureChapters("book", text)

            cache.chaptersOf("book") shouldBe emptyList()
        }

        test("removing a book's text removes its chapter starts") {
            val (cache, derived) = cache()
            cache.put("book", text)

            cache.remove("book")

            derived.paths() shouldBe emptyList()
        }

        test("an entry with another format version is a cache miss") {
            val (cache, derived) = cache()
            val bare = VaultCodec.json.encodeToString(StructuredText.serializer(), text)
            derived.put("book-text/book.json", """{"formatVersion":99,"text":$bare}""".encodeToByteArray())

            cache.textOf("book") shouldBe null
        }

        test("a text cached before chapters were inferred is a cache miss") {
            // Version 1 is the shape ADR-0041 supersedes: a TXT cached as one
            // chapter must be re-parsed, or a Vault imported before the change
            // never grows a chapter list.
            val (cache, derived) = cache()
            val bare = VaultCodec.json.encodeToString(StructuredText.serializer(), text)
            derived.put("book-text/book.json", """{"formatVersion":1,"text":$bare}""".encodeToByteArray())

            cache.textOf("book") shouldBe null
        }

        test("an unversioned entry is a cache miss") {
            val (cache, derived) = cache()
            val bare = VaultCodec.json.encodeToString(StructuredText.serializer(), text)
            derived.put("book-text/book.json", bare.encodeToByteArray())

            cache.textOf("book") shouldBe null
        }
    })
