package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Completing a **Language manifest** for publication (issue #305, ADR-0047): each
 * artefact gains the address the app follows, the SHA-256 of the bytes behind it
 * and its size; an artefact the run did not produce is refused rather than
 * advertised, and one it never built stays absent.
 */
class LanguageManifestPublicationTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `each published asset gains its address, digest and size`() {
        val assets = directory.resolve("out")
        Files.createDirectories(assets)
        Files.write(assets.resolve(DIFFICULTY), "difficulty bytes".encodeToByteArray())
        Files.write(assets.resolve(DICTIONARY), "dictionary bytes!".encodeToByteArray())

        val published = publishLanguageManifest(manifest(), assets, BASE_URL)
        val language = published.languages.single()

        assertEquals("$BASE_URL/$DIFFICULTY", language.difficulty?.url)
        assertEquals("cac53ac8da6e017b8ccae92557d471671e16fc29a7fe819fb7330ec91f4edb96", language.difficulty?.sha256)
        assertEquals(16L, language.difficulty?.sizeBytes)
        assertEquals("$BASE_URL/$DICTIONARY", language.dictionaries.single().url)
        assertEquals(
            "a0482b5a9dc3973ddba625ec590e893049361dec73be3e844e218121f8c12102",
            language.dictionaries.single().sha256,
        )
        assertEquals(17L, language.dictionaries.single().sizeBytes)
    }

    @Test
    fun `a manifest that names an asset the run did not produce is refused`() {
        val assets = directory.resolve("out")
        Files.createDirectories(assets)
        Files.write(assets.resolve(DIFFICULTY), "difficulty bytes".encodeToByteArray())

        val error = assertFailsWith<IllegalArgumentException> {
            publishLanguageManifest(manifest(), assets, BASE_URL)
        }

        assertEquals(
            "the manifest names '$DICTIONARY', which this run did not produce in $assets",
            error.message,
        )
    }

    @Test
    fun `an artefact the run never published stays absent`() {
        val assets = directory.resolve("out")
        Files.createDirectories(assets)
        Files.write(assets.resolve(DICTIONARY), "dictionary bytes!".encodeToByteArray())
        val withoutDifficulty = buildLanguageManifest(
            dictionaries = listOf(LanguagePair("en", "fr")),
            difficulties = emptyList(),
        )

        val published = publishLanguageManifest(withoutDifficulty, assets, BASE_URL)

        assertNull(published.languages.single().difficulty)
    }

    @Test
    fun `a trailing slash on the base url does not double`() {
        val assets = directory.resolve("out")
        Files.createDirectories(assets)
        Files.write(assets.resolve(DIFFICULTY), "difficulty bytes".encodeToByteArray())
        Files.write(assets.resolve(DICTIONARY), "dictionary bytes!".encodeToByteArray())

        val published = publishLanguageManifest(manifest(), assets, "$BASE_URL/")

        assertEquals("$BASE_URL/$DIFFICULTY", published.languages.single().difficulty?.url)
    }

    private fun manifest(): LanguageManifest = buildLanguageManifest(
        dictionaries = listOf(LanguagePair("en", "fr")),
        difficulties = listOf("en"),
    )

    private companion object {
        const val BASE_URL = "https://data.test/releases/download/dictionary-20261101"
        val DIFFICULTY: String = difficultyAssetName("en")
        val DICTIONARY: String = dictionaryAssetName("en", "fr")
    }
}
