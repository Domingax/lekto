package app.lekto.integrations.webdav

import app.lekto.core.sync.SyncTargetException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The listing parser on its own: it reads a `207 Multi-Status` body into the
 * resource paths the driver resolves to item paths and their reported ETags, so a
 * listing can skip an unchanged item without a `GET` (ADR-0027), and a body it
 * cannot parse becomes a recoverable [SyncTargetException] rather than a crash or
 * a silent skip.
 */
class WebDavMultistatusTest {

    @Test
    fun `reads the href and etag of every response`() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<D:multistatus xmlns:D="DAV:">""" +
            """<D:response><D:href>/webdav/records/one.json</D:href>""" +
            """<D:propstat><D:prop><D:getetag>"etag-1"</D:getetag></D:prop></D:propstat></D:response>""" +
            """<D:response><D:href>/webdav/records/two.json</D:href></D:response>""" +
            """</D:multistatus>"""

        assertEquals(
            listOf(
                WebDavResource("/webdav/records/one.json", "\"etag-1\""),
                WebDavResource("/webdav/records/two.json", null),
            ),
            WebDavMultistatus.entries(xml),
        )
    }

    @Test
    fun `a malformed listing is a recoverable error`() {
        assertFailsWith<SyncTargetException> { WebDavMultistatus.entries("<not-xml") }
    }
}
