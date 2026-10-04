package app.lekto.tools.dictionaries

import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pack keys must match `core`'s `normaliseSurface` exactly, so the duplicated
 * rule is pinned here: NFC plus a locale-independent lower-case fold.
 */
class SurfaceNormalisationTest {

    @Test
    fun `folds case and normalises to NFC`() {
        assertEquals("café", normaliseSurface("Café"))
        assertEquals("café", normaliseSurface(Normalizer.normalize("café", Normalizer.Form.NFD)))
    }

    @Test
    fun `is idempotent`() {
        val once = normaliseSurface("ǅungla")
        assertEquals(once, normaliseSurface(once))
    }

    @Test
    fun `keeps hyphenated and apostrophised tokens`() {
        assertTrue(isSingleToken("well-known"))
        assertTrue(isSingleToken("l'ami"))
        assertTrue(isSingleToken("run"))
    }

    @Test
    fun `drops multi-word and blank surfaces`() {
        assertFalse(isSingleToken("ice cream"))
        assertFalse(isSingleToken("avoir + past participle"))
        assertFalse(isSingleToken("   "))
    }
}
