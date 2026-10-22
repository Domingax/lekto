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
