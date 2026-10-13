package app.lekto.core.http

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/**
 * Opens [url] over HTTP, checks for a 2xx answer, and hands the response body to
 * [body] before disconnecting. It is the one place the JVM wire rules live —
 * redirects are followed, because a GitHub release's stable
 * `releases/latest/download/…` alias answers with a redirect — so the pack and
 * the **Language manifest** downloaders cannot drift.
 *
 * It is blocking, and the caller runs it off the UI thread. A non-2xx answer
 * throws [IOException]; so does any transport failure.
 */
internal fun <T> withHttpGet(url: String, body: (InputStream) -> T): T {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = TIMEOUT_MILLIS
        readTimeout = TIMEOUT_MILLIS
        requestMethod = "GET"
    }
    try {
        val code = connection.responseCode
        if (code !in HTTP_OK until HTTP_REDIRECT) throw IOException("the download failed with HTTP $code")
        return connection.inputStream.buffered().use(body)
    } finally {
        connection.disconnect()
    }
}

/** A whole-file read may be slow; the connect and read timeouts are generous but finite. */
private const val TIMEOUT_MILLIS = 30_000
private const val HTTP_OK = 200
private const val HTTP_REDIRECT = 300
