package app.lekto.integrations.webdav

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Android's [WebDavTransport], over OkHttp. Android ships no `java.net.http`, and
 * `HttpURLConnection` rejects the `PROPFIND` and `MKCOL` methods WebDAV needs, so
 * OkHttp — which performs any HTTP method — is the transport that makes the
 * driver work on the first-class client (ADR-0026). OkHttp is Apache-2.0 and
 * AGPL-compatible (ADR-0011).
 *
 * The request body is attached only when there is one, so OkHttp's own rules for
 * which methods carry a body are respected (`GET`/`HEAD` carry none). A body-less
 * `MKCOL` is a null body, not an empty one.
 */
internal class OkHttpWebDavTransport : WebDavTransport {

    private val client: OkHttpClient = defaultClient()

    override fun execute(request: WebDavRequest): WebDavResponse {
        val builder = Request.Builder().url(request.url).method(request.method, bodyOf(request))
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            return WebDavResponse(
                status = response.code,
                etag = etagOrNull(response.header("ETag")),
                body = response.body.bytes(),
            )
        }
    }

    private fun bodyOf(request: WebDavRequest): RequestBody? = request.body.takeIf { it.isNotEmpty() }?.toRequestBody()

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(WEBDAV_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(WEBDAV_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(WEBDAV_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }
}

/** Android's transport: OkHttp, because Android ships no `java.net.http`. */
internal actual fun defaultWebDavTransport(): WebDavTransport = OkHttpWebDavTransport()
