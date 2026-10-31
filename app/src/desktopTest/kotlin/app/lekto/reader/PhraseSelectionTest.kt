package app.lekto.reader

import app.lekto.core.text.WordKey
import app.lekto.core.text.WordToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pure half of phrase selection (issue #87): the range a long-press drag
 * covers and the phrase it cuts out of the chapter, tested on the words
 * themselves rather than through a frame. The rendered gesture has its own
 * semantics test.
 */
@Suppress("MagicNumber") // The word offsets are the fixture's own; naming each would hide the mapping.
class PhraseSelectionTest {

    private val words = listOf(
        WordToken("the", 0, 3, WordKey("en", "the")),
        WordToken("lantern", 4, 11, WordKey("en", "lantern")),
        WordToken("glows", 12, 17, WordKey("en", "glows")),
    )

    @Test
    fun theRangeSpansEveryWordFromAnchorToCurrent() {
        assertEquals(0..17, phraseRangeAt(words, 0, 2))
    }

    @Test
    fun theRangeIsTheSameWhicheverDirectionTheDragGoes() {
        assertEquals(4..17, phraseRangeAt(words, 2, 1))
    }

    @Test
    fun aSingleWordRangeIsThatWord() {
        assertEquals(4..11, phraseRangeAt(words, 1, 1))
    }

    @Test
    fun thePhraseIsCutFromTheChapterAtThePageOffset() {
        val selection = phraseInChapter("the lantern glows", pageStart = 0, range = 4..11)

        assertEquals("lantern", selection.text)
        assertEquals(4..11, selection.range)
    }

    @Test
    fun thePageOffsetLiftsTheRangeToTheChaptersCoordinates() {
        val selection = phraseInChapter("xxxxxthe lantern glows", pageStart = 5, range = 0..3)

        assertEquals("the", selection.text)
        assertEquals(5..8, selection.range)
    }

    @Test
    fun thePhraseCarriesItsContainingSentence() {
        val selection = phraseInChapter("The keeper lit the lantern. Then he waited.", pageStart = 0, range = 19..26)

        assertEquals("lantern", selection.text)
        assertEquals("The keeper lit the lantern.", selection.sentence)
    }

    @Test
    fun aSentenceWithoutATerminatorRunsToTheChaptersEnd() {
        val selection = phraseInChapter("the lantern glows", pageStart = 0, range = 4..11)

        assertEquals("the lantern glows", selection.sentence)
    }

    @Test
    fun theLiveDragIsAlreadyInThePagesCoordinates() {
        assertEquals(4..11, washedRange(drag = 4..11, committed = null, pageStart = 100))
    }

    @Test
    fun theCommittedSelectionIsBroughtOntoThePage() {
        // The panel's range is a chapter range; the wash compares the page's own
        // offsets, so it is moved by the page start (issue #244).
        assertEquals(4..11, washedRange(drag = null, committed = 104..111, pageStart = 100))
    }

    @Test
    fun nothingIsWashedWhenNoRangeIsLive() {
        assertNull(washedRange(drag = null, committed = null, pageStart = 100))
    }
}
