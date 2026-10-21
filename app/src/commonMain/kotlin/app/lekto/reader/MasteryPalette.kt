package app.lekto.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextDecoration
import app.lekto.core.MasteryLevel
import app.lekto.theme.Sepia as SepiaScheme

/**
 * One paper's measured mastery ramp (`docs/design.md` §2.2, ADR-0036). Four
 * hues keep their meanings; their lightness is chosen against their own paper,
 * because a coloured word is 18 sp of running text and so its bar is WCAG AA,
 * 4.5:1. Level 4 is absent: "known" is normal text.
 */
data class MasteryRamp(val unknown: Color, val recognized: Color, val familiar: Color, val learned: Color)

/**
 * The mastery palette: one measured ramp per paper, and the lookup from the
 * active **App theme** to it (`docs/design.md` §2.2; ADR-0036).
 */
object MasteryPalette {
    /** The daylight paper's ramp: four dark hues, each at or above 4.6:1. */
    val Light = MasteryRamp(
        unknown = Color(0xFF1D4ED8),
        recognized = Color(0xFFA16207),
        familiar = Color(0xFFC2410C),
        learned = Color(0xFF15803D),
    )

    /** Warm night: the same meanings, lifted until they read on near-black. */
    val Dark = MasteryRamp(
        unknown = Color(0xFF8AB4F8),
        recognized = Color(0xFFF2C14E),
        familiar = Color(0xFFEE9A5F),
        learned = Color(0xFF7BD88F),
    )

    /** Rag paper: Light's ramp with its three warm hues darkened to 6:1. */
    val Sepia = MasteryRamp(
        unknown = Color(0xFF1D4ED8),
        recognized = Color(0xFF92400E),
        familiar = Color(0xFF9A3412),
        learned = Color(0xFF166534),
    )

    /** The ramp for [theme]'s paper. */
    fun of(theme: ColorScheme): MasteryRamp = ofPaper(theme.background)

    /**
     * The ramp for [paper]. Sepia is recognised by its own tone; any other dark
     * paper takes [Dark] rather than silently falling back to the daylight hues,
     * which are the ones that fail on a dark background.
     */
    internal fun ofPaper(paper: Color): MasteryRamp = when {
        paper == SepiaScheme.background -> Sepia
        paper.luminance() < DARK_PAPER_LUMINANCE -> Dark
        else -> Light
    }
}

/** The luminance below which a paper is dark enough to need the lifted ramp. */
private const val DARK_PAPER_LUMINANCE = 0.18f

/**
 * The wash the tapped word carries while its lookup panel is open — the
 * `WordToken` `selected` state of the UX spec. The theme's own ink at 14 %, so
 * nothing new enters the screen and the level's colour still reads through.
 */
fun selectionHighlight(ink: Color): Color = ink.copy(alpha = SELECTION_WASH_ALPHA)

/** The share of the theme's ink the selected word carries (`docs/design.md` §2.3). */
const val SELECTION_WASH_ALPHA: Float = 0.14f

/** The colour a word at [level] is painted with; `Unspecified` means normal text. */
fun MasteryLevel.readerColor(ramp: MasteryRamp): Color = when (this) {
    MasteryLevel.UNKNOWN -> ramp.unknown
    MasteryLevel.RECOGNIZED -> ramp.recognized
    MasteryLevel.FAMILIAR -> ramp.familiar
    MasteryLevel.LEARNED -> ramp.learned
    MasteryLevel.KNOWN -> Color.Unspecified
}

/**
 * [readerColor], or [normal] when the level has no colour of its own — the
 * single place "a known word is normal text" is expressed, so the renderer and
 * the golden preview cannot drift apart.
 */
fun MasteryLevel.readerColorOr(normal: Color, ramp: MasteryRamp): Color =
    readerColor(ramp).takeIf { colour -> colour != Color.Unspecified } ?: normal

/** The relative luminance above which a palette level is light enough to need dark ink. */
private const val LIGHT_LUMINANCE = 0.4f

/** The ink a lifted palette level uses on a filled control: near-black, WCAG AA on each. */
val MasteryInk: Color = Color(0xFF1A1A1A)

/**
 * The foreground that reads on a filled [background] at WCAG AA: white on the
 * light papers' darker hues, [MasteryInk] on the dark paper's lifted ones. Used
 * where a level is a **filled control** rather than text — the lookup panel's
 * selector — so its chip carries the same colour as the word it will paint in
 * the reader.
 */
fun contentColorOn(background: Color): Color = if (background.luminance() > LIGHT_LUMINANCE) MasteryInk else Color.White

/**
 * The non-colour indicator for [this] level, for colour-blind readers (UX spec
 * §Accessibility). Compose's [TextDecoration] offers only none/underline/
 * line-through, so the spike uses a single underline: highlighted words carry
 * one, known words do not. A distinct shape per level needs a custom
 * `TextDecoration` renderer and is recorded as follow-up in the spike report.
 */
fun MasteryLevel.readerDecoration(): TextDecoration = if (highlighted) TextDecoration.Underline else TextDecoration.None
