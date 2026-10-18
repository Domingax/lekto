package app.lekto.core.reading

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The reading preference's own rules (ADR-0035): the defaults reproduce today's
 * fixed reading style, the leading is derived from the size rather than stored,
 * the margin steps cover today's 24.dp, and the size control stops at its bounds.
 */
class ReadingPreferencesTest :
    FunSpec({

        test("the defaults reproduce today's fixed reading style") {
            ReadingPreferences.DEFAULT.face shouldBe ReadingFace.SERIF
            ReadingPreferences.DEFAULT.size shouldBe 18f
            ReadingPreferences.DEFAULT.margin shouldBe ReadingMargin.COMFORTABLE
            ReadingPreferences.DEFAULT.horizontalMargin shouldBe 24f
        }

        test("the leading is derived from the size, not a second control") {
            ReadingPreferences(size = 18f).leading shouldBe 18f * ReadingPreferences.LEADING_RATIO
            ReadingPreferences(size = 28f).leading shouldBe 28f * ReadingPreferences.LEADING_RATIO
        }

        test("a larger size gives a larger leading") {
            (ReadingPreferences(size = 24f).leading > ReadingPreferences(size = 18f).leading) shouldBe true
        }

        test("the margin is one of three steps around today's 24.dp") {
            ReadingMargin.entries.map { margin -> margin.horizontalMargin } shouldBe listOf(16f, 24f, 32f)
        }

        test("the size steps up by one step and stops at the maximum") {
            ReadingPreferences(size = 18f).larger().size shouldBe 20f
            ReadingPreferences(size = ReadingPreferences.MAX_SIZE).larger().size shouldBe ReadingPreferences.MAX_SIZE
        }

        test("the size steps down by one step and stops at the minimum") {
            ReadingPreferences(size = 18f).smaller().size shouldBe 16f
            ReadingPreferences(size = ReadingPreferences.MIN_SIZE).smaller().size shouldBe ReadingPreferences.MIN_SIZE
        }

        test("stepping leaves the face and the margin alone") {
            val chosen = ReadingPreferences(ReadingFace.SANS_SERIF, 18f, ReadingMargin.WIDE)

            chosen.larger() shouldBe chosen.copy(size = 20f)
        }
    })
