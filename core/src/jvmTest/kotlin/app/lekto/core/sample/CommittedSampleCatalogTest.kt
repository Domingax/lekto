package app.lekto.core.sample

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.derivedMastery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The catalogue the build committed (issue #297) is decoded through the app's own
 * reader, so the shipped bytes cannot drift into a shape the first launch refuses.
 * Its maps are recomputed from the difficulty bands it carries with `core`'s own
 * default-mastery rule, so the frozen copy cannot silently disagree with the rule
 * the reader's books use, and every glossary is held to the words the sample
 * highlights.
 */
class CommittedSampleCatalogTest {

    @Test
    fun `the committed catalogue reads as one English sample`() {
        val catalogue = committedCatalogue()

        assertEquals(listOf("en"), catalogue.samples.map(Sample::language))
        assertEquals("sample:en", catalogue.samples.single().identity)
        assertEquals(6, catalogue.samples.single().colourMaps.size)
    }

    @Test
    fun `every map equals the table's band composed with the default-mastery rule`() {
        val sample = committedCatalogue().samples.single()

        CefrLevel.entries.forEach { readerBand ->
            val map = sample.colourMap(readerBand)
            assertEquals(sample.difficulty.keys, map.keys, "the $readerBand map must cover every sample word")
            sample.difficulty.forEach { (word, difficulty) ->
                assertEquals(
                    derivedMastery(difficulty, readerBand),
                    map.getValue(word),
                    "$word at $readerBand must be the table's output, not a hand-written colour",
                )
            }
        }
    }

    @Test
    fun `every glossary covers every word the sample highlights`() {
        val sample = committedCatalogue().samples.single()
        val highlighted = sample.difficulty.keys.filter { word ->
            CefrLevel.entries.any { band -> sample.colourMap(band).getValue(word) != MasteryLevel.KNOWN }
        }

        check(highlighted.isNotEmpty()) { "the sample highlights no word, so the guide shows no colour" }
        sample.glossaries.forEach { (translation, glossary) ->
            val missing = highlighted.filter { word -> glossary.entriesOf(word).isEmpty() }
            check(missing.isEmpty()) { "the $translation glossary misses $missing" }
        }
    }

    @Test
    fun `the sample carries the forms its own tokens need before a pack is installed`() {
        val sample = committedCatalogue().samples.single()

        check(sample.forms.isNotEmpty()) { "an inflected sample word would miss its lemma key on the guide's page" }
        sample.forms.forEach { (surface, word) ->
            check(surface != word) { "a form is stored only when it differs from its normalised surface" }
            check(word in sample.difficulty) { "the form $surface points at a word the sample does not carry" }
        }
    }

    private fun committedCatalogue(): SampleCatalogContent {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/samples/catalogue.json")) {
            "the committed sample catalogue must be on the core classpath"
        }.use { stream -> stream.readBytes() }

        return assertIs<SampleCatalogRead.Read>(readSampleCatalog(bytes)).catalogue
    }
}
