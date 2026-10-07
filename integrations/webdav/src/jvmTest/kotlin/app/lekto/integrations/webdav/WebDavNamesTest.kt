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
        assertFalse(WebDavNames.isRecord("attachments/YQ.bin"))
    }

    @Test
    fun `a name that is not a Base64-URL id reads as null`() {
        assertNull(WebDavNames.decode("not base64!!"))
    }
}
