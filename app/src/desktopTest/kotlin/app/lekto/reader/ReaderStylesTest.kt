package app.lekto.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingMargin
import app.lekto.core.reading.ReadingPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bridge from a **Reading preference** to the styles the page renders with
 * (ADR-0035): the default reproduces the reader's fixed style, the face maps to a
 * platform face, the size and leading follow the preference, and the heading
 * scales with the body rather than staying put.
 */
@Suppress("MagicNumber") // The fixed sizes and margins are the test's parameters.
class ReaderStylesTest {

    @Test
    fun theDefaultsReproduceTheFixedReadingStyle() {
        val styles = ReaderStyles.of(ReadingPreferences.DEFAULT)

        assertEquals(FontFamily.Serif, styles.body.fontFamily)
        assertEquals(18.sp, styles.body.fontSize)
        assertEquals((18f * ReadingPreferences.LEADING_RATIO).sp, styles.body.lineHeight)
        assertEquals(24.sp, styles.heading.fontSize)
        assertEquals(24.dp, styles.horizontalMargin)
    }

    @Test
    fun aSansSerifPreferenceUsesThePlatformSansSerif() {
        val styles = ReaderStyles.of(ReadingPreferences(face = ReadingFace.SANS_SERIF))

        assertEquals(FontFamily.SansSerif, styles.body.fontFamily)
        assertEquals(FontFamily.SansSerif, styles.heading.fontFamily)
    }

    @Test
    fun theSizeAndLeadingFollowThePreference() {
        val styles = ReaderStyles.of(ReadingPreferences(size = 24f))

        assertEquals(24.sp, styles.body.fontSize)
        assertEquals((24f * ReadingPreferences.LEADING_RATIO).sp, styles.body.lineHeight)
    }

    @Test
    fun theHeadingScalesWithTheSize() {
        val default = ReaderStyles.of(ReadingPreferences(size = 18f))
        val larger = ReaderStyles.of(ReadingPreferences(size = 36f))

        assertEquals((24f * 2).sp, larger.heading.fontSize, "the heading must double when the body doubles")
        assertEquals(default.heading.fontSize.value * 2, larger.heading.fontSize.value, "the ratio holds")
    }

    @Test
    fun theMarginFollowsThePreference() {
        assertEquals(16.dp, ReaderStyles.of(ReadingPreferences(margin = ReadingMargin.NARROW)).horizontalMargin)
        assertEquals(32.dp, ReaderStyles.of(ReadingPreferences(margin = ReadingMargin.WIDE)).horizontalMargin)
    }

    @Test
    fun theInkComesFromTheAppTheme() {
        val ink = Color(0xFFE8E2D8)
        val styles = ReaderStyles.of(ReadingPreferences.DEFAULT, ink)

        assertEquals(ink, styles.body.color)
        assertEquals(ink, styles.heading.color)
    }
}
