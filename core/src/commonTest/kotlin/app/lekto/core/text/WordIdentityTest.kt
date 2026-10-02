package app.lekto.core.text

import app.lekto.testkit.InMemoryLemmaLookup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Word identity in the small (ticket #13; ADR-0006): the cases the property
 * tests generalise — an inflection and its lemma share a key, a missing lemma
 * falls back to the normalised surface form, and case and Unicode form are
 * folded away.
 */
class WordIdentityTest :
    FunSpec({

        test("an inflection collapses to its lemma's key") {
            val lemmas = InMemoryLemmaLookup(mapOf("mangeais" to "manger", "mange" to "manger"))

            wordKeyOf("mangeais", "fr", lemmas) shouldBe WordKey("fr", "manger")
            wordKeyOf("mangeais", "fr", lemmas) shouldBe wordKeyOf("mange", "fr", lemmas)
        }

        test("a known lemma is used as the dictionary pack stores it") {
            val lemmas = InMemoryLemmaLookup(mapOf("Mangeais" to "Manger"))

            wordKeyOf("Mangeais", "fr", lemmas) shouldBe WordKey("fr", "Manger")
        }

        test("without a lemma the normalised surface form is the key") {
            wordKeyOf("Mangeais", "fr", InMemoryLemmaLookup(emptyMap())) shouldBe WordKey("fr", "mangeais")
        }

        test("case folds into one key") {
            wordKeyOf("Lantern", "en") shouldBe wordKeyOf("lantern", "en")
        }
    })
