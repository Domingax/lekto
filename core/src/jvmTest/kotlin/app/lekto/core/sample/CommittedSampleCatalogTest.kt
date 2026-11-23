package app.lekto.core.sample

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.derivedMastery
import app.lekto.core.language.SampleRoster
import app.lekto.core.text.IcuTextSegmenter
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.normaliseSurface
import app.lekto.core.text.wordKeyOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun `the embedded roster is the committed catalogue's languages`() {
        assertEquals(
            committedCatalogue().samples.map(Sample::language),
            SampleRoster.LANGUAGES,
            "the roster is generated from the sample catalogue: a sample without a roster entry is never offered",
        )
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
    fun `every language's A1 paragraph shows the guide's four notes`() {
        committedCatalogue().samples.forEach { sample ->
            val levels = demonstratedLevels(sample, CefrLevel.A1).distinct().toSet()

            assertEquals(
                MasteryLevel.entries.filter(MasteryLevel::highlighted).toSet(),
                levels,
                "${sample.language}: A1 must carry a word at every highlighted level",
            )
        }
    }

    @Test
    fun `every band from A1 to B2 keeps three coloured words over two levels`() {
        committedCatalogue().samples.forEach { sample ->
            SPREAD_BANDS.forEach { band ->
                val levels = demonstratedLevels(sample, band)

                assertTrue(
                    levels.size >= MIN_COLOURED_WORDS,
                    "${sample.language} at $band must keep $MIN_COLOURED_WORDS coloured words, " +
                        "not ${levels.size}",
                )
                assertTrue(
                    levels.distinct().size >= MIN_HIGHLIGHTED_LEVELS,
                    "${sample.language} at $band must span $MIN_HIGHLIGHTED_LEVELS levels, " +
                        "not ${levels.distinct().size}",
                )
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

    /**
     * The highlighted levels [sample]'s demonstrated paragraph shows at [band]:
     * the paragraph whose colours cover the most distinct levels, ties keeping
     * the earlier — the rule `app`'s `guideParagraph` applies (issue #277).
     */
    private fun demonstratedLevels(sample: Sample, band: CefrLevel): List<MasteryLevel> {
        val lemmas = LemmaLookup { surface, _ -> sample.forms[normaliseSurface(surface)] }
        return sample.paragraphs
            .map { paragraph -> levelsIn(sample, paragraph, band, lemmas) }
            .maxByOrNull { levels -> levels.distinct().size }
            .orEmpty()
    }

    private fun levelsIn(sample: Sample, paragraph: String, band: CefrLevel, lemmas: LemmaLookup): List<MasteryLevel> =
        SEGMENTER.words(paragraph, sample.language)
            .map { span ->
                sample.colourMap(band)[wordKeyOf(span.surface, sample.language, lemmas).key] ?: MasteryLevel.KNOWN
            }
            .filter(MasteryLevel::highlighted)

    private fun committedCatalogue(): SampleCatalogContent {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/samples/catalogue.json")) {
            "the committed sample catalogue must be on the core classpath"
        }.use { stream -> stream.readBytes() }

        return assertIs<SampleCatalogRead.Read>(readSampleCatalog(bytes)).catalogue
    }

    private companion object {
        /** The reading languages this build ships a sample for, in the catalogue's order. */
        val LANGUAGES: List<String> = listOf("de", "en", "es", "fr", "it", "ja", "pt", "tr")

        /** The bands the guide's level screen must have something to recolour at (issue #324). */
        val SPREAD_BANDS: List<CefrLevel> = listOf(CefrLevel.A1, CefrLevel.A2, CefrLevel.B1, CefrLevel.B2)

        /** The fewest coloured words the demonstrated paragraph keeps from A1 to B2. */
        const val MIN_COLOURED_WORDS: Int = 3

        /** The fewest distinct highlighted levels those words span. */
        const val MIN_HIGHLIGHTED_LEVELS: Int = 2

        /** The reader's own production segmenter, so a paragraph is split as the page splits it. */
        val SEGMENTER: IcuTextSegmenter = IcuTextSegmenter()
    }
}
