package app.lekto.tools.dictionaries

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The coverage pass mirrors the pack's filters — one reading language, normalised
 * single-token lemmas, entries merged on `(language, lemma, part of speech)` — so
 * a pair's lemma count is the count the real build would produce (issue #302).
 */
class CoverageTest {

    @Test
    fun `counts each target once per entry and merges duplicate keys`() {
        val coverage = measure(fixtureText(), "fr", setOf("de", "en", "it"))

        assertEquals(7, coverage.totalLines)
        assertEquals(1, coverage.malformedLines)
        assertEquals(5, coverage.readingEntries)

        val de = coverage.pairs.single { it.translation == "de" }
        assertEquals(3, de.entriesWithTranslation, "two German words on one entry still count once")
        assertEquals(2, de.lemmas, "chat appears as a noun and a verb but is one lemma")
        assertEquals(3, de.entries)
    }

    @Test
    fun `normalises the lemma and keeps one row per requested target`() {
        val coverage = measure(fixtureText(), "fr", setOf("de", "en", "it", "es"))

        val en = coverage.pairs.single { it.translation == "en" }
        assertEquals(2, en.entriesWithTranslation, "chat and Chat both carry an English translation")
        assertEquals(1, en.lemmas, "Chat merges into chat")

        val es = coverage.pairs.single { it.translation == "es" }
        assertEquals(0, es.entriesWithTranslation, "an empty pair is reported, not omitted")
        assertEquals(0, es.lemmas)
        assertEquals(0, es.entries)
    }

    @Test
    fun `drops multi-word headwords and other languages' entries like the pack does`() {
        val coverage = measure(fixtureText(), "fr", setOf("de"))

        val de = coverage.pairs.single()
        assertEquals(3, de.entriesWithTranslation, "avoir faim is multi-word, house is English")
        assertEquals(listOf("de"), coverage.pairs.map(PairCoverage::translation))
    }

    @Test
    fun `labels an edition whose entries carry another language's code`() {
        val entry = """{"word":"chat","lang_code":"en","translations":[{"lang_code":"fr","word":"chat"}]}"""
        val coverage = CoverageBuilder("en", setOf("fr"), edition = "simple")
            .build(StringReader(entry).buffered())

        assertEquals("simple", coverage.edition)
        assertEquals("en", coverage.language)
        assertEquals(1, coverage.pairs.single().lemmas)
    }

    @Test
    fun `folds Mandarin translations into the Chinese target`() {
        val entry = """{"word":"cat","lang_code":"en",""" +
            """"translations":[{"lang_code":"cmn","word":"猫"},{"lang_code":"zh","word":"貓"}]}"""
        val coverage = CoverageBuilder("en", setOf("cmn")).build(StringReader(entry).buffered())

        val zh = coverage.pairs.single()
        assertEquals("zh", zh.translation, "a cmn target is the app's zh")
        assertEquals(1, zh.entriesWithTranslation, "both rows are one entry")
        assertEquals(1, zh.lemmas)
    }

    @Test
    fun `ignores translations into a language outside the measured targets`() {
        val coverage = measure(fixtureText(), "fr", setOf("it"))

        assertEquals(1, coverage.pairs.single().entriesWithTranslation)
        assertEquals(5, coverage.readingEntries, "the pass still reads every French entry")
    }
}

private fun measure(jsonl: String, reading: String, targets: Set<String>): EditionCoverage =
    CoverageBuilder(reading, targets).build(StringReader(jsonl).buffered())

private fun fixtureText(): String = """
    |{"word":"chat","lang_code":"fr","pos":"noun","translations":[{"lang_code":"de","word":"Katze"},{"lang_code":"en","word":"cat"}]}
    |{"word":"chat","lang_code":"fr","pos":"verb","translations":[{"lang_code":"de","word":"plaudern"}]}
    |{"word":"chien","lang_code":"fr","pos":"noun","translations":[{"lang_code":"de","word":"Hund"},{"lang_code":"de","word":"Hündchen"},{"lang_code":"de","word":" "},{"lang_code":"it","word":"cane"}]}
    |{"word":"avoir faim","lang_code":"fr","pos":"verb","translations":[{"lang_code":"de","word":"Hunger haben"}]}
    |{"word":"house","lang_code":"en","pos":"noun","translations":[{"lang_code":"de","word":"Haus"}]}
    |{"word":"Chat","lang_code":"fr","pos":"noun","translations":[{"lang_code":"en","word":"cat"}]}
    |{"word":"x","lang_code":"fr",
""".trimMargin()
