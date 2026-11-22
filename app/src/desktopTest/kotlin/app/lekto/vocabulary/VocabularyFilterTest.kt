package app.lekto.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The vocabulary list's mastery filter (issue #66): `null` keeps every entry, and
 * a level keeps only the entries saved at that level. The book filter narrows by
 * the entry's own history (issue #280): `null` keeps every entry, and a book id
 * keeps the words whose history names it.
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

    @Test
    fun `a null book keeps every entry`() {
        assertEquals(entries, filterByBook(entries, null))
    }

    @Test
    fun `a book keeps only the entries whose history names it`() {
        val fromBook = learned.copy(bookIds = listOf("b1"))
        val fromAnother = recognized.copy(bookIds = listOf("b2"))

        assertEquals(listOf(fromBook), filterByBook(listOf(fromBook, fromAnother), "b1"))
    }

    @Test
    fun `a word saved from two books is listed under both`() {
        val shared = learned.copy(bookIds = listOf("b1", "b2"))

        assertEquals(listOf(shared), filterByBook(listOf(shared), "b1"))
        assertEquals(listOf(shared), filterByBook(listOf(shared), "b2"))
    }

    @Test
    fun `the book filter and the level filter compose`() {
        val fromBook = learned.copy(bookIds = listOf("b1"))
        val knownFromBook = recognized.copy(bookIds = listOf("b1"), mastery = MasteryLevel.KNOWN)
        val book = listOf(fromBook, knownFromBook)

        assertEquals(
            listOf(knownFromBook),
            filterByMastery(filterByBook(book, "b1"), MasteryLevel.KNOWN),
        )
    }

    private fun entry(surface: String, mastery: MasteryLevel): VocabularyEntry =
        VocabularyEntry(WordKey("en", surface), surface, mastery = mastery)
}
