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

/** The AA bar a coloured word clears against its own paper (`docs/design.md` §2.2). */
private const val MINIMUM_PAPER_CONTRAST: Float = 4.5f

/** The bar two adjacent levels clear against each other (`docs/design.md` §2.2). */
private const val MINIMUM_ADJACENT_CONTRAST: Float = 1.35f

/**
 * The one rule the mastery palette cannot break (`docs/design.md` §2.2,
 * ADR-0036): a coloured word is 18 sp of running text, so every hue is at least
 * 4.5:1 against its own paper. A second rule is the reader's actual comparison:
 * two adjacent levels must also differ from each other, or the page shows one
 * blot at four shades. Measured tests, not comments, because the delivered
 * palette failed three of its four levels and no golden can tell a readable hue
 * from a pretty one.
 */
@Suppress("MagicNumber") // The two bars and WCAG's luminance offsets are the rule's own constants.
class MasteryPaletteContrastTest {

    private data class Paper(val name: String, val theme: ColorScheme)

    private val papers = listOf(
        Paper("Light", Light),
        Paper("Dark", Dark),
        Paper("Sepia", Sepia),
    )

    @Test
    fun everyHighlightedLevelMeetsAaAgainstItsOwnPaper() {
        forEachPaper { paper, ramp ->
            highlightedLevels().forEach { level ->
                val ratio = contrast(level.readerColor(ramp), paper.theme.background)
                assertTrue(
                    ratio >= MINIMUM_PAPER_CONTRAST,
                    "${paper.name} ${level.name}: $ratio:1 against ${paper.theme.background}",
                )
            }
        }
    }

    @Test
    fun everyAdjacentPairMeetsTheMinimum() {
        forEachPaper { paper, ramp ->
            // MasteryLevel declares the scale in order, so the enum order is the adjacency order.
            // KNOWN is absent: it has no ramp token, and the underline separates it from LEARNED.
            highlightedLevels().zipWithNext().forEach { (lower, higher) ->
                val ratio = contrast(lower.readerColor(ramp), higher.readerColor(ramp))
                assertTrue(
                    ratio >= MINIMUM_ADJACENT_CONTRAST,
                    "${paper.name} ${lower.name}/${higher.name}: $ratio:1 against each other",
                )
            }
        }
    }

    @Test
    fun knownStaysColourlessOnEveryPaper() {
        forEachPaper { paper, ramp ->
            assertTrue(
                MasteryLevel.KNOWN.readerColor(ramp) == Color.Unspecified,
                "${paper.name} paints a known word",
            )
        }
    }

    private fun highlightedLevels(): List<MasteryLevel> = MasteryLevel.entries.filter { level -> level.highlighted }

    private fun forEachPaper(assertion: (Paper, MasteryRamp) -> Unit) {
        papers.forEach { paper -> assertion(paper, MasteryPalette.of(paper.theme)) }
    }

    /** WCAG 2.1's relative-luminance contrast ratio. */
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (max(first, second) + 0.05f) / (min(first, second) + 0.05f)
    }
}
