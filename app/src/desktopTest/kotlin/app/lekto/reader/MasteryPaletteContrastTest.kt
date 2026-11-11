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

/** The bar any two levels clear against each other (`docs/design.md` §2.2). */
private const val MINIMUM_PAIR_CONTRAST: Float = 1.25f

/**
 * The one rule the mastery palette cannot break (`docs/design.md` §2.2,
 * ADR-0036): a coloured word is 18 sp of running text, so every hue is at least
 * 4.5:1 against its own paper. A second rule is the reader's actual comparison:
 * any two levels must differ from each other, because a page puts any two words
 * side by side, not only the neighbours of the scale. Measured tests, not
 * comments, because the delivered palette failed three of its four levels and no
 * golden can tell a readable hue from a pretty one.
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
    fun everyPairOfLevelsMeetsTheMinimum() {
        forEachPaper { paper, ramp ->
            // A page can show any two levels together, not only the neighbours of the scale.
            highlightedLevels().forEachIndexed { index, lower ->
                highlightedLevels().drop(index + 1).forEach { higher ->
                    val ratio = contrast(lower.readerColor(ramp), higher.readerColor(ramp))
                    assertTrue(
                        ratio >= MINIMUM_PAIR_CONTRAST,
                        "${paper.name} ${lower.name}/${higher.name}: $ratio:1 against each other",
                    )
                }
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
