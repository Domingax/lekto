package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The sample stage (issue #297): the authored text plus a built **Difficulty
 * table** and the built **Dictionary pack**s produce exactly one catalogue entry,
 * with six maps and one glossary per published translation language. It reads the
 * same SQLite artifacts the app installs, so a regeneration starts from a data
 * release rather than a workflow-only intermediate.
 */
class SampleStageTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `emits one sample with six maps and one glossary per published pack`() {
        writeSource()
        writeArtefacts()
        val manifest = writeManifest()
        val output = directory.resolve("catalogue.json")

        runSample(CliOptions.Sample(directory.resolve("samples"), directory.resolve("artifacts"), manifest, output))

        val catalogue = sampleCatalogJson.decodeFromString<SampleCatalogueDocument>(Files.readString(output))
        assertEquals(SampleFormat.VERSION, catalogue.formatVersion)
        assertEquals(listOf("en"), catalogue.samples.map(SampleContent::language))
        val sample = catalogue.samples.single()
        assertEquals(CEFR_BANDS, sample.colourMaps.keys.toList())
        assertEquals(setOf("fr", "es"), sample.glossaries.keys)
        assertEquals(setOf("boat", "calm", "rock", "wait"), sample.glossaries.getValue("fr").entries.keys)
        assertEquals("KNOWN", sample.colourMaps.getValue("C2").getValue("boat"))
        assertEquals("UNKNOWN", sample.colourMaps.getValue("A1").getValue("rock"))
        assertEquals(mapOf("rocks" to "rock", "waited" to "wait"), sample.forms)
        assertEquals(
            mapOf(
                "a" to "A1",
                "boat" to "B1",
                "calm" to "A2",
                "near" to "A1",
                "rock" to "C1",
                "the" to "A1",
                "wait" to "B2",
            ),
            sample.difficulty,
        )
    }

    @Test
    fun `the same inputs yield the same catalogue bytes`() {
        writeSource()
        writeArtefacts()
        val manifest = writeManifest()
        val first = directory.resolve("first.json")
        val second = directory.resolve("second.json")

        runSample(CliOptions.Sample(directory.resolve("samples"), directory.resolve("artifacts"), manifest, first))
        runSample(CliOptions.Sample(directory.resolve("samples"), directory.resolve("artifacts"), manifest, second))

        assertTrue(Files.readAllBytes(first).contentEquals(Files.readAllBytes(second)))
    }

    @Test
    fun `a language the manifest publishes no dictionary for is refused`() {
        writeSource()
        writeArtefacts()
        val manifest = writeManifest(pairs = emptyList())

        val error = assertFailsWith<IllegalArgumentException> {
            runSample(
                CliOptions.Sample(
                    directory.resolve("samples"),
                    directory.resolve("artifacts"),
                    manifest,
                    directory.resolve("catalogue.json"),
                ),
            )
        }

        assertTrue("no dictionary" in error.message.orEmpty())
    }

    @Test
    fun `a language with no difficulty table is refused, never given a guessed map`() {
        writeSource()
        val manifest = writeManifest()
        val error = assertFailsWith<IllegalStateException> {
            runSample(
                CliOptions.Sample(
                    directory.resolve("samples"),
                    directory.resolve("artifacts").also(Files::createDirectories),
                    manifest,
                    directory.resolve("catalogue.json"),
                ),
            )
        }

        assertTrue("lekto-difficulty-en.sqlite" in error.message.orEmpty())
    }

    @Test
    fun `a source directory with no text is refused`() {
        Files.createDirectories(directory.resolve("samples"))

        assertFailsWith<IllegalArgumentException> {
            runSample(
                CliOptions.Sample(
                    directory.resolve("samples"),
                    directory.resolve("artifacts").also(Files::createDirectories),
                    writeManifest(),
                    directory.resolve("catalogue.json"),
                ),
            )
        }
    }

    private fun writeSource(): Path {
        val sources = directory.resolve("samples").also(Files::createDirectories)
        Files.writeString(
            sources.resolve("en.json"),
            """
            {"language":"en","title":"A Test Sample","paragraphs":["A calm boat waited near the rocks."]}
            """.trimIndent(),
        )
        return sources
    }

    private fun writeManifest(
        pairs: List<LanguagePair> = listOf(LanguagePair("en", "fr"), LanguagePair("en", "es")),
    ): Path {
        val manifest = buildLanguageManifest(pairs, listOf("en"))
        val file = directory.resolve("manifest.json")
        Files.writeString(file, manifest.canonicalJson())
        return file
    }

    private fun writeArtefacts() {
        val artifacts = directory.resolve("artifacts").also(Files::createDirectories)
        SqliteDifficultyWriter.write(DIFFICULTY, artifacts.resolve("lekto-difficulty-en.sqlite"))
        SqlitePackWriter.write(pack("fr"), artifacts.resolve("lekto-dictionary-en-fr.sqlite"))
        SqlitePackWriter.write(pack("es"), artifacts.resolve("lekto-dictionary-en-es.sqlite"))
    }

    private fun pack(translation: String): PackContent = PackContent(
        provenance = PROVENANCE,
        stats = PackStats(1, 0, 1, 1, 7, 7, 7, 7, 7),
        entries = listOf("a", "boat", "calm", "near", "rock", "the", "wait").map { lemma ->
            PackEntry(
                lemma = lemma,
                language = "en",
                partOfSpeech = "noun",
                senses = listOf(PackSense("The $lemma meaning.", listOf("$translation-$lemma"))),
            )
        },
        forms = listOf(PackForm("waited", "en", "wait"), PackForm("rocks", "en", "rock")),
    )

    private companion object {
        val PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-11-11")

        val DIFFICULTY = DifficultyContent(
            provenance = PROVENANCE,
            calibration = DifficultyCalibration(listOf(1, 2, 3, 4, 5, 6), "test", "test"),
            stats = DifficultyStats(7, 7, 7),
            rows = listOf(
                DifficultyRow("a", "en", "A1"),
                DifficultyRow("the", "en", "A1"),
                DifficultyRow("near", "en", "A1"),
                DifficultyRow("calm", "en", "A2"),
                DifficultyRow("boat", "en", "B1"),
                DifficultyRow("wait", "en", "B2"),
                DifficultyRow("rock", "en", "C1"),
            ),
        )
    }
}
