package app.lekto.core.text

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The segmenter against the cases the spike must prove (ticket #10): ordinary
 * Latin words with punctuation dropped, and CJK split by dictionary rather than
 * by character. ICU's own break iterator backs [IcuTextSegmenter], so these are
 * the edge cases ADR-0007 cares about, pinned here.
 */
class IcuTextSegmenterTest :
    FunSpec({

        val segmenter = IcuTextSegmenter()

        test("Latin text segments into words and drops punctuation and spaces") {
            segmenter.words("The quick, brown fox!", "en").map { it.surface } shouldContainExactly
                listOf("The", "quick", "brown", "fox")
        }

        test("word offsets point back into the segmented text") {
            val token = segmenter.words("The quick, brown fox!", "en")[1]

            token.surface shouldBe "quick"
            "The quick, brown fox!".substring(token.start, token.end) shouldBe "quick"
        }

        test("Japanese segments by dictionary, not by character") {
            val words = segmenter.words("日本語の文章です。", "ja").map { it.surface }

            // ICU's Japanese dictionary keeps 日本語 and 文章 whole; a per-character
            // split would yield eight one-character tokens.
            words shouldContainExactly listOf("日本語", "の", "文章", "です")
        }

        test("Chinese segments by dictionary, not by character") {
            val words = segmenter.words("我看着你，你好。", "zh").map { it.surface }

            words shouldContainExactly listOf("我看", "着", "你", "你好")
        }

        test("an unknown language still segments Latin text") {
            segmenter.words("Hello world", null).map { it.surface } shouldContainExactly
                listOf("Hello", "world")
        }

        test("empty text has no words") {
            segmenter.words("", "en") shouldBe emptyList()
        }
    })
