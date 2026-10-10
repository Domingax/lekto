package app.lekto.integrations.webdav

/**
 * One HTTP exchange the driver asks the platform to perform, described without
 * naming any platform API so the seam is shared by every client. WebDAV needs
 * methods `HttpURLConnection` rejects (`PROPFIND`, `MKCOL`), so the method is a
 * plain string and the body is bytes; the headers carry the conditional writes
 * and the credentials.
 */
internal data class WebDavRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
)

/**
 * The platform's answer to a [WebDavRequest]: the status, the `ETag` a write
 * condition reads, and the bytes. `etag` is `null` when the server sent none,
 * which the driver treats as a failure rather than a guess.
 */
internal data class WebDavResponse(val status: Int, val etag: String?, val body: ByteArray)

/**
 * An `ETag` header as the seam carries it: `null` when absent or blank, so both
 * transports normalise a missing revision the same way.
 */
internal fun etagOrNull(value: String?): String? = value?.takeIf { it.isNotBlank() }

/** The connect ceiling a transport waits for, in seconds. */
internal const val WEBDAV_CONNECT_TIMEOUT_SECONDS: Long = 15

/** The per-request ceiling a transport waits for, in seconds. */
internal const val WEBDAV_REQUEST_TIMEOUT_SECONDS: Long = 30

/**
 * The one platform call the WebDAV driver makes: perform [request] and return
 * the response, or throw an I/O failure. It is the seam that lets the JVM keep
 * `java.net.http` and Android use a transport that speaks arbitrary methods
 * (OkHttp); because both implementations satisfy this interface and both run the
 * shared `SyncTargetContract`, the two cannot drift (ADR-0026).
 *
 * It is deliberately the whole surface: no connection pooling, no redirect
 * policy, no cookie store leaks through it, so a platform is free to choose.
 */
internal interface WebDavTransport {

    /** Performs [request]; an unreachable server or a broken connection is thrown. */
    fun execute(request: WebDavRequest): WebDavResponse
}

/**
 * The transport this platform ships: `java.net.http` on the JVM, OkHttp on
 * Android. An `expect`/`actual` so the driver never names either and the two
 * cannot be wired by mistake.
 */
internal expect fun defaultWebDavTransport(): WebDavTransport
