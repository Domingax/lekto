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

/**
 * Arbitrary code points, not printable ASCII: identity must survive combining
 * marks, emoji and lone surrogates.
 */
internal val anyCodepoints: Arb<Codepoint> = Arb.int(0..0x10FFFF).map(::Codepoint)

/**
 * Word identity as a property (ticket #13; ADR-0006): the key is
 * `(language, lemma | normalised surface form)`. The same surface form in the
 * same language must key the same, the language must separate them, a known
 * lemma must key as the lemma, and a missing lemma must fall back to the
 * normalised surface form. Normalisation's idempotence and Unicode canonical
 * equivalence need a platform normaliser, so they run in `jvmTest`.
 */
@OptIn(ExperimentalKotest::class)
class WordKeyPropertyTest :
    FunSpec({

        val surfaces = Arb.string(minSize = 0, maxSize = 24, codepoints = anyCodepoints)

        test("the same surface form in the same language always yields the same key") {
            forAll(PropTestConfig(seed = 20261015, iterations = 300), surfaces) { surface ->
                wordKeyOf(surface, "fr") == wordKeyOf(surface, "fr")
            }
        }

        test("the language separates otherwise identical surface forms") {
            forAll(PropTestConfig(seed = 20261016, iterations = 200), surfaces) { surface ->
                wordKeyOf(surface, "en") != wordKeyOf(surface, "fr")
            }
        }

        test("without a lemma mapping the normalised surface form is the key") {
            forAll(PropTestConfig(seed = 20261017, iterations = 300), surfaces) { surface ->
                wordKeyOf(surface, "fr", LemmaLookup.None).key == normaliseSurface(surface)
            }
        }

        test("a known lemma collapses every surface form to one key") {
            val oneLemma = LemmaLookup { _, _ -> "manger" }
            forAll(PropTestConfig(seed = 20261018, iterations = 200), surfaces, surfaces) { first, second ->
                wordKeyOf(first, "fr", oneLemma) == wordKeyOf(second, "fr", oneLemma)
            }
        }
    })
