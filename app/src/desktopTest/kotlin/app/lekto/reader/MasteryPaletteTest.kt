package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import app.lekto.core.MasteryLevel
import app.lekto.theme.Dark
import app.lekto.theme.InkOnLightFill
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import app.lekto.theme.contentColorOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The mastery palette's contract: each paper's four highlighted levels are
 * visually distinct and stand out from normal text, a known word is painted as
 * plain text, and the ramp follows the paper. The screenshot goldens pin the
 * exact colours and layout; these assertions pin the rules a golden update could
 * otherwise relax silently.
 */
@Suppress("MagicNumber") // The 14 % wash and its 8-bit tolerance are the rule's own numbers.
class MasteryPaletteTest {

    private val papers = listOf(Light, Dark, Sepia)

    @Test
    fun everyHighlightedLevelHasItsOwnColourOnEveryPaper() {
        papers.forEach { theme ->
            val ramp = MasteryPalette.of(theme)
            val colours = MasteryLevel.entries.filter { it.highlighted }.map { it.readerColor(ramp) }

            assertEquals(colours.size, colours.toSet().size, "each highlighted level needs a distinct colour")
            colours.forEach { colour -> assertNotEquals(Color.Unspecified, colour) }
        }
    }

    @Test
    fun theRampFollowsThePaper() {
        assertEquals(MasteryPalette.Light, MasteryPalette.of(Light))
        assertEquals(MasteryPalette.Dark, MasteryPalette.of(Dark))
        assertEquals(MasteryPalette.Sepia, MasteryPalette.of(Sepia))
    }

    @Test
    fun anUnrecognisedPaperTakesTheRampItsLightnessNeeds() {
        assertEquals(MasteryPalette.Dark, MasteryPalette.ofPaper(Color(0xFF101010)))
        assertEquals(MasteryPalette.Light, MasteryPalette.ofPaper(Color(0xFFF0EDE8)))
    }

    @Test
    fun knownRendersAsNormalText() {
        papers.forEach { theme ->
            val ramp = MasteryPalette.of(theme)
            assertEquals(Color.Unspecified, MasteryLevel.KNOWN.readerColor(ramp))
        }
        assertEquals(TextDecoration.None, MasteryLevel.KNOWN.readerDecoration())
    }

    @Test
    fun highlightedLevelsAreUnderlined() {
        MasteryLevel.entries.filter { it.highlighted }.forEach { level ->
            assertEquals(TextDecoration.Underline, level.readerDecoration())
        }
    }

    @Test
    fun aFilledChipGetsAForegroundThatReadsOnItsColour() {
        // The light papers' hues are dark enough to need white; the dark paper's
        // are lifted, so near-black reads better on every one of them.
        listOf(Light, Sepia).forEach { theme ->
            val ramp = MasteryPalette.of(theme)
            MasteryLevel.entries.filter { it.highlighted }.forEach { level ->
                assertEquals(Color.White, contentColorOn(level.readerColor(ramp)), "$theme ${level.name}")
            }
        }
        listOf(MasteryLevel.UNKNOWN, MasteryLevel.RECOGNIZED, MasteryLevel.FAMILIAR, MasteryLevel.LEARNED)
            .forEach { level ->
                val colour = contentColorOn(level.readerColor(MasteryPalette.Dark))
                assertEquals(InkOnLightFill, colour, "Dark ${level.name}")
            }
    }

    @Test
    fun theSelectionWashIsTheThemeInkAt14Percent() {
        papers.forEach { theme ->
            assertEquals(theme.onBackground.copy(alpha = SELECTION_WASH_ALPHA), selectionHighlight(theme.onBackground))
            // Compose quantises alpha to 8 bits, so the 14 % is within one step.
            assertEquals(0.14f, selectionHighlight(theme.onBackground).alpha, 1f / 255f)
        }
    }
}
