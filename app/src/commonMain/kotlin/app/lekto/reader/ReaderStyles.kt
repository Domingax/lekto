package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingPreferences

/**
 * The widest a page's text column may be, in ems of the body's size
 * (docs/design.md §3, issue #281): 34 em is about 68 characters at 18 sp, inside
 * the 45–75 band a learner reads comfortably. It is held in `em` rather than dp so
 * the cap follows the size control and the platform's font scale, where a fixed
 * width would not.
 */
internal const val MAX_MEASURE_EM: Float = 34f

/**
 * The reader's typography: a serif body and a heading style, built from a
 * **Reading preference** (ADR-0035) and pinned here rather than taken from the
 * (UI) Material theme so pagination measures exactly what the page renders. The
 * face, size, leading and margin come from the preference; [ink] comes from the
 * active **App theme**'s paper (`docs/design.md` §2.1), because colour does not
 * move a glyph and so cannot change a page break.
 *
 * The body is set justified, the way a printed book's text is: every wrapped
 * line ends at the right margin, while a paragraph's last line keeps its ragged
 * end — and so does a page's bottom line, since a page is a slice that ends at a
 * line break. Alignment is fixed typography, not a **Reading preference** — it
 * changes no glyph's advance, so it changes no page break — and the heading is
 * held to the start edge so a title that wraps does not stretch.
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
                    textAlign = TextAlign.Justify,
                    color = ink,
                ),
                heading = TextStyle(
                    fontFamily = family,
                    fontSize = (preferences.size * HEADING_SIZE_RATIO).sp,
                    lineHeight = (preferences.size * HEADING_LEADING_RATIO).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    color = ink,
                ),
                horizontalMargin = preferences.horizontalMargin.dp,
                mastery = mastery,
                listIndent = (preferences.size * LIST_INDENT_RATIO).sp,
            )
        }
    }
}

/**
 * The margin that centres the page's text column when [MAX_MEASURE_EM] caps it,
 * and the reading margin when it does not: the preference stays a minimum and the
 * cap a maximum, so neither overrides the other (docs/design.md §3, issue #281).
 */
internal fun ReaderStyles.pageInset(pageWidth: Dp, density: Density): Dp {
    val measure = with(density) { (body.fontSize * MAX_MEASURE_EM).toDp() }
    val column = minOf(pageWidth - horizontalMargin * 2, measure).coerceAtLeast(0.dp)
    return ((pageWidth - column) / 2).coerceAtLeast(0.dp)
}

/** The width the paginator measures, in pixels: the capped, centred column the page renders. */
internal fun ReaderStyles.contentWidthPx(pageWidth: Dp, density: Density): Int =
    with(density) { (pageWidth - pageInset(pageWidth, density) * 2).roundToPx() }.coerceAtLeast(0)
