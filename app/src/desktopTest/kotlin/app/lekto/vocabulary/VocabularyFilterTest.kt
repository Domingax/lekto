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
    private val familiar = entry("harbour", MasteryLevel.FAMILIAR)
    private val mastered = entry("lantern", MasteryLevel.MASTERED)
    private val entries = listOf(unknown, familiar, mastered)

    @Test
    fun `a null level keeps every entry`() {
        assertEquals(entries, filterByMastery(entries, null))
    }

    @Test
    fun `a level keeps only the entries at that level`() {
        assertEquals(listOf(familiar), filterByMastery(entries, MasteryLevel.FAMILIAR))
    }

    @Test
    fun `a level nothing is saved at returns nothing`() {
        assertEquals(emptyList(), filterByMastery(entries, MasteryLevel.KNOWN))
    }

    @Test
    fun `the mastery filter and the search compose`() {
        assertEquals(listOf(mastered), filterByMastery(searchVocabulary(entries, "lant"), MasteryLevel.MASTERED))
        assertEquals(emptyList(), filterByMastery(searchVocabulary(entries, "lant"), MasteryLevel.FAMILIAR))
    }

    private fun entry(surface: String, mastery: MasteryLevel): VocabularyEntry =
        VocabularyEntry(WordKey("en", surface), surface, mastery = mastery)
}
