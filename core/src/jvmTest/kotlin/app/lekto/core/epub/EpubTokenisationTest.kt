package app.lekto.core.epub

import app.lekto.core.text.IcuTextSegmenter
import app.lekto.testkit.EpubFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The spike end to end (ticket #10): the parsed blocks are the input the
 * segmenter turns into [app.lekto.core.text.WordToken]s, so a paragraph that
 * came out of the EPUB container is checked all the way to its words.
 */
class EpubTokenisationTest :
    FunSpec({

        test("a parsed paragraph segments into words with positions in the block") {
            val book = EpubParser().parse(EpubFixtures.awkward())
            val english = book.blocks.first { it.text.startsWith("An awkward") }

            val words = IcuTextSegmenter().words(english.text, book.language)

            words.first().surface shouldBe "An"
            english.text.substring(words.first().start, words.first().end) shouldBe "An"
        }

        test("a parsed CJK paragraph segments by dictionary") {
            val book = EpubParser().parse(EpubFixtures.awkward())
            val japanese = book.blocks.first { it.text.startsWith("日本語") }

            val words = IcuTextSegmenter().words(japanese.text, "ja").map { it.surface }

            words shouldContainExactly listOf("日本語", "の", "文章", "です", "これ", "は", "テスト", "です")
        }
    })
