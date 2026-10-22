package app.lekto.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The language hues (`docs/design.md` §2.4; issue #208): the five the identity
 * names, lifted by a third on night's paper, Sepia's daylight hues unchanged, and
 * a language the identity does not name reading as the theme's muted ink rather
 * than as a sixth hue nobody chose.
 */
class LanguageHuesTest {

    @Test
    fun `each named language keeps the hue the identity gives it`() {
        DAYLIGHT_HUES.forEach { (language, hue) ->
            assertEquals(hue, LanguageHues.fill(language, Light), "Light $language")
            assertEquals(hue, LanguageHues.fill(language, Sepia), "Sepia $language")
        }
    }

    @Test
    fun `a dark paper lifts every hue by a third`() {
        DAYLIGHT_HUES.forEach { (language, hue) ->
            val lifted = LanguageHues.fill(language, Dark)

            assertEquals(lerp(hue, Color.White, LIFT), lifted, "Dark $language")
            assertTrue(lifted.luminance() > hue.luminance(), "the lifted hue must read brighter: $language")
        }
    }

    @Test
    fun `a regional tag takes its base language's hue`() {
        assertEquals(LanguageHues.fill("fr", Light), LanguageHues.fill("fr-CA", Light))
        assertEquals(LanguageHues.fill("en", Dark), LanguageHues.fill("en-GB", Dark))
    }

    @Test
    fun `a language the identity does not name reads as the muted ink`() {
        assertEquals(Light.onSurfaceVariant, LanguageHues.fill("pt", Light))
        assertEquals(Dark.onSurfaceVariant, LanguageHues.fill("pt", Dark))
        assertEquals(Light.onSurfaceVariant, LanguageHues.fill(null, Light))
        assertEquals(Light.onSurfaceVariant, LanguageHues.fill("", Light))
    }
}

/** The third of the way to white a dark paper lifts each hue (`docs/design.md` §2.4). */
private const val LIFT = 1f / 3f

/** The hues `docs/design.md` §2.4 names, as the daylight papers paint them. */
private val DAYLIGHT_HUES = mapOf(
    "en" to Color(0xFF4C6B8A),
    "fr" to Color(0xFF8A5A44),
    "es" to Color(0xFF9A7B33),
    "de" to Color(0xFF5F7A5A),
    "it" to Color(0xFF7A5F7A),
)
