package app.lekto.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The vocabulary list's search (issue #23): a blank query keeps everything, and a
 * non-blank one finds a word by any of the four things the list shows — the
 * spelling, the lemma it is keyed by, its translation and its context sentence —
 * case-insensitively.
 */
class VocabularySearchTest {

    private val lantern = VocabularyEntry(
        key = WordKey("en", "lantern"),
        surface = "lantern",
        translation = "lanterne",
        contextSentence = "The lantern burned all night.",
        mastery = MasteryLevel.MASTERED,
    )
    private val manger = VocabularyEntry(
        key = WordKey("fr", "manger"),
        surface = "mangeais",
        translation = "to eat",
        contextSentence = "Je mangeais du pain.",
        mastery = MasteryLevel.FAMILIAR,
    )
    private val entries = listOf(lantern, manger)

    @Test
    fun `a blank query returns every entry`() {
        assertEquals(entries, searchVocabulary(entries, ""))
        assertEquals(entries, searchVocabulary(entries, "   "))
    }

    @Test
    fun `the spelling matches case-insensitively`() {
        assertEquals(listOf(lantern), searchVocabulary(entries, "LANTERN"))
    }

    @Test
    fun `the lemma finds an inflected surface`() {
        assertEquals(listOf(manger), searchVocabulary(entries, "manger"))
    }

    @Test
    fun `the translation matches`() {
        assertEquals(listOf(manger), searchVocabulary(entries, "eat"))
    }

    @Test
    fun `the context sentence matches`() {
        assertEquals(listOf(lantern), searchVocabulary(entries, "burned"))
    }

    @Test
    fun `a query that matches nothing returns nothing`() {
        assertEquals(emptyList(), searchVocabulary(entries, "zzzz"))
    }
}
