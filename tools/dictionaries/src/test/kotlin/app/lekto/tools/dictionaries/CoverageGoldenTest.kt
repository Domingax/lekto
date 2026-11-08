package app.lekto.tools.dictionaries

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The coverage pass is a transform, so its canonical output is pinned against the
 * committed fixture and a second pass must reproduce the same bytes (issue #302).
 * The counts are the pack build's own, which is why this fixture is the pack
 * golden's.
 */
class CoverageGoldenTest {

    @Test
    fun `pins the canonical coverage of the committed fixture`() {
        val first = report().canonicalJson()

        assertEquals(Files.readString(GOLDEN).trim(), first.trim())
        assertEquals(first, report().canonicalJson(), "the same input must yield the same bytes")
    }

    private fun report(): CoverageReport {
        val fixture = Path.of("src/test/resources/en-fr-sample.jsonl")
        val coverage = Files.newBufferedReader(fixture).use { reader ->
            CoverageBuilder("en", setOf("fr", "es", "de")).build(reader)
        }
        return CoverageReport(listOf(coverage))
    }

    private companion object {
        val GOLDEN: Path = Path.of("src/test/resources/golden/coverage-en-sample.json")
    }
}
