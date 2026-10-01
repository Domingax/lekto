package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import app.lekto.core.MasteryLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The mastery palette's contract: the four highlighted levels are visually
 * distinct and stand out from normal text, and a known word is painted as plain
 * text. The screenshot golden pins the exact colours and layout; this pins the
 * rule, which a golden update could otherwise relax silently.
 */
class MasteryPaletteTest {

    @Test
    fun everyHighlightedLevelHasItsOwnColour() {
        val colours = MasteryLevel.entries.filter { it.highlighted }.map { it.readerColor() }

        assertEquals(colours.size, colours.toSet().size, "each highlighted level needs a distinct colour")
        colours.forEach { colour -> assertNotEquals(Color.Unspecified, colour) }
    }

    @Test
    fun knownRendersAsNormalText() {
        assertEquals(Color.Unspecified, MasteryLevel.KNOWN.readerColor())
        assertEquals(TextDecoration.None, MasteryLevel.KNOWN.readerDecoration())
    }

    @Test
    fun highlightedLevelsAreUnderlined() {
        MasteryLevel.entries.filter { it.highlighted }.forEach { level ->
            assertEquals(TextDecoration.Underline, level.readerDecoration())
        }
    }
}
