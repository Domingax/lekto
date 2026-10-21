package app.lekto.core.book

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll

/**
 * **Reading progress** is the share of a book's rendered text before its
 * **Reading position**, shown as a whole percentage rounded down (issue #196;
 * docs/testing.md, "Test levels": a derivation over generated data).
 */
@OptIn(ExperimentalKotest::class)
class ReadingProgressTest :
    FunSpec({

        test("a fraction of a percent is rounded down, so 28.9 % reads 28 %") {
            ReadingProgress(offset = 289, length = 1000).percent shouldBe 28
        }

        test("a position at the very start reads 0 %") {
            ReadingProgress(offset = 0, length = 1000).percent shouldBe 0
        }

        test("a position past the end is clamped to 100 %") {
            ReadingProgress(offset = 1250, length = 1000).percent shouldBe 100
        }

        test("the last page reads just under 100 %, never a 99 % cap") {
            ReadingProgress(offset = 999, length = 1000).percent shouldBe 99
            ReadingProgress(offset = 1000, length = 1000).percent shouldBe 100
        }

        test("a length of zero reads 0, because there is no text to be a share of") {
            ReadingProgress(offset = 10, length = 0).percent shouldBe 0
        }

        test("a percentage is always a whole number between 0 and 100, for arbitrary data") {
            forAll(
                PropTestConfig(seed = 20261021, iterations = 300),
                Arb.long(-100_000L..100_000L),
                Arb.int(1..100_000),
            ) { offset, length ->
                ReadingProgress(offset.toInt(), length).percent in 0..100
            }
        }

        test("a row with no measured length is unmeasured, never Not started") {
            val position = ReadingPosition(bookId = "b1", offset = 10)

            BookProgress.forPosition(position, length = null) shouldBe BookProgress.Unmeasured
            BookProgress.forPosition(position, length = 1000) shouldBe BookProgress.Measured(1)
        }
    })
