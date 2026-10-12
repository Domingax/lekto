package app.lekto.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The vocabulary list's mastery filter (issue #66): `null` keeps every entry, and
 * a level keeps only the entries saved at that level.
 */
class VocabularyFilterTest {

    private val unknown = entry("on", MasteryLevel.UNKNOWN)
    private val recognized = entry("harbour", MasteryLevel.RECOGNIZED)
    private val learned = entry("lantern", MasteryLevel.LEARNED)
    private val entries = listOf(unknown, recognized, learned)

    @Test
    fun `a null level keeps every entry`() {
        assertEquals(entries, filterByMastery(entries, null))
    }

    @Test
    fun `a level keeps only the entries at that level`() {
        assertEquals(listOf(recognized), filterByMastery(entries, MasteryLevel.RECOGNIZED))
    }

    @Test
    fun `a level nothing is saved at returns nothing`() {
        assertEquals(emptyList(), filterByMastery(entries, MasteryLevel.KNOWN))
    }

    @Test
    fun `the mastery filter and the search compose`() {
        assertEquals(listOf(learned), filterByMastery(searchVocabulary(entries, "lant"), MasteryLevel.LEARNED))
        assertEquals(emptyList(), filterByMastery(searchVocabulary(entries, "lant"), MasteryLevel.RECOGNIZED))
    }

    private fun entry(surface: String, mastery: MasteryLevel): VocabularyEntry =
        VocabularyEntry(WordKey("en", surface), surface, mastery = mastery)
}
