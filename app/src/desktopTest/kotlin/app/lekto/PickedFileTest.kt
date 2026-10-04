package app.lekto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * A picked file's identity (issue #18): a re-pick of the same name and bytes is
 * the same value, so tests and state compare it by content rather than identity.
 */
@Suppress("MagicNumber") // The byte values are the test's parameters, not a domain constant.
class PickedFileTest {

    private val file = PickedFile("lantern.epub", byteArrayOf(1, 2, 3))

    @Test
    fun `the same name and bytes are equal, with the same hash`() {
        assertEquals(file, file)
        assertEquals(file, PickedFile("lantern.epub", byteArrayOf(1, 2, 3)))
        assertEquals(file.hashCode(), PickedFile("lantern.epub", byteArrayOf(1, 2, 3)).hashCode())
    }

    @Test
    fun `a different name is not equal`() {
        assertNotEquals(file, PickedFile("other.epub", byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `different bytes are not equal`() {
        assertNotEquals(file, PickedFile("lantern.epub", byteArrayOf(4, 5, 6)))
    }

    @Test
    fun `a different byte length is not equal`() {
        assertNotEquals(file, PickedFile("lantern.epub", byteArrayOf(1, 2)))
    }

    @Test
    fun `a non-file is not equal`() {
        assertFalse(file.equals("not a file"))
    }
}
