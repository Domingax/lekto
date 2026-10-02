package app.lekto.core.text

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import java.text.Normalizer

/**
 * Word identity over arbitrary code points, including awkward Unicode: the key
 * must be equal for canonically equivalent forms, the same surface form must
 * key the same, and the language and lemma must drive the key. It lives in
 * `jvmTest`, next to the JVM `normaliseSurface` actual, because the NFC/NFD
 * pairing needs a platform normaliser.
 */
@OptIn(ExperimentalKotest::class)
class WordKeyUnicodePropertyTest :
    FunSpec({

        val surfaces = Arb.string(
            minSize = 0,
            maxSize = 24,
            codepoints = Arb.int(0..0x10FFFF).map(::Codepoint),
        )

        test("normalisation is idempotent") {
            forAll(PropTestConfig(seed = 20261022, iterations = 300), surfaces) { surface ->
                val once = normaliseSurface(surface)
                normaliseSurface(once) == once
            }
        }

        test("canonically equivalent forms share a key") {
            forAll(PropTestConfig(seed = 20261023, iterations = 300), surfaces) { surface ->
                val nfd = Normalizer.normalize(surface, Normalizer.Form.NFD)
                val nfc = Normalizer.normalize(surface, Normalizer.Form.NFC)
                wordKeyOf(nfd, "fr") == wordKeyOf(nfc, "fr")
            }
        }

        test("the same surface form in the same language always yields the same key") {
            forAll(PropTestConfig(seed = 20261024, iterations = 300), surfaces) { surface ->
                wordKeyOf(surface, "fr") == wordKeyOf(surface, "fr")
            }
        }

        test("the language separates otherwise identical surface forms") {
            forAll(PropTestConfig(seed = 20261025, iterations = 200), surfaces) { surface ->
                wordKeyOf(surface, "en") != wordKeyOf(surface, "fr")
            }
        }

        test("without a lemma mapping the normalised surface form is the key") {
            forAll(PropTestConfig(seed = 20261026, iterations = 300), surfaces) { surface ->
                wordKeyOf(surface, "fr", LemmaLookup.None).key == normaliseSurface(surface)
            }
        }
    })
