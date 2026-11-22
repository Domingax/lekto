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
class GuideSampleTest {

    private val segmenter = WhitespaceTextSegmenter()
    private val sample = guideSample()

    @Test
    fun theFirstParagraphIsTheOnePageDocument() {
        val document = guideDocument(sample, CefrLevel.B1, plain = false, segmenter = segmenter)

        assertEquals("A Guide Sample", document.text.title)
        assertEquals(1, document.text.blocks.size)
        assertTrue(document.text.blocks.single().text.startsWith("The old fisherman"))
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
        val sparse = sample.copy(colourMaps = CefrLevel.entries.associateWith { mapOf("fisherman" to MasteryLevel.UNKNOWN) })

        val notes = guideNotes(sparse, CefrLevel.B1, segmenter, translationLanguage = "fr")

        assertEquals(1, notes.size)
    }
}
