package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingPreferences

/**
 * The reader's typography: a serif body and a heading style, built from a
 * **Reading preference** (ADR-0035) and pinned here rather than taken from the
 * (UI) Material theme so pagination measures exactly what the page renders. The
 * default preference reproduces the fixed style the reader used to hold: an 18px
 * serif at a 1.7 line height and a 24px heading.
 *
 * The [horizontalMargin] rides along, because the page's margin is the same
 * reading preference and the reader must paginate against the width it leaves.
 */
data class ReaderStyles(val body: TextStyle, val heading: TextStyle, val horizontalMargin: Dp) {
    companion object {
        /** Near-black on the light reading theme. */
        private val Ink = Color(0xFF1A1A1A)

        /** The heading's size and leading as multiples of the body's, so they follow the size control. */
        private const val HEADING_SIZE_RATIO: Float = 24f / 18f
        private const val HEADING_LEADING_RATIO: Float = 34f / 18f

        /** [preferences] rendered as the styles the page and its pagination measure. */
        fun of(preferences: ReadingPreferences): ReaderStyles {
            val family = when (preferences.face) {
                ReadingFace.SERIF -> FontFamily.Serif
                ReadingFace.SANS_SERIF -> FontFamily.SansSerif
            }
            return ReaderStyles(
                body = TextStyle(
                    fontFamily = family,
                    fontSize = preferences.size.sp,
                    lineHeight = preferences.leading.sp,
                    color = Ink,
                ),
                heading = TextStyle(
                    fontFamily = family,
                    fontSize = (preferences.size * HEADING_SIZE_RATIO).sp,
                    lineHeight = (preferences.size * HEADING_LEADING_RATIO).sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                ),
                horizontalMargin = preferences.horizontalMargin.dp,
            )
        }
    }
}
