@file:Suppress("MagicNumber", "TooManyFunctions") // HTTP status codes are the protocol's; a fake speaks every verb.

package app.lekto.testkit

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/**
 * An in-process WebDAV server for a driver's fast-lane tests: it speaks the
 * subset of RFC 4918 the driver uses — `MKCOL`, `PROPFIND`, `GET`, `HEAD`, `PUT`
 * and `DELETE`, behind HTTP Basic auth — and issues an ETag per write, honouring
 * `If-None-Match: *`, `If-Match` and the DAV `If` header with `412 Precondition
 * Failed`. A
 * write returns its ETag by default; with [omitEtagOnPut] it withholds it, as
 * Apache `mod_dav` does, so the driver's HEAD fallback is exercised without
 * Docker.
 *
 * It lives in `testkit` because a test source set may hold only `*Test.kt` files
 * (docs/testing.md#naming-and-placement). The containerised `mod_dav` run is the
 * real integration lane (ticket #27); this is the fast twin.
 */
class FakeWebDavServer(username: String = "lekto", password: String = "lekto") : AutoCloseable {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val collections = linkedSetOf("/")
    private val resources = linkedMapOf<String, Stored>()
    private val sequence = AtomicInteger()

    /** The URL a driver should point at; collections below it are created on demand. */
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    /** When set, the next request answers this status instead of being handled. */
    var failNextRequestWith: Int? = null

    /** When true a `PUT` omits its `ETag`, so the driver must fall back to `HEAD`. */
    var omitEtagOnPut: Boolean = false

    /**
     * The status a `MKCOL` on a collection that already exists answers. RFC 4918
     * says `405`, but servers vary — Infomaniak kDrive answers `404`, even for the
     * drive root — so a driver must read existence instead of trusting this.
     */
    var mkcolExistingStatus: Int = 405

    private val expectedAuthorization: String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))

    init {
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    override fun close() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (!authorized(exchange)) {
                exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=\"lekto\"")
                respond(exchange, 401)
                return
            }
            failNextRequestWith?.let { status ->
                failNextRequestWith = null
                respond(exchange, status)
                return
            }
            dispatch(exchange)
        } finally {
            exchange.close()
        }
    }

    /** Runs the handler for the request's WebDAV method. */
    private fun dispatch(exchange: HttpExchange) {
        val path = pathOf(exchange)
        when (exchange.requestMethod.uppercase()) {
            "MKCOL" -> mkcol(exchange, path)
            "PROPFIND" -> propfind(exchange, path)
            "GET" -> get(exchange, path)
            "HEAD" -> head(exchange, path)
            "PUT" -> put(exchange, path)
            "DELETE" -> delete(exchange, path)
            "MOVE" -> move(exchange, path)
            else -> respond(exchange, 405)
        }
    }

    private fun authorized(exchange: HttpExchange): Boolean =
        exchange.requestHeaders.getFirst("Authorization") == expectedAuthorization

    private fun mkcol(exchange: HttpExchange, path: String) {
        val parent = parentOf(path)
        when {
            path in collections -> respond(exchange, mkcolExistingStatus)

            parent !in collections -> respond(exchange, 409)

            else -> {
                collections += path
                respond(exchange, 201)
            }
        }
    }

    private fun propfind(exchange: HttpExchange, path: String) {
        if (path !in collections) {
            respond(exchange, 404)
            return
        }
        val children = resources.keys.filter { parentOf(it) == path }.sorted()
        respond(exchange, 207, multistatus(path, children).toByteArray(), "application/xml; charset=utf-8")
    }

    private fun get(exchange: HttpExchange, path: String) {
        val stored = resources[path] ?: return respond(exchange, 404)
        exchange.responseHeaders.add("ETag", stored.etag)
        respond(exchange, 200, stored.bytes, "application/octet-stream")
    }

    private fun head(exchange: HttpExchange, path: String) {
        val stored = resources[path] ?: return respond(exchange, 404)
        exchange.responseHeaders.add("ETag", stored.etag)
        exchange.sendResponseHeaders(200, -1)
    }

    private fun put(exchange: HttpExchange, path: String) {
        val current = resources[path]
        when {
            parentOf(path) !in collections -> respond(exchange, 409)
            preconditionFails(exchange, current) -> respond(exchange, 412)
            else -> store(exchange, path, current != null)
        }
    }

    /** Whether a conditional header rejects the write: `If-None-Match: *`, `If-Match`, or the DAV `If`. */
    private fun preconditionFails(exchange: HttpExchange, current: Stored?): Boolean {
        val ifNoneMatch = exchange.requestHeaders.getFirst("If-None-Match")
        val ifMatch = exchange.requestHeaders.getFirst("If-Match")
        val ifHeader = exchange.requestHeaders.getFirst("If")
        return when {
            ifNoneMatch == "*" -> current != null
            ifMatch != null -> ifMatch != current?.etag
            ifHeader != null -> opaque(etagOf(ifHeader)) != current?.let { stored -> opaque(stored.etag) }
            else -> false
        }
    }

    /** The entity-tag a DAV `If` condition names, e.g. `([W/"abc"])` → `W/"abc"`. */
    private fun etagOf(ifHeader: String): String = ifHeader.substringAfter('[', "").substringBefore(']', "")

    /** The opaque tag, with any `W/` weakness marker removed for a weak comparison (RFC 4918 §10.4.1). */
    private fun opaque(etag: String): String = etag.removePrefix("W/")

    private fun store(exchange: HttpExchange, path: String, replaced: Boolean) {
        val etag = "\"s${sequence.incrementAndGet()}\""
        resources[path] = Stored(exchange.requestBody.readBytes(), etag)
        if (!omitEtagOnPut) exchange.responseHeaders.add("ETag", etag)
        respond(exchange, if (replaced) 204 else 201)
    }

    private fun delete(exchange: HttpExchange, path: String) {
        if (resources.remove(path) == null) respond(exchange, 404) else respond(exchange, 204)
    }

    private fun move(exchange: HttpExchange, path: String) {
        val stored = resources[path] ?: return respond(exchange, 404)
        val destination = destinationOf(exchange) ?: return respond(exchange, 400)
        resources.remove(path)
        resources[destination] = stored
        respond(exchange, 201)
    }

    /** The path the `Destination` header names, e.g. a `MOVE`'s new name. */
    private fun destinationOf(exchange: HttpExchange): String? {
        val destination = exchange.requestHeaders.getFirst("Destination") ?: return null
        val raw = URI(destination).path
        return if (raw.length > 1) raw.trimEnd('/') else raw
    }

    /**
     * Places [bytes] at [path] — a root-relative path such as
     * `/base/attachments/x.bin` — creating the collections above it, so a test
     * can plant a file the driver did not write, a legacy `.bin` say (issue #116).
     */
    fun seed(path: String, bytes: ByteArray) {
        var parent = parentOf(path)
        while (parent !in collections) {
            collections += parent
            if (parent == "/") break
            parent = parentOf(parent)
        }
        resources[path] = Stored(bytes, "\"s${sequence.incrementAndGet()}\"")
    }

    private fun multistatus(collection: String, children: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="utf-8"?>""")
        append("""<D:multistatus xmlns:D="DAV:">""")
        append(responseEntry(if (collection == "/") "/" else "$collection/", null))
        children.forEach { append(responseEntry(it, resources.getValue(it).etag)) }
        append("</D:multistatus>")
    }

    private fun responseEntry(href: String, etag: String?): String {
        val prop = if (etag == null) {
            "<D:resourcetype><D:collection/></D:resourcetype>"
        } else {
            "<D:getetag>$etag</D:getetag>"
        }
        return """<D:response><D:href>${xmlEscape(href)}</D:href>""" +
            """<D:propstat><D:prop>$prop</D:prop>""" +
            """<D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"""
    }

    private fun xmlEscape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: ByteArray = ByteArray(0),
        contentType: String? = null,
    ) {
        contentType?.let { exchange.responseHeaders.add("Content-Type", it) }
        if (body.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun pathOf(exchange: HttpExchange): String {
        val raw = exchange.requestURI.path
        return if (raw.length > 1) raw.trimEnd('/') else raw
    }

    private fun parentOf(path: String): String = path.substringBeforeLast('/', "").ifEmpty { "/" }

    private data class Stored(val bytes: ByteArray, val etag: String)
}
