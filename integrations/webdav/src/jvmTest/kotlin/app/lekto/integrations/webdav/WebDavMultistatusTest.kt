package app.lekto.integrations.webdav

import app.lekto.core.sync.SyncTargetException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The listing parser on its own: it reads a `207 Multi-Status` body into the
 * hrefs the driver resolves to item paths, and a body it cannot parse becomes a
 * recoverable [SyncTargetException] rather than a crash or a silent skip.
 */
class WebDavMultistatusTest {

    @Test
    fun `reads the href of every response`() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<D:multistatus xmlns:D="DAV:">""" +
            """<D:response><D:href>/webdav/records/one.json</D:href></D:response>""" +
            """<D:response><D:href>/webdav/records/two.json</D:href></D:response>""" +
            """</D:multistatus>"""

        assertEquals(
            listOf("/webdav/records/one.json", "/webdav/records/two.json"),
            WebDavMultistatus.hrefs(xml),
        )
    }

    @Test
    fun `a malformed listing is a recoverable error`() {
        assertFailsWith<SyncTargetException> { WebDavMultistatus.hrefs("<not-xml") }
    }
}
