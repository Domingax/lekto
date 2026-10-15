package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The gate that replaces a pinned source hash: it fails only on a real anomaly. */
class SanityGateTest {

    @Test
    fun `a healthy pack passes`() {
        assertTrue(check(healthy(), previousLemmaCount = 71_808).isEmpty())
    }

    @Test
    fun `an empty stream fails`() {
        val problems = check(healthy().copy(totalLines = 0), previousLemmaCount = null)

        assertTrue(problems.any { "zero lines" in it })
    }

    @Test
    fun `a schema that yields no reading-language entries fails`() {
        val stats = healthy().copy(readingEntries = 0, entriesWithTranslation = 0, lemmaCount = 0, entryCount = 0)

        val problems = check(stats, previousLemmaCount = null)

        assertTrue(problems.any { "schema" in it })
    }

    @Test
    fun `coverage below the threshold of the previous build fails`() {
        val problems = check(healthy().copy(lemmaCount = 50_000), previousLemmaCount = 71_808)

        assertEquals(1, problems.size)
        assertTrue("coverage collapsed" in problems.single())
    }

    @Test
    fun `a first build with no previous count is not gated on coverage`() {
        assertTrue(check(healthy(), previousLemmaCount = null).isEmpty())
    }

    private fun check(stats: PackStats, previousLemmaCount: Int?): List<String> = SanityGate.check(
        stats = stats,
        readingLanguage = PackFormat.ENGLISH,
        translationLanguage = PackFormat.FRENCH,
        previousLemmaCount = previousLemmaCount,
        collapseThreshold = 0.8,
    )

    private fun healthy(): PackStats = PackStats(
        totalLines = 10_913_997,
        malformedLines = 0,
        readingEntries = 1_492_836,
        entriesWithTranslation = 77_665,
        lemmaCount = 71_808,
        entryCount = 77_665,
        formCount = 57_631,
        senseCount = 200_000,
        translationCount = 128_486,
    )
}
