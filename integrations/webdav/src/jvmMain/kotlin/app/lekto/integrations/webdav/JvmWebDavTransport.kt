package app.lekto.integrations.webdav

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The JVM's [WebDavTransport] over `java.net.http` (JDK 11+) rather than
 * `HttpURLConnection`, which rejects the `PROPFIND` and `MKCOL` methods
 * (`ProtocolException: Invalid HTTP method`) — the two verbs WebDAV listings and
 * collection creation need. It is dependency-free, so the desktop client keeps
 * no HTTP library of its own (ADR-0026).
 *
 * An I/O failure is thrown as a [IOException] for the driver to map; an
 * interrupted call restores the interrupt flag so the caller's cancellation is
 * not swallowed.
 */
internal class JvmWebDavTransport : WebDavTransport {

    private val http: HttpClient = defaultClient()

    override fun execute(request: WebDavRequest): WebDavResponse {
        val builder = HttpRequest.newBuilder(URI.create(request.url))
            .method(request.method, bodyPublisher(request.body))
            .timeout(Duration.ofSeconds(WEBDAV_REQUEST_TIMEOUT_SECONDS))
        request.headers.forEach(builder::header)
        val response = try {
            http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("the WebDAV request was interrupted", e)
        }
        return WebDavResponse(
            status = response.statusCode(),
            etag = etagOrNull(response.headers().firstValue("ETag").orElse(null)),
            body = response.body(),
        )
    }

    private fun bodyPublisher(bytes: ByteArray): HttpRequest.BodyPublisher =
        if (bytes.isEmpty()) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofByteArray(bytes)

    private companion object {
        fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(WEBDAV_CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }
}

/** The JVM's transport: `java.net.http`, which ships with the JDK. */
internal actual fun defaultWebDavTransport(): WebDavTransport = JvmWebDavTransport()
