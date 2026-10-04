package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The gate that replaces a pinned source hash: it fails only on a real anomaly. */
class SanityGateTest {

    @Test
    fun `a healthy pack passes`() {
        assertTrue(SanityGate.check(healthy(), previousLemmaCount = 71_808, collapseThreshold = 0.8).isEmpty())
    }

    @Test
    fun `an empty stream fails`() {
        val problems = SanityGate.check(
            healthy().copy(totalLines = 0),
            previousLemmaCount = null,
            collapseThreshold = 0.8,
        )

        assertTrue(problems.any { "zero lines" in it })
    }

    @Test
    fun `a schema that yields no English entries fails`() {
        val stats = healthy().copy(englishEntries = 0, englishEntriesWithFrench = 0, lemmaCount = 0, entryCount = 0)
        val problems = SanityGate.check(stats, previousLemmaCount = null, collapseThreshold = 0.8)

        assertTrue(problems.any { "schema" in it })
    }

    @Test
    fun `coverage below the threshold of the previous build fails`() {
        val problems = SanityGate.check(
            healthy().copy(lemmaCount = 50_000),
            previousLemmaCount = 71_808,
            collapseThreshold = 0.8,
        )

        assertEquals(1, problems.size)
        assertTrue("coverage collapsed" in problems.single())
    }

    @Test
    fun `a first build with no previous count is not gated on coverage`() {
        assertTrue(SanityGate.check(healthy(), previousLemmaCount = null, collapseThreshold = 0.8).isEmpty())
    }

    private fun healthy(): PackStats = PackStats(
        totalLines = 10_913_997,
        malformedLines = 0,
        englishEntries = 1_492_836,
        englishEntriesWithFrench = 77_665,
        lemmaCount = 71_808,
        entryCount = 77_665,
        formCount = 57_631,
        senseCount = 200_000,
        translationCount = 128_486,
    )
}
