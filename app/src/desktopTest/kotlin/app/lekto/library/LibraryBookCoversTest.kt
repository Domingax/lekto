package app.lekto.library

import app.lekto.InMemoryLibrary
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The real [LibraryBookCovers] over a book's stored bytes (issue #259): a valid
 * image decodes through the desktop's Skia seam, and a book with no cover asks
 * for its language plaque instead. The UI suite uses a stand-in loader; this one
 * crosses the decode the app actually performs.
 */
class LibraryBookCoversTest {

    @Test
    fun aStoredCoverDecodesAndABookWithoutOneStaysNull() {
        val covers = LibraryBookCovers(InMemoryLibrary(covers = mapOf("b1" to ONE_PIXEL_PNG)))

        assertNotNull(covers.load("b1"))
        assertNull(covers.load("b2"))
    }

    private companion object {
        /** A real 1 × 1 PNG: what an import stores, not a header stub. */
        val ONE_PIXEL_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
        )
    }
}
