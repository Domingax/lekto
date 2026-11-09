package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The decomposed run's assembly (issue #306, ADR-0047): the **Language manifest**
 * is built from the per-artefact manifests the run's separate jobs produced, so a
 * failed or thin pair is absent rather than advertised, and every surviving entry
 * names bytes the run wrote.
 */
class LanguageManifestAssemblyTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `assembles the manifest from what the run produced`() {
        val assets = assets()
        writePack(assets, "en", "fr", lemmaCount = 51_039, bytes = "dictionary bytes!")
        writeDifficulty(assets, "en", bytes = "difficulty bytes")

        val manifest = assembleLanguageManifest(assets, BASE_URL)
        val language = manifest.languages.single()

        assertEquals(LanguageManifestFormat.VERSION, manifest.formatVersion)
        assertEquals("en", language.language)
        assertEquals(
            ManifestArtifact(
                name = "lekto-difficulty-en.sqlite.gz",
                url = "$BASE_URL/lekto-difficulty-en.sqlite.gz",
                sha256 = "cac53ac8da6e017b8ccae92557d471671e16fc29a7fe819fb7330ec91f4edb96",
                sizeBytes = 16,
            ),
            language.difficulty,
        )
        assertEquals(
            ManifestDictionary(
                translationLanguage = "fr",
                name = "lekto-dictionary-en-fr.sqlite.gz",
                url = "$BASE_URL/lekto-dictionary-en-fr.sqlite.gz",
                sha256 = "a0482b5a9dc3973ddba625ec590e893049361dec73be3e844e218121f8c12102",
                sizeBytes = 17,
            ),
            language.dictionaries.single(),
        )
    }

    @Test
    fun `a pair below the floor is absent while its sibling publishes`() {
        val assets = assets()
        writePack(assets, "en", "fr", lemmaCount = 51_039, bytes = "dictionary bytes!")
        writePack(assets, "en", "tr", lemmaCount = MINIMUM_PUBLISHED_LEMMAS - 1, bytes = "thin bytes")

        val language = assembleLanguageManifest(assets, BASE_URL).languages.single()

        assertEquals(listOf("fr"), language.dictionaries.map(ManifestDictionary::translationLanguage))
    }

    @Test
    fun `a failed pair leaves no trace in the manifest`() {
        val assets = assets()
        writePack(assets, "en", "fr", lemmaCount = 51_039, bytes = "dictionary bytes!")

        val language = assembleLanguageManifest(assets, BASE_URL).languages.single()

        assertNull(language.difficulty, "a table the run did not build is absent, not null")
        assertEquals(listOf("fr"), language.dictionaries.map(ManifestDictionary::translationLanguage))
    }

    @Test
    fun `an asset with no per-artefact manifest is never advertised`() {
        val assets = assets()
        Files.write(assets.resolve("lekto-dictionary-en-fr.sqlite.gz"), "orphan".encodeToByteArray())

        assertTrue(assembleLanguageManifest(assets, BASE_URL).languages.isEmpty())
    }

    @Test
    fun `a manifest naming bytes the run did not write is refused`() {
        val assets = assets()
        writePack(assets, "en", "fr", lemmaCount = 51_039, bytes = "dictionary bytes!")
        Files.delete(assets.resolve("lekto-dictionary-en-fr.sqlite.gz"))

        val error = assertFailsWith<IllegalArgumentException> {
            assembleLanguageManifest(assets, BASE_URL)
        }

        assertTrue("lekto-dictionary-en-fr.sqlite.gz" in error.message.orEmpty())
    }

    @Test
    fun `the assembly is deterministic`() {
        val assets = assets()
        writePack(assets, "en", "fr", lemmaCount = 51_039, bytes = "dictionary bytes!")
        writePack(assets, "fr", "en", lemmaCount = 85_277, bytes = "other dictionary")
        writeDifficulty(assets, "en", bytes = "difficulty bytes")

        assertEquals(
            assembleLanguageManifest(assets, BASE_URL).canonicalJson(),
            assembleLanguageManifest(assets, BASE_URL).canonicalJson(),
        )
    }

    @Test
    fun `the assembly writes the movable manifest into the asset directory`() {
        val assets = assets()
        writePack(assets, "en", "fr", lemmaCount = 51_039, bytes = "dictionary bytes!")

        runAssemble(CliOptions.Assemble(assets, BASE_URL))

        val written = manifestJson.decodeFromString<LanguageManifest>(
            Files.readString(assets.resolve(RELEASE_MANIFEST_NAME)),
        )
        assertEquals(LanguageManifestFormat.VERSION, written.formatVersion)
        assertEquals("fr", written.languages.single().dictionaries.single().translationLanguage)
    }

    @Test
    fun `a pair exactly at the floor publishes`() {
        val assets = assets()
        writePack(assets, "en", "tr", lemmaCount = MINIMUM_PUBLISHED_LEMMAS, bytes = "floor bytes")

        val language = assembleLanguageManifest(assets, BASE_URL).languages.single()

        assertEquals("tr", language.dictionaries.single().translationLanguage)
    }

    /** The asset directory with the compressed bytes, before any per-artefact manifest is written. */
    private fun assets(): Path = directory.resolve("assets").also(Files::createDirectories)

    @Suppress("LongParameterList") // The pair, its count and the bytes, each independently set by a case.
    private fun writePack(assets: Path, reading: String, translation: String, lemmaCount: Int, bytes: String) {
        val stem = dictionaryStem(reading, translation)
        Files.write(assets.resolve("$stem.sqlite.gz"), bytes.encodeToByteArray())
        Files.writeString(assets.resolve("manifest-$stem.json"), packManifest(reading, lemmaCount))
    }

    private fun writeDifficulty(assets: Path, language: String, bytes: String) {
        val stem = difficultyStem(language)
        Files.write(assets.resolve("$stem.sqlite.gz"), bytes.encodeToByteArray())
        Files.writeString(assets.resolve("manifest-$stem.json"), difficultyManifest(language))
    }

    private fun packManifest(reading: String, lemmaCount: Int): String = manifestJson.encodeToString(
        PackManifest(
            formatVersion = PackFormat.VERSION,
            provenance = PackProvenance("https://kaikki.org/$reading.jsonl.gz", "0".repeat(64), "2026-11-02"),
            stats = PackStats(
                totalLines = 100,
                malformedLines = 0,
                readingEntries = 80,
                entriesWithTranslation = lemmaCount.toLong(),
                lemmaCount = lemmaCount,
                entryCount = lemmaCount,
                formCount = lemmaCount,
                senseCount = lemmaCount,
                translationCount = lemmaCount,
            ),
        ),
    )

    private fun difficultyManifest(language: String): String = manifestJson.encodeToString(
        DifficultyManifest(
            formatVersion = DifficultyFormat.VERSION,
            provenance = PackProvenance("https://kaikki.org/$language.jsonl.gz", "0".repeat(64), "2026-11-02"),
            calibration = DifficultyCalibration(listOf(1, 2, 3, 4, 5, 6), "test", "test"),
            stats = DifficultyStats(frequencyForms = 100, wordKeys = 90, bandedWords = 80),
        ),
    )

    private companion object {
        const val BASE_URL: String = "https://github.com/Domingax/lekto-data/releases/download/dictionary-20261101"
    }
}
