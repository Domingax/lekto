package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The command line: required flags, defaults, and the errors a bad invocation must produce. */
class CliOptionsTest {

    @Test
    fun `parses the required flags`() {
        val options = CliOptions.parse(validArgs())

        assertEquals("raw.jsonl.gz", options.input.fileName.toString())
        assertEquals("pack.sqlite", options.output.fileName.toString())
        assertEquals("https://kaikki.org/raw.jsonl.gz", options.provenance.sourceUrl)
        assertEquals("abc123", options.provenance.sourceSha256)
        assertEquals("2026-10-04", options.provenance.extractionDate)
        assertNull(options.gate.previousLemmaCount)
        assertEquals(SanityGate.DEFAULT_COLLAPSE_THRESHOLD, options.gate.collapseThreshold)
    }

    @Test
    fun `defaults the notice and manifest beside the output`() {
        val options = CliOptions.parse(validArgs())

        assertEquals(options.output.resolveSibling("NOTICE"), options.outputs.notice)
        assertEquals(options.output.resolveSibling("manifest.json"), options.outputs.manifest)
    }

    @Test
    fun `carries the gate settings through`() {
        val options = CliOptions.parse(validArgs("--previous-lemma-count", "71808", "--collapse-threshold", "0.9"))

        assertEquals(71_808, options.gate.previousLemmaCount)
        assertEquals(0.9, options.gate.collapseThreshold)
    }

    @Test
    fun `rejects an unknown flag`() {
        assertFailsWith<IllegalArgumentException> { CliOptions.parse(arrayOf("--nope", "x")) }
    }

    @Test
    fun `rejects a missing required flag`() {
        val args = arrayOf(
            "--input",
            "raw.jsonl.gz",
            "--output",
            "pack.sqlite",
            "--source-url",
            "u",
            "--source-sha256",
            "s",
        )

        assertFailsWith<IllegalArgumentException> { CliOptions.parse(args) }
    }

    @Test
    fun `rejects a threshold outside the unit interval`() {
        assertFailsWith<IllegalArgumentException> {
            CliOptions.parse(validArgs("--collapse-threshold", "1.5"))
        }
    }
}

private fun validArgs(vararg extra: String): Array<String> = arrayOf(
    "--input", "raw.jsonl.gz",
    "--output", "pack.sqlite",
    "--source-url", "https://kaikki.org/raw.jsonl.gz",
    "--source-sha256", "abc123",
    "--extraction-date", "2026-10-04",
    *extra,
)
