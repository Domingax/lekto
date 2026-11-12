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
 * default-mastery rule, so each frozen copy cannot silently disagree with the rule
 * the reader's books use, and every glossary is held to the words the sample
 * highlights.
 */
class CommittedSampleCatalogTest {

    @Test
    fun `the committed catalogue reads as the eight shipped samples`() {
        val catalogue = committedCatalogue()

        assertEquals(LANGUAGES, catalogue.samples.map(Sample::language))
        catalogue.samples.forEach { sample ->
            assertEquals(6, sample.colourMaps.size, "${sample.language} must carry six maps")
            assertEquals(Sample.identityOf(sample.language), sample.identity)
        }
    }

    @Test
    fun `every map equals the table's band composed with the default-mastery rule`() {
        committedCatalogue().samples.forEach { sample ->
            CefrLevel.entries.forEach { readerBand ->
                val map = sample.colourMap(readerBand)
                assertEquals(
                    sample.difficulty.keys,
                    map.keys,
                    "${sample.language}'s $readerBand map must cover every word",
                )
                sample.difficulty.forEach { (word, difficulty) ->
                    assertEquals(
                        derivedMastery(difficulty, readerBand),
                        map.getValue(word),
                        "${sample.language}'s $word at $readerBand must be the table's output, " +
                            "not a hand-written colour",
                    )
                }
            }
        }
    }

    @Test
    fun `every glossary covers every word its sample highlights`() {
        committedCatalogue().samples.forEach { sample ->
            val highlighted = sample.difficulty.keys.filter { word ->
                CefrLevel.entries.any { band -> sample.colourMap(band).getValue(word) != MasteryLevel.KNOWN }
            }

            check(highlighted.isNotEmpty()) { "${sample.language} highlights no word, so the guide shows no colour" }
            sample.glossaries.forEach { (translation, glossary) ->
                val missing = highlighted.filter { word -> glossary.entriesOf(word).isEmpty() }
                check(missing.isEmpty()) { "${sample.language}→$translation glossary misses $missing" }
            }
        }
    }

    @Test
    fun `each sample carries the forms its own tokens need before a pack is installed`() {
        committedCatalogue().samples.forEach { sample ->
            sample.forms.forEach { (surface, word) ->
                check(surface != word) { "${sample.language}: a form is stored only when it differs from its surface" }
                check(word in sample.difficulty) {
                    "${sample.language}: the form $surface points at a word the sample does not carry"
                }
            }
        }
        check(committedCatalogue().samples.any { sample -> sample.forms.isNotEmpty() }) {
            "an inflected sample word would miss its lemma key on the guide's page"
        }
    }

    private fun committedCatalogue(): SampleCatalogContent {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/samples/catalogue.json")) {
            "the committed sample catalogue must be on the core classpath"
        }.use { stream -> stream.readBytes() }

        return assertIs<SampleCatalogRead.Read>(readSampleCatalog(bytes)).catalogue
    }

    private companion object {
        /** The reading languages this build ships a sample for, in the catalogue's order. */
        val LANGUAGES: List<String> = listOf("de", "en", "es", "fr", "it", "ja", "pt", "tr")
    }
}
