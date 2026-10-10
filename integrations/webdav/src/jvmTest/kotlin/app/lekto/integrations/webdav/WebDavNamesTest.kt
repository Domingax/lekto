package app.lekto.integrations.webdav

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The remote layout's names on their own: an id round-trips through its path
 * segment for arbitrary text, a record path is recognised, and a name that is
 * not a Base64-URL id reads as `null` rather than throwing.
 */
class WebDavNamesTest {

    @Test
    fun `an id round-trips through its path segment`() {
        val id = "a/b c-é"

        assertEquals(id, WebDavNames.decode(WebDavNames.encode(id)))
    }

    @Test
    fun `a record path is a json file under records`() {
        assertTrue(WebDavNames.isRecord("records/YQ.json"))
        assertFalse(WebDavNames.isRecord("records/README.txt"))
        assertFalse(WebDavNames.isRecord("attachments/YQ.data"))
    }

    @Test
    fun `an attachment path is a data file under attachments, and reads its id back`() {
        // The suffix is `.data`, not `.bin`: Koofr accepts a `.bin` upload but
        // refuses to serve it back, so an original could never be downloaded
        // (issue #116). This pins the remote layout's attachment channel.
        val path = WebDavNames.attachmentPath("a/b c-é")

        assertTrue(path.endsWith(".data"), path)
        assertEquals("a/b c-é", WebDavNames.idFromAttachment(path))
    }

    @Test
    fun `a name that is not a Base64-URL id reads as null`() {
        assertNull(WebDavNames.decode("not base64!!"))
    }
}
