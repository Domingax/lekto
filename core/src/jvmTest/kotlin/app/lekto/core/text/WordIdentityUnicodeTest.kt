package app.lekto.core.text

import app.lekto.testkit.InMemoryLemmaLookup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The Unicode half of word identity (ticket #13): a composed and a decomposed
 * surface form share a key, because `normaliseSurface` folds them to one NFC
 * form. It lives in `jvmTest` beside the JVM `normaliseSurface` actual.
 */
class WordIdentityUnicodeTest :
    FunSpec({

        test("a composed and a decomposed surface form share a key") {
            // "café" written with é (U+00E9) and with e + combining acute (U+0301).
            wordKeyOf("caf\u00e9", "fr") shouldBe wordKeyOf("cafe\u0301", "fr")
        }

        test("a composed and a decomposed surface form share a lemma fallback") {
            val lemmas = InMemoryLemmaLookup(emptyMap())

            wordKeyOf("caf\u00e9", "fr", lemmas).key shouldBe "caf\u00e9"
        }
    })
