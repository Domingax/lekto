package app.lekto.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The filled-control foreground rule (`docs/design.md` §2.2, §2.4): the ink is
 * chosen at the luminance where it overtakes white, so a lifted dark-paper hue
 * and a mid-tone daylight hue take ink rather than unreadable white. The mastery
 * ramps and the language vignettes both read through this one function.
 */
@Suppress("MagicNumber") // The sample fills are the identity's own tokens.
class ContentColorTest {

    @Test
    fun aDarkFillTakesWhite() {
        assertEquals(Color.White, contentColorOn(Color(0xFF123A3E)))
    }

    @Test
    fun aLiftedFillTakesInk() {
        assertEquals(InkOnLightFill, contentColorOn(Color(0xFF328F97)))
    }

    @Test
    fun aMidToneTakesTheMoreLegibleInk() {
        val fill = Color(0xFF9A7B33) // the daylight Spanish vignette
        assertEquals(InkOnLightFill, contentColorOn(fill))
        assertTrue(contrast(InkOnLightFill, fill) > contrast(Color.White, fill))
    }

    /** WCAG 2.1's relative-luminance contrast ratio. */
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (max(first, second) + 0.05f) / (min(first, second) + 0.05f)
    }
}
