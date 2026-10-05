package app.lekto.core.text

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

/**
 * The context sentence a vocabulary entry keeps (issue #22): the sentence around
 * a word, bounded by sentence terminators, with a safe answer when the range is
 * not in the text.
 */
@OptIn(ExperimentalKotest::class)
class ContextSentenceTest :
    FunSpec({

        test("the sentence around a word is returned with its punctuation") {
            val text = "The lantern burned. The keeper waited."

            contextSentence(text, 4, 11) shouldBe "The lantern burned."
            contextSentence(text, 23, 29) shouldBe "The keeper waited."
        }

        test("a word with no terminator before it starts the text") {
            contextSentence("lantern burned brightly", 0, 7) shouldBe "lantern burned brightly"
        }

        test("a line break is a sentence boundary") {
            contextSentence("First line\nSecond line", 11, 17) shouldBe "Second line"
        }

        test("a range outside the text has no sentence") {
            contextSentence("short", 10, 12) shouldBe null
            contextSentence("short", 3, 3) shouldBe null
        }

        test("the extracted sentence always contains the word") {
            val terminatorFree = Arb.string().map { text -> text.filter { char -> char.isLetterOrDigit() } }
                .filter { text -> text.isNotEmpty() }
            forAll(
                PropTestConfig(seed = 20261022, iterations = 200),
                terminatorFree,
                terminatorFree,
                terminatorFree,
            ) { before, word, after ->
                val text = "$before $word $after."
                val start = before.length + 1
                val sentence = contextSentence(text, start, start + word.length)

                sentence != null && sentence.contains(word)
            }
        }
    })
