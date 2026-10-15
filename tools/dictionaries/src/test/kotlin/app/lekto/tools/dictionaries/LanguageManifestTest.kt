package app.lekto.tools.dictionaries

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The catalogue reflects exactly what a run published: no invention, no omission (issues #145, #161). */
class LanguageManifestTest {

    @Test
    fun `a run that publishes nothing yields an empty manifest`() {
        val manifest = buildLanguageManifest(dictionaries = emptyList(), difficulties = emptyList())

        assertEquals(LanguageManifestFormat.VERSION, manifest.formatVersion)
        assertTrue(manifest.languages.isEmpty(), "no artefact means no language, not an invented one")
    }

    @Test
    fun `a run without a difficulty table lists the pack alone and omits the table`() {
        val manifest = buildLanguageManifest(
            dictionaries = listOf(LanguagePair("en", "fr")),
            difficulties = emptyList(),
        )

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
        val manifest = buildLanguageManifest(dictionaries = emptyList(), difficulties = listOf("en"))

        val language = manifest.languages.single()
        assertEquals("en", language.language)
        assertEquals(ManifestArtifact("lekto-difficulty-en.sqlite.gz"), language.difficulty)
        assertTrue(language.dictionaries.isEmpty())

        val json = manifest.canonicalJson()
        assertTrue("lekto-difficulty-en.sqlite.gz" in json)
        assertFalse("dictionaries" in json, "an unpublished pair must be absent, not empty")
    }

    @Test
    fun `a reading language with several packs lists them all under one entry`() {
        val manifest = buildLanguageManifest(
            dictionaries = listOf(LanguagePair("en", "fr"), LanguagePair("en", "es")),
            difficulties = listOf("en"),
        )

        val language = manifest.languages.single()
        assertEquals("en", language.language)
        assertEquals(
            listOf(
                ManifestDictionary("fr", "lekto-dictionary-en-fr.sqlite.gz"),
                ManifestDictionary("es", "lekto-dictionary-en-es.sqlite.gz"),
            ),
            language.dictionaries,
        )
        assertEquals(ManifestArtifact("lekto-difficulty-en.sqlite.gz"), language.difficulty)
    }

    @Test
    fun `a pair and a difficulty for different languages yield two entries`() {
        val manifest = buildLanguageManifest(
            dictionaries = listOf(LanguagePair("fr", "es")),
            difficulties = listOf("es"),
        )

        assertEquals(listOf("fr", "es"), manifest.languages.map(ManifestLanguage::language))
    }

    @Test
    fun `the committed language set parses into the configured artefacts`() {
        val config = readLanguagesConfig(Files.readAllBytes(Path.of("languages.json")))

        assertEquals(
            listOf(
                LanguagePair("en", "fr"),
                LanguagePair("en", "es"),
            ),
            config.dictionaries,
        )
        assertEquals(listOf("en", "es", "fr"), config.difficulties)
    }
}
