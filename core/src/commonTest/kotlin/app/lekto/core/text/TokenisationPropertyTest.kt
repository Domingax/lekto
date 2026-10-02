package app.lekto.core.text

import app.lekto.testkit.WhitespaceTextSegmenter
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

/**
 * Tokenisation as a property (ticket #13; docs/research/testing-harness.md,
 * property 7): concatenating token surface forms with their separators
 * reconstructs the input, every token is an ordered in-bounds slice that matches
 * its surface, and every token carries the key its surface and language imply.
 * The deterministic [WhitespaceTextSegmenter] keeps this independent of the
 * machine's ICU dictionaries; the arbitrary text is built from [anyCodepoints],
 * so combining marks, emoji and lone surrogates are exercised.
 */
@OptIn(ExperimentalKotest::class)
class TokenisationPropertyTest :
    FunSpec({

        val segmenter = WhitespaceTextSegmenter()
        val texts = Arb.string(minSize = 0, maxSize = 80, codepoints = anyCodepoints)
        val languages = Arb.string(minSize = 0, maxSize = 8)

        test("token surface forms with their separators reconstruct the input") {
            forAll(PropTestConfig(seed = 20261019, iterations = 400), texts, languages) { text, language ->
                tokenise(text, language, segmenter).text == text
            }
        }

        test("every token is an ordered, in-bounds slice that matches its surface") {
            forAll(PropTestConfig(seed = 20261020, iterations = 300), texts) { text ->
                var previousEnd = 0
                tokenise(text, "en", segmenter).words.all { token ->
                    val ordered = token.start >= previousEnd
                    val inBounds = token.start in 0 until token.end && token.end <= text.length
                    val matches = text.substring(token.start, token.end) == token.surface
                    previousEnd = token.end
                    ordered && inBounds && matches
                }
            }
        }

        test("every token carries the key its surface and language imply") {
            val lemmas = LemmaLookup { surface, _ -> "manger".takeIf { surface.lowercase() == "mangeais" } }
            forAll(PropTestConfig(seed = 20261021, iterations = 300), texts) { text ->
                tokenise(text, "fr", segmenter, lemmas).words.all { token ->
                    token.key == wordKeyOf(token.surface, "fr", lemmas)
                }
            }
        }
    })
