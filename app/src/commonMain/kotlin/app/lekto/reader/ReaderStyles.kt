package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingPreferences

/**
 * The reader's typography: a serif body and a heading style, built from a
 * **Reading preference** (ADR-0035) and pinned here rather than taken from the
 * (UI) Material theme so pagination measures exactly what the page renders. The
 * face, size, leading and margin come from the preference; [ink] comes from the
 * active **App theme**'s paper (`docs/design.md` §2.1), because colour does not
 * move a glyph and so cannot change a page break.
 *
 * The default preference reproduces the fixed style the reader used to hold: an
 * 18px serif at a 1.7 line height and a 24px heading, in near-black ink.
 *
 * The [horizontalMargin] rides along, because the page's margin is the same
 * reading preference and the reader must paginate against the width it leaves.
 * [mastery] is the active paper's measured ramp (`docs/design.md` §2.2), carried
 * here so the word layer and the golden preview paint the same hues the screen
 * does — a colour cannot change a glyph's width, so it cannot change a page.
 */
data class ReaderStyles(
    val body: TextStyle,
    val heading: TextStyle,
    val horizontalMargin: Dp,
    val mastery: MasteryRamp,
    /**
     * One list level's indent, scaled from the body's size so the hanging indent
     * follows the size control. A marker sits at its level's indent and the item's
     * wrapped lines align one step further in.
     */
    val listIndent: TextUnit,
) {
    companion object {
        /** The Light paper's ink, used when no theme supplies one. */
        private val DefaultInk = Color(0xFF1C1A17)

        /** The heading's size and leading as multiples of the body's, so they follow the size control. */
        private const val HEADING_SIZE_RATIO: Float = 24f / 18f
        private const val HEADING_LEADING_RATIO: Float = 34f / 18f

        /** A list level's indent, in body-line units: room for a bullet or a rank and a space. */
        private const val LIST_INDENT_RATIO: Float = 1.5f

        /** [preferences] rendered as the styles the page and its pagination measure. */
        fun of(preferences: ReadingPreferences, mastery: MasteryRamp, ink: Color = DefaultInk): ReaderStyles {
            val family = when (preferences.face) {
                ReadingFace.SERIF -> FontFamily.Serif
                ReadingFace.SANS_SERIF -> FontFamily.SansSerif
            }
            return ReaderStyles(
                body = TextStyle(
                    fontFamily = family,
                    fontSize = preferences.size.sp,
                    lineHeight = preferences.leading.sp,
                    color = ink,
                ),
                heading = TextStyle(
                    fontFamily = family,
                    fontSize = (preferences.size * HEADING_SIZE_RATIO).sp,
                    lineHeight = (preferences.size * HEADING_LEADING_RATIO).sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                ),
                horizontalMargin = preferences.horizontalMargin.dp,
                mastery = mastery,
                listIndent = (preferences.size * LIST_INDENT_RATIO).sp,
            )
        }
    }
}
