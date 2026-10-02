package app.lekto.core.text

import app.lekto.testkit.InMemoryLemmaLookup
import app.lekto.testkit.WhitespaceTextSegmenter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The tokeniser in the small (ticket #13): the words and the separators between
 * them survive together, so the input reconstructs exactly, and each word's key
 * carries its language and identity.
 */
class TokenisationTest :
    FunSpec({

        val segmenter = WhitespaceTextSegmenter()

        test("words and their separators are kept, so the text round-trips") {
            val tokenised = tokenise("The quick, brown fox!", "en", segmenter)

            tokenised.words.map { it.surface } shouldContainExactly listOf("The", "quick", "brown", "fox")
            tokenised.text shouldBe "The quick, brown fox!"
        }

        test("a word's key follows its language") {
            val tokenised = tokenise("The", "en", segmenter)

            tokenised.words.single().key shouldBe WordKey("en", "the")
        }

        test("a segmented word's token carries its lemma key") {
            val lemmas = InMemoryLemmaLookup(mapOf("mangeais" to "manger"))

            val tokenised = tokenise("Elle mangeais hier", "fr", segmenter, lemmas)

            tokenised.words.map { it.surface } shouldContainExactly
                listOf("Elle", "mangeais", "hier")
            tokenised.words.map { it.key.key } shouldContainExactly
                listOf("elle", "manger", "hier")
        }

        test("text without words is one separator") {
            val tokenised = tokenise("— ! —", "en", segmenter)

            tokenised.words shouldBe emptyList()
            tokenised.text shouldBe "— ! —"
        }
    })
