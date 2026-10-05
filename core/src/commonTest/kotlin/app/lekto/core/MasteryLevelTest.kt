package app.lekto.core

import app.lekto.core.text.WordKey
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
    fun aLookupAnswersForAWordsIdentity() {
        val lookup = MasteryLookup { key -> if (key.key == "manger") MasteryLevel.KNOWN else MasteryLevel.UNKNOWN }

        assertEquals(MasteryLevel.KNOWN, lookup.levelOf(WordKey("fr", "manger")))
        assertTrue(lookup.levelOf(WordKey("fr", "mangeais")).highlighted)
    }
}
