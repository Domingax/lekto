package app.lekto.core.book

import app.lekto.core.text.BlockKind
import app.lekto.core.text.Chapter
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.VaultCodec
import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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

        test("an entry with another format version is a cache miss") {
            val (cache, derived) = cache()
            val bare = VaultCodec.json.encodeToString(StructuredText.serializer(), text)
            derived.put("book-text/book.json", """{"formatVersion":99,"text":$bare}""".encodeToByteArray())

            cache.textOf("book") shouldBe null
        }

        test("an unversioned entry is a cache miss") {
            val (cache, derived) = cache()
            val bare = VaultCodec.json.encodeToString(StructuredText.serializer(), text)
            derived.put("book-text/book.json", bare.encodeToByteArray())

            cache.textOf("book") shouldBe null
        }
    })
