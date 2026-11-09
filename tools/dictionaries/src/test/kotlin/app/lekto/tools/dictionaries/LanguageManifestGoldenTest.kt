package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The language manifest's reproducibility evidence (issues #145, #161, #305): the
 * manifest is a pure union of the packs and difficulty tables a run published, so
 * the catalogue and the artefacts cannot drift, and the published format-2 shape
 * — each entry carrying the `url` the app follows, the `sha256` of the bytes
 * behind it and its size — is pinned in `golden/language-manifest.json` until a
 * change to the catalogue is deliberate.
 */
class LanguageManifestGoldenTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `the published manifest is built from the pipeline's own output`() {
        val first = buildLanguageManifest(DICTIONARIES, DIFFICULTIES)
        val second = buildLanguageManifest(DICTIONARIES, DIFFICULTIES)

        assertEquals(first.canonicalJson(), second.canonicalJson(), "the builder must be deterministic")
        assertEquals(
            golden(),
            publishLanguageManifest(first, assets(), BASE_URL).canonicalJson(),
            "update the golden only when the manifest changes on purpose",
        )
    }

    @Test
    fun `the assembled manifest names exactly the artefacts the run produced`() {
        val assets = assets()
        Files.writeString(
            assets.resolve("manifest-lekto-dictionary-en-fr.json"),
            manifestJson.encodeToString(
                PackManifest(
                    formatVersion = PackFormat.VERSION,
                    provenance = PROVENANCE,
                    stats = PackStats(
                        totalLines = 100,
                        malformedLines = 0,
                        readingEntries = 80,
                        entriesWithTranslation = 51_039,
                        lemmaCount = 51_039,
                        entryCount = 51_039,
                        formCount = 51_039,
                        senseCount = 51_039,
                        translationCount = 51_039,
                    ),
                ),
            ),
        )
        Files.writeString(
            assets.resolve("manifest-lekto-difficulty-en.json"),
            manifestJson.encodeToString(
                DifficultyManifest(
                    formatVersion = DifficultyFormat.VERSION,
                    provenance = PROVENANCE,
                    calibration = DifficultyCalibration(listOf(1, 2, 3, 4, 5, 6), "test", "test"),
                    stats = DifficultyStats(frequencyForms = 100, wordKeys = 90, bandedWords = 80),
                ),
            ),
        )

        assertEquals(
            golden(),
            assembleLanguageManifest(assets, BASE_URL).canonicalJson(),
            "the decomposed run assembles the same manifest from the files it produced",
        )
    }

    /** The compressed assets the run produced, with fixed bytes so the golden's digest is stable. */
    private fun assets(): Path {
        val assets = directory.resolve("out")
        Files.createDirectories(assets)
        Files.write(assets.resolve(difficultyAssetName("en")), "difficulty bytes".encodeToByteArray())
        Files.write(assets.resolve(dictionaryAssetName("en", "fr")), "dictionary bytes!".encodeToByteArray())
        return assets
    }

    private fun golden(): String = requireNotNull(LanguageManifestGoldenTest::class.java.getResourceAsStream(GOLDEN)) {
        "the committed golden must be on the test classpath"
    }.bufferedReader().use { it.readText() }.trimEnd('\n')
}

private val DICTIONARIES = listOf(LanguagePair("en", "fr"))
private val DIFFICULTIES = listOf("en")
private val PROVENANCE = PackProvenance("https://kaikki.org/en.jsonl.gz", "0".repeat(64), "2026-11-01")

private const val BASE_URL: String = "https://github.com/Domingax/lekto-data/releases/download/dictionary-20261101"
private const val GOLDEN: String = "/golden/language-manifest.json"
