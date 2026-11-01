package app.lekto.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The relative luminance above which a fill is light enough to need dark ink. */
private const val LIGHT_FILL_LUMINANCE = 0.4f

/** The ink a lifted fill uses: near-black, WCAG AA on every lifted hue the identity names. */
val InkOnLightFill: Color = Color(0xFF1A1A1A)

/**
 * The foreground that reads on a filled [background] at WCAG AA: white on the
 * daylight papers' darker hues, [InkOnLightFill] on a dark paper's lifted ones.
 *
 * It is the one rule for text on a filled control, wherever the fill came from —
 * a **Mastery level**'s chip in the lookup panel, or a row's **Reading language**
 * vignette (§2.2, §2.4) — so the two cannot disagree about which ink is legible.
 */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > LIGHT_FILL_LUMINANCE) InkOnLightFill else Color.White

/** The share of the theme's ink a surface carries while the pointer rests on it (`docs/design.md` §8). */
const val HOVER_WASH_ALPHA: Float = 0.05f

/**
 * The wash a row carries while the pointer is over it: the theme's own ink at
 * [HOVER_WASH_ALPHA], so the surface under the mouse is the one it is about,
 * with nothing new on screen and nothing moving (§2.3's rule, one step lighter
 * than the selected word's 14 %).
 */
fun hoverWash(ink: Color): Color = ink.copy(alpha = HOVER_WASH_ALPHA)
