package app.lekto.integrations.webdav

import java.net.URI
import java.net.http.HttpRequest

/**
 * The HTTP requests the driver sends, named by the WebDAV method each one is, so
 * the client reads as a sequence of protocol operations rather than header
 * plumbing.
 */
internal object WebDavRequests {

    /** The `PROPFIND` body listing the properties the driver reads. */
    val PROPFIND_BODY: ByteArray =
        (
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<D:propfind xmlns:D="DAV:">""" +
                """<D:prop><D:getetag/><D:resourcetype/></D:prop></D:propfind>"""
            ).toByteArray()

    fun propfind(url: String): HttpRequest.Builder = HttpRequest.newBuilder(URI.create(url))
        .method("PROPFIND", HttpRequest.BodyPublishers.ofByteArray(PROPFIND_BODY))
        .header("Depth", "1")
        .header("Content-Type", "application/xml; charset=utf-8")

    fun mkcol(url: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(url)).method("MKCOL", HttpRequest.BodyPublishers.noBody())

    fun get(url: String): HttpRequest.Builder = HttpRequest.newBuilder(URI.create(url)).GET()

    fun head(url: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody())

    fun delete(url: String): HttpRequest.Builder = HttpRequest.newBuilder(URI.create(url)).DELETE()

    fun put(url: String, bytes: ByteArray): HttpRequest.Builder = HttpRequest.newBuilder(URI.create(url))
        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
        .header("Content-Type", "application/octet-stream")

    /** The conditional headers [condition] translates to (RFC 4918, RFC 7232). */
    fun conditions(condition: WriteCondition): Map<String, String> = when (condition) {
        WriteCondition.CreateOnly -> mapOf("If-None-Match" to "*")
        is WriteCondition.MatchesRevision -> mapOf("If-Match" to condition.revision)
        WriteCondition.Unconditional -> emptyMap()
    }
}
