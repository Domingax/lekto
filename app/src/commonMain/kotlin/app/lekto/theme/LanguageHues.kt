package app.lekto.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.lekto.core.text.baseLanguage

/**
 * The muted, earthy hue a book's **Reading language** is painted with
 * (`docs/design.md` §2.4; issue #208): the fill of the Library row's language
 * vignette, and nothing else.
 *
 * A hue is **decoration**, never information. The two-letter code printed on the
 * vignette is the signal, so a reader who cannot tell the hues apart loses
 * nothing — which is why the palette is chosen for calm rather than for maximum
 * distance. A language the identity does not name takes the theme's muted ink.
 *
 * The five hues are the daylight papers'; on a dark paper each is lifted by a
 * third so it stays visible without becoming a light source. Sepia is a daylight
 * paper and keeps them unchanged.
 */
object LanguageHues {

    /** The daylight palette: English, French, Spanish, German and Italian. */
    private val Daylight: Map<String, Color> = mapOf(
        "en" to Color(0xFF4C6B8A),
        "fr" to Color(0xFF8A5A44),
        "es" to Color(0xFF9A7B33),
        "de" to Color(0xFF5F7A5A),
        "it" to Color(0xFF7A5F7A),
    )

    /** The share of white a dark paper's hue is lifted by. */
    private const val DARK_LIFT = 1f / 3f

    /**
     * The vignette's fill for [language] on [theme]'s paper, or the theme's muted
     * ink when the identity names no hue for that language.
     */
    fun fill(language: String?, theme: ColorScheme): Color {
        val hue = Daylight[baseLanguage(language.orEmpty())] ?: return theme.onSurfaceVariant
        return if (isDarkPaper(theme.background)) lerp(hue, Color.White, DARK_LIFT) else hue
    }
}
