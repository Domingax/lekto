package app.lekto.core.text

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.PropTestConfig
import io.kotest.property.forAll

/**
 * The tokenisation invariant as a property (ticket #10; docs/testing.md, "Test
 * levels"): whatever the text, a [WordToken] is a non-blank, in-bounds slice
 * that matches the text it offsets, and the words stay in reading order and do
 * not overlap. Example tests pin the interesting cases; this pins the contract
 * the reader relies on — a tap's offsets must always address the right characters.
 *
 * It lives in `jvmTest`, not `commonTest`, because the only `TextSegmenter` so
 * far is the ICU-backed JVM implementation; a `commonTest` property will land
 * when a pure-Kotlin segmenter exists (see `docs/research/epub-to-tokens-spike.md`).
 */
@OptIn(ExperimentalKotest::class)
class IcuTextSegmenterPropertyTest :
    FunSpec({

        val segmenter = IcuTextSegmenter()

        test("every word is a non-blank, ordered, in-bounds slice of the text") {
            forAll<String>(PropTestConfig(seed = 20261001)) { text ->
                var previousEnd = 0
                segmenter.words(text, "en").all { word ->
                    val ordered = word.start >= previousEnd
                    val inBounds = word.start in 0 until word.end && word.end <= text.length
                    val matches = text.substring(word.start, word.end) == word.surface
                    val nonBlank = word.surface.isNotBlank()
                    previousEnd = word.end
                    ordered && inBounds && matches && nonBlank
                }
            }
        }
    })
