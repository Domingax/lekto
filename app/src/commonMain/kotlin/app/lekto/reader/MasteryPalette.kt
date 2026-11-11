package app.lekto.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextDecoration
import app.lekto.core.MasteryLevel
import app.lekto.theme.isDarkPaper
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
    /**
     * The daylight paper's ramp: four dark hues, each at or above 4.5:1 on
     * `#FAF7F2` and at least 1.25:1 from every other level. Unknown is teal
     * rather than blue: blue plus the underline reads as a hyperlink.
     */
    val Light = MasteryRamp(
        unknown = Color(0xFF0D2B2E),
        recognized = Color(0xFF515B0A),
        familiar = Color(0xFF63230E),
        learned = Color(0xFF11522C),
    )

    /**
     * Warm night: the same meanings, lifted until they read on near-black and
     * spread so no two levels are the same blot.
     */
    val Dark = MasteryRamp(
        unknown = Color(0xFF328F97),
        recognized = Color(0xFFA2B61A),
        familiar = Color(0xFFE17A58),
        learned = Color(0xFF68D997),
    )

    /**
     * Rag paper: the same four meanings, tuned to `#F6ECD8` so each level clears
     * both the paper bar and the pair bar. Its warmer ink leaves less room than
     * the daylight paper's, so this ramp is the tightest of the three.
     */
    val Sepia = MasteryRamp(
        unknown = Color(0xFF103437),
        recognized = Color(0xFF5B670B),
        familiar = Color(0xFF732910),
        learned = Color(0xFF145E33),
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
        isDarkPaper(paper) -> Dark
        else -> Light
    }
}

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

/** The share of white the wash's base paper is lifted by, so the tint sits behind the text. */
private const val WASH_PAPER_LIFT: Float = 0.5f

/** How much of the level's hue each wash carries: the palest on unknown, the deepest on learned. */
private const val WASH_TINT_UNKNOWN: Float = 0.08f
private const val WASH_TINT_RECOGNIZED: Float = 0.14f
private const val WASH_TINT_FAMILIAR: Float = 0.20f
private const val WASH_TINT_LEARNED: Float = 0.26f

/**
 * The pale tint behind a highlighted word on a daylight paper (`docs/design.md`
 * §2.2): the paper lifted halfway to white, then tinted with the level's own hue.
 * It is the second cue the four levels need on Light and Sepia, where four dark
 * hues alone leave too little room. The tint is measured with the text against
 * it, not against the bare paper, so it never eats the 4.5:1 bar.
 *
 * The night paper keeps its lifted hues alone: a tint there would pull the paper
 * toward the light text and lower the contrast. A known word has no tint.
 */
fun MasteryLevel.readerBackground(ramp: MasteryRamp, paper: Color): Color {
    val tint = when (this) {
        MasteryLevel.UNKNOWN -> WASH_TINT_UNKNOWN
        MasteryLevel.RECOGNIZED -> WASH_TINT_RECOGNIZED
        MasteryLevel.FAMILIAR -> WASH_TINT_FAMILIAR
        MasteryLevel.LEARNED -> WASH_TINT_LEARNED
        MasteryLevel.KNOWN -> null
    }
    return if (tint == null || isDarkPaper(paper)) {
        Color.Unspecified
    } else {
        val base = lerp(paper, Color.White, WASH_PAPER_LIFT)
        lerp(base, readerColor(ramp), tint)
    }
}

/**
 * [readerColor], or [normal] when the level has no colour of its own — the
 * single place "a known word is normal text" is expressed, so the renderer and
 * the golden preview cannot drift apart.
 */
fun MasteryLevel.readerColorOr(normal: Color, ramp: MasteryRamp): Color =
    readerColor(ramp).takeIf { colour -> colour != Color.Unspecified } ?: normal

/**
 * The non-colour indicator for [this] level, for colour-blind readers (UX spec
 * §Accessibility). Compose's [TextDecoration] offers only none/underline/
 * line-through, so the spike uses a single underline: highlighted words carry
 * one, known words do not. A distinct shape per level needs a custom
 * `TextDecoration` renderer and is recorded as follow-up in the spike report.
 */
fun MasteryLevel.readerDecoration(): TextDecoration = if (highlighted) TextDecoration.Underline else TextDecoration.None
