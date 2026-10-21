package app.lekto.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.lekto.core.MasteryLevel
import app.lekto.theme.Dark
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The one rule the mastery palette cannot break (`docs/design.md` §2.2,
 * ADR-0036): a coloured word is 18 sp of running text, so every hue is at least
 * 4.5:1 against its own paper. A measured test, not a comment, because the
 * delivered palette failed three of its four levels and no golden can tell a
 * readable hue from a pretty one.
 */
@Suppress("MagicNumber") // The AA floor and WCAG's luminance offsets are the rule's own constants.
class MasteryPaletteContrastTest {

    private data class Paper(val name: String, val theme: ColorScheme)

    private val papers = listOf(
        Paper("Light", Light),
        Paper("Dark", Dark),
        Paper("Sepia", Sepia),
    )

    @Test
    fun everyHighlightedLevelMeetsAaAgainstItsOwnPaper() {
        papers.forEach { paper ->
            val ramp = MasteryPalette.of(paper.theme)
            MasteryLevel.entries.filter { level -> level.highlighted }.forEach { level ->
                val ratio = contrast(level.readerColor(ramp), paper.theme.background)
                assertTrue(
                    ratio >= 4.5f,
                    "${paper.name} ${level.name}: $ratio:1 against ${paper.theme.background}",
                )
            }
        }
    }

    @Test
    fun knownStaysColourlessOnEveryPaper() {
        papers.forEach { paper ->
            assertTrue(
                MasteryLevel.KNOWN.readerColor(MasteryPalette.of(paper.theme)) == Color.Unspecified,
                "${paper.name} paints a known word",
            )
        }
    }

    /** WCAG 2.1's relative-luminance contrast ratio. */
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (max(first, second) + 0.05f) / (min(first, second) + 0.05f)
    }
}
