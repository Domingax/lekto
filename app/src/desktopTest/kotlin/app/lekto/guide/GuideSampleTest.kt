package app.lekto.guide

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.text.WordKey
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.guideSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sample-as-the-guide-reads-it helpers (issue #277): the first paragraph
 * becomes the reader's one-page document, the sample's own colour map paints it,
 * its forms are the lemma lookup, and the four notes name one word per
 * highlighted **Mastery level** with the sample's meaning. Everything is pure,
 * so the screens' layout and the demonstration's content are proved apart.
 */
@Suppress("TooManyFunctions") // One behaviour per helper: the page, the notes, the lookup and the save.
class GuideSampleTest {

    private val segmenter = WhitespaceTextSegmenter()
    private val sample = guideSample()

    @Test
    fun theDemonstrationDefaultsToTheLowestBand() {
        assertEquals(CefrLevel.A1, GUIDE_DEFAULT_BAND, "A1 colours the most words before any band is chosen")
    }

    @Test
    fun aSavedLevelOverridesTheDemonstratedWordsMastery() {
        val saved = WordKey("en", "calm") to MasteryLevel.KNOWN

        val mastery = guideMastery(sample, CefrLevel.A1, plain = false, saved = saved)

        assertEquals(MasteryLevel.KNOWN, mastery.levelOf(WordKey("en", "calm")), "level 4 is normal ink")
        assertEquals(MasteryLevel.UNKNOWN, mastery.levelOf(WordKey("en", "fisherman")), "the other words stay put")
    }

    @Test
    fun theDottedRulesFollowTheSaveThroughTheDocument() {
        val key = WordKey("en", "fisherman")

        val plain = guideDocument(sample, CefrLevel.A1, plain = false, segmenter = segmenter)
        val saved = guideDocument(
            sample,
            CefrLevel.A1,
            plain = false,
            segmenter = segmenter,
            saved = key to MasteryLevel.KNOWN,
        )

        assertEquals(MasteryLevel.UNKNOWN, plain.renderer.mastery.levelOf(key))
        assertEquals(MasteryLevel.KNOWN, saved.renderer.mastery.levelOf(key))
        assertTrue(saved.masteryRevision != plain.masteryRevision, "the page rebuilds for the save")
    }

    @Test
    fun theFirstParagraphIsTheOnePageDocument() {
        val document = guideDocument(sample, CefrLevel.B1, plain = false, segmenter = segmenter)

        assertEquals("A Guide Sample", document.text.title)
        assertEquals(1, document.text.blocks.size)
        assertTrue(document.text.blocks.single().text.startsWith("The old fisherman"))
    }

    @Test
    fun theBeatPicksTheParagraphThatCoversTheMostLevels() {
        val twoParagraphs = sample.copy(
            paragraphs = listOf(
                "The old boat.",
                "The old fisherman rowed his small boat across the calm water.",
            ),
        )

        val notes = guideNotes(twoParagraphs, CefrLevel.A1, segmenter, translationLanguage = "fr")
        val document = guideDocument(twoParagraphs, CefrLevel.A1, plain = false, segmenter = segmenter)

        assertEquals(
            listOf("fisherman", "old", "calm", "rowed"),
            notes.map { note -> note.surface },
            "the paragraph that shows all four levels is the one demonstrated",
        )
        assertEquals(
            "The old fisherman rowed his small boat across the calm water.",
            document.text.blocks.single().text,
            "the page shows the same paragraph its notes explain",
        )
    }

    @Test
    fun anEquallyRichParagraphKeepsTheEarlierOne() {
        val tie = sample.copy(
            paragraphs = listOf(
                "old fisherman rowed calm",
                "calm old fisherman rowed",
            ),
        )

        val document = guideDocument(tie, CefrLevel.A1, plain = false, segmenter = segmenter)

        assertEquals("old fisherman rowed calm", document.text.blocks.single().text)
    }

    @Test
    fun theSampleOwnColourMapPaintsThePageAtTheBand() {
        val mastery = guideMastery(sample, CefrLevel.B1, plain = false)

        assertEquals(MasteryLevel.UNKNOWN, mastery.levelOf(WordKey("en", "fisherman")))
        assertEquals(MasteryLevel.FAMILIAR, mastery.levelOf(WordKey("en", "calm")))
        assertEquals(MasteryLevel.KNOWN, mastery.levelOf(WordKey("en", "boat")))
    }

    @Test
    fun thePlainBeatPaintsNothingAtAll() {
        val mastery = guideMastery(sample, CefrLevel.B1, plain = true)

        assertEquals(MasteryLevel.KNOWN, mastery.levelOf(WordKey("en", "fisherman")))
    }

    @Test
    fun theSampleFormsAreTheLemmaLookup() {
        val lemmas = guideLemmas(sample)

        assertEquals("row", lemmas.lemmaOf("rowed", "en"))
        assertNull(lemmas.lemmaOf("boats", "en"))
    }

    @Test
    fun theFourNotesNameOneWordPerHighlightedLevelInLevelOrder() {
        val notes = guideNotes(sample, CefrLevel.B1, segmenter, translationLanguage = "fr")

        assertEquals(
            listOf(
                MasteryLevel.UNKNOWN,
                MasteryLevel.RECOGNIZED,
                MasteryLevel.FAMILIAR,
                MasteryLevel.LEARNED,
            ),
            notes.map { note -> note.level },
        )
        assertEquals(listOf("fisherman", "old", "calm", "rowed"), notes.map { note -> note.surface })
    }

    @Test
    fun aNoteCarriesTheWordTheMeaningAndItsSentence() {
        val note = guideNotes(sample, CefrLevel.B1, segmenter, translationLanguage = "fr").first()

        assertEquals(WordKey("en", "fisherman"), note.key)
        assertEquals("pêcheur", note.meaning)
        assertEquals("The old fisherman rowed his small boat across the calm water.", note.context)
    }

    @Test
    fun theNotesFallBackToAnotherGlossaryWhenTheChosenOneIsAbsent() {
        val notes = guideNotes(sample, CefrLevel.B1, segmenter, translationLanguage = "de")

        assertEquals("pêcheur", notes.first().meaning)
    }

    @Test
    fun theTapIsAnsweredFromTheSampleGlossary() {
        val note = guideNotes(sample, CefrLevel.B1, segmenter, translationLanguage = "fr").first()

        val lookup = guideLookup(sample, note, translationLanguage = "fr")

        assertTrue(lookup is WordLookup.Found)
        val entry = lookup.entries.firstOrNull()
        assertNotNull(entry)
        assertEquals("fisherman", entry.lemma)
        assertEquals("noun", entry.partOfSpeech)
        assertEquals("/ˈfɪʃəmən/", entry.pronunciation)
        assertEquals("pêcheur", entry.senses.single().translations.single())
    }

    @Test
    fun aBandWithoutAFourthColourCarriesOnlyTheWordsItHas() {
        val sparse = sample.copy(
            colourMaps = CefrLevel.entries.associateWith { mapOf("fisherman" to MasteryLevel.UNKNOWN) },
        )

        val notes = guideNotes(sparse, CefrLevel.B1, segmenter, translationLanguage = "fr")

        assertEquals(1, notes.size)
    }
}
