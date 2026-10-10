package app.lekto.integrations.webdav

/**
 * The HTTP requests the driver sends, named by the WebDAV method each one is, so
 * the client reads as a sequence of protocol operations rather than header
 * plumbing. Each builds a platform-neutral [WebDavRequest] for the transport.
 */
internal object WebDavRequests {

    /** The `PROPFIND` body listing the properties the driver reads. */
    val PROPFIND_BODY: ByteArray =
        (
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<D:propfind xmlns:D="DAV:">""" +
                """<D:prop><D:getetag/><D:resourcetype/></D:prop></D:propfind>"""
            ).toByteArray()

    fun propfind(url: String, depth: Int = 1): WebDavRequest = WebDavRequest(
        method = "PROPFIND",
        url = url,
        headers = mapOf("Depth" to depth.toString(), "Content-Type" to "application/xml; charset=utf-8"),
        body = PROPFIND_BODY,
    )

    fun mkcol(url: String): WebDavRequest = WebDavRequest("MKCOL", url)

    fun get(url: String): WebDavRequest = WebDavRequest("GET", url)

    fun head(url: String): WebDavRequest = WebDavRequest("HEAD", url)

    fun delete(url: String): WebDavRequest = WebDavRequest("DELETE", url)

    /** A `MOVE` that renames [url] to [destination], overwriting any file already there. */
    fun move(url: String, destination: String): WebDavRequest =
        WebDavRequest("MOVE", url, mapOf("Destination" to destination, "Overwrite" to "T"))

    fun put(url: String, bytes: ByteArray): WebDavRequest =
        WebDavRequest("PUT", url, mapOf("Content-Type" to "application/octet-stream"), bytes)

    /**
     * The conditional headers [condition] translates to.
     *
     * A create uses HTTP `If-None-Match: *`. An update conditions on the
     * revision through WebDAV's `If` header (`If: ([<etag>])`), not HTTP
     * `If-Match`: `If-Match` requires a **strong** ETag comparison, and Apache
     * `mod_dav` marks a freshly written file's ETag weak for a second (its
     * `ap_make_etag_ex` cannot rule out a change within the same second), so an
     * immediate `If-Match` update is rejected. The `If` header compares ETags
     * weakly (RFC 4918 §10.4.1), so it works on both strong and weak ETags.
     */
    fun conditions(condition: WriteCondition): Map<String, String> = when (condition) {
        WriteCondition.CreateOnly -> mapOf("If-None-Match" to "*")
        is WriteCondition.MatchesRevision -> mapOf("If" to "([${condition.revision}])")
        WriteCondition.Unconditional -> emptyMap()
    }
}
