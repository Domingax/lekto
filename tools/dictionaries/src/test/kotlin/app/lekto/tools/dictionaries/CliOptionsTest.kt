package app.lekto.tools.dictionaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The command line: required flags, defaults, the two modes and the errors a bad invocation must produce. */
class CliOptionsTest {

    @Test
    fun `parses the required flags and the default pair`() {
        val options = CliOptions.parse(validArgs()) as CliOptions.Build

        assertEquals("raw.jsonl.gz", options.input.fileName.toString())
        assertEquals("pack.sqlite", options.output.fileName.toString())
        assertEquals("https://kaikki.org/raw.jsonl.gz", options.provenance.sourceUrl)
        assertEquals("abc123", options.provenance.sourceSha256)
        assertEquals("2026-10-04", options.provenance.extractionDate)
        assertEquals(PackFormat.ENGLISH, options.readingLanguage)
        assertEquals(PackFormat.FRENCH, options.translationLanguage)
        assertEquals(DifficultyFormat.ENGLISH, options.difficultyLanguage)
        assertEquals(emptyMap(), options.gate.previous)
        assertEquals(SanityGate.DEFAULT_COLLAPSE_THRESHOLD, options.gate.collapseThreshold)
    }

    @Test
    fun `takes the reading, translation and difficulty languages`() {
        val options = CliOptions.parse(
            validArgs("--reading-language", "fr", "--translation-language", "es", "--difficulty-language", "es"),
        ) as CliOptions.Build

        assertEquals("fr", options.readingLanguage)
        assertEquals("es", options.translationLanguage)
        assertEquals("es", options.difficultyLanguage)
    }

    @Test
    fun `defaults the difficulty language to the reading language`() {
        val options = CliOptions.parse(validArgs("--reading-language", "fr")) as CliOptions.Build

        assertEquals("fr", options.difficultyLanguage)
    }

    @Test
    fun `defaults the notice and manifest beside the output`() {
        val options = CliOptions.parse(validArgs()) as CliOptions.Build

        assertEquals(options.output.resolveSibling("NOTICE"), options.outputs.notice)
        assertEquals(options.output.resolveSibling("manifest.json"), options.outputs.manifest)
        assertEquals(options.output.resolveSibling("language-manifest.json"), options.languageManifest)
    }

    @Test
    fun `takes an explicit language manifest path`() {
        val options = CliOptions.parse(validArgs("--language-manifest", "published/language-manifest.json"))
            as CliOptions.Build

        assertEquals("published/language-manifest.json", options.languageManifest.toString())
    }

    @Test
    fun `keys a build's previous lemma count by its reading language`() {
        val options = CliOptions.parse(
            validArgs("--reading-language", "fr", "--previous-lemma-count", "71808"),
        ) as CliOptions.Build

        assertEquals(71_808, options.gate.previous.getValue("fr"))
    }

    @Test
    fun `carries the threshold through`() {
        val options = CliOptions.parse(validArgs("--collapse-threshold", "0.9")) as CliOptions.Build

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

    @Test
    fun `parses the catalogue mode with a frequency per difficulty language`() {
        val options = CliOptions.parse(
            arrayOf(
                "--input", "raw.jsonl.gz",
                "--languages-config", "languages.json",
                "--output-dir", "out",
                "--source-url", "u",
                "--source-sha256", "s",
                "--extraction-date", "2026-10-04",
                "--frequency", "en=wordfreq-en.tsv.gz",
                "--frequency", "es=wordfreq-es.tsv.gz",
                "--previous-lemma-count", "en=71808",
            ),
        ) as CliOptions.Catalogue

        assertEquals("languages.json", options.config.fileName.toString())
        assertEquals("out", options.outputDir.fileName.toString())
        assertEquals("wordfreq-en.tsv.gz", options.frequencies.getValue("en").fileName.toString())
        assertEquals("wordfreq-es.tsv.gz", options.frequencies.getValue("es").fileName.toString())
        assertEquals(71_808, options.gate.previous.getValue("en"))
        assertEquals(options.outputDir.resolve("language-manifest.json"), options.languageManifest)
    }

    @Test
    fun `rejects a malformed assignment flag`() {
        assertFailsWith<IllegalArgumentException> {
            CliOptions.parse(
                arrayOf(
                    "--input", "raw.jsonl.gz",
                    "--languages-config", "languages.json",
                    "--output-dir", "out",
                    "--source-url", "u",
                    "--source-sha256", "s",
                    "--extraction-date", "2026-10-04",
                    "--frequency", "en",
                ),
            )
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
