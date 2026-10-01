package app.lekto.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MasteryLevelTest {

    @Test
    fun levelsRunFromUnknownToKnown() {
        assertEquals(listOf(0, 1, 2, 3, 4), MasteryLevel.entries.map { level -> level.level })
    }

    @Test
    fun everyLevelButKnownIsHighlighted() {
        val highlighted = MasteryLevel.entries.filter { level -> level.highlighted }.map { level -> level.level }
        assertEquals(listOf(0, 1, 2, 3), highlighted)
        assertFalse(MasteryLevel.KNOWN.highlighted)
    }

    @Test
    fun aLookupAnswersForAWordInItsLanguage() {
        val lookup = MasteryLookup { word, _ -> if (word == "manger") MasteryLevel.KNOWN else MasteryLevel.UNKNOWN }
        assertEquals(MasteryLevel.KNOWN, lookup.levelOf("manger", "fr"))
        assertTrue(lookup.levelOf("mangeais", "fr").highlighted)
    }
}
