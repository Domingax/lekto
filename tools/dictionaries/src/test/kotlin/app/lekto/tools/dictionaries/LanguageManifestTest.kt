package app.lekto.tools.dictionaries

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The catalogue reflects exactly what a run published: no invention, no omission (issue #145). */
class LanguageManifestTest {

    @Test
    fun `a run that publishes nothing yields an empty manifest`() {
        val manifest = buildLanguageManifest(pack = null, difficulty = null)

        assertEquals(LanguageManifestFormat.VERSION, manifest.formatVersion)
        assertTrue(manifest.languages.isEmpty(), "no artefact means no language, not an invented one")
    }

    @Test
    fun `a run without a difficulty table lists the pack alone and omits the table`() {
        val manifest = buildLanguageManifest(pack = fixturePack(), difficulty = null)

        val language = manifest.languages.single()
        assertEquals("en", language.language)
        assertNull(language.difficulty)
        assertEquals(listOf(ManifestDictionary("fr", "lekto-dictionary-en-fr.sqlite.gz")), language.dictionaries)

        val json = manifest.canonicalJson()
        assertTrue("lekto-dictionary-en-fr.sqlite.gz" in json)
        assertFalse("difficulty" in json, "an unpublished artefact must be absent, not null")
    }

    @Test
    fun `a run without a pack lists the table alone and omits the pack`() {
        val manifest = buildLanguageManifest(pack = null, difficulty = fixtureDifficulty())

        val language = manifest.languages.single()
        assertEquals("en", language.language)
        assertEquals(ManifestArtifact("lekto-difficulty-en.sqlite.gz"), language.difficulty)
        assertTrue(language.dictionaries.isEmpty())

        val json = manifest.canonicalJson()
        assertTrue("lekto-difficulty-en.sqlite.gz" in json)
        assertFalse("dictionaries" in json, "an unpublished pair must be absent, not empty")
    }
}

private fun fixturePack(): PackContent = PackBuilder(FIXTURE_PROVENANCE).build(
    requireNotNull(LanguageManifestTest::class.java.getResourceAsStream("/en-fr-sample.jsonl")).bufferedReader(),
)

private fun fixtureDifficulty(): DifficultyContent = DifficultyBuilder(
    pack = fixturePack(),
    calibration = DifficultyCalibration(listOf(1, 2, 3, 4, 5, 6), "test", "test"),
).build(BufferedReader(StringReader(fixtureFrequency())))

private fun fixtureFrequency(): String =
    requireNotNull(LanguageManifestTest::class.java.getResourceAsStream("/difficulty-frequency-sample.tsv")) {
        "the committed fixture must be on the test classpath"
    }.bufferedReader().use(BufferedReader::readText)

private val FIXTURE_PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")
