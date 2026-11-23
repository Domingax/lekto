package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The coverage bars the guide needs from every shipped **Sample** (issue #324):
 * the demonstrated paragraph is the one with the most distinct highlighted
 * levels (ties keep the earlier), A1 must name every highlighted level, and A1
 * to B2 must keep three coloured words over two levels. Each case names the
 * arithmetic, so a change to a bar fails here rather than in the guide.
 */
class SampleCoverageTest {

    @Test
    fun `a four-level A1 paragraph keeps every band's spread`() {
        // A2 and B1 and B2 cover the lower three notes, two C1 words and one C2
        // word keep B2's three coloured words over two levels.
        requireSampleCoverage(sampleOf("A2", "B1", "B2", "C1", "C1", "C2"))
    }

    @Test
    fun `an A1 paragraph without an unknown-level word is refused`() {
        // Without a word above B2, A1 shows LEARNED, FAMILIAR and RECOGNIZED only.
        val error = assertFailsWith<IllegalArgumentException> {
            requireSampleCoverage(sampleOf("A2", "B1", "B2"))
        }

        assertTrue("A1" in error.message.orEmpty())
        assertTrue("UNKNOWN" in error.message.orEmpty())
    }

    @Test
    fun `a B2 paragraph with a single coloured word is refused`() {
        // Two B2 words keep B1's spread, but at B2 only the C1 word is coloured.
        val error = assertFailsWith<IllegalArgumentException> {
            requireSampleCoverage(sampleOf("A2", "B1", "B2", "B2", "C1"))
        }

        assertTrue("B2" in error.message.orEmpty())
        assertTrue("1 coloured word" in error.message.orEmpty())
    }

    @Test
    fun `three coloured words on one level are not a spread`() {
        // Three C1 words are coloured at B2, but all three carry LEARNED.
        val error = assertFailsWith<IllegalArgumentException> {
            requireSampleCoverage(sampleOf("A2", "B1", "B2", "C1", "C1", "C1"))
        }

        assertTrue("B2" in error.message.orEmpty())
        assertTrue("over 1 level(s)" in error.message.orEmpty())
    }

    @Test
    fun `the demonstrated paragraph is the richest one, not the first`() {
        val sample = sampleOf(
            "A2", "B1", "A2", "B1", "B2", "C1", "C1", "C2",
            paragraphs = listOf("alpha beta", "gamma delta epsilon zeta eta theta"),
        )

        requireSampleCoverage(sample)
    }

    @Test
    fun `a tie keeps the earlier paragraph, as the guide does`() {
        val sample = sampleOf(
            "A2", "B1", "B2", "B2", "C1", "A2", "B1", "B2", "C1", "C1", "C1",
            paragraphs = listOf("alpha beta gamma delta epsilon", "zeta eta theta iota kappa lambda"),
        )

        val error = assertFailsWith<IllegalArgumentException> { requireSampleCoverage(sample) }

        assertTrue("B2" in error.message.orEmpty(), "the first paragraph's thin B2 is the one demonstrated")
    }

    /** One English sample whose words take [bands] in reading order. */
    private fun sampleOf(
        vararg bands: String?,
        paragraphs: List<String> = listOf(WORDS.take(bands.size).joinToString(" ")),
    ): SampleContent {
        val words = paragraphs.flatMap { paragraph -> paragraph.split(" ") }
        require(words.size == bands.size) { "one band per word" }
        val difficulty = words.zip(bands).toMap()
        return SampleContent(
            language = "en",
            title = "A Coverage Sample",
            paragraphs = paragraphs,
            difficulty = difficulty,
            forms = emptyMap(),
            colourMaps = CEFR_BANDS.associateWith { readerBand ->
                difficulty.mapValues { (_, band) -> sampleMastery(band, readerBand) }
            },
            glossaries = emptyMap(),
        )
    }

    private companion object {
        /** Enough named words for the longest case; the names are opaque to the rule. */
        val WORDS: List<String> = listOf(
            "alpha",
            "beta",
            "gamma",
            "delta",
            "epsilon",
            "zeta",
            "eta",
            "theta",
            "iota",
            "kappa",
        )
    }
}
