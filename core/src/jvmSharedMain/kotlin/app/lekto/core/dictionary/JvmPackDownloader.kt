package app.lekto.core.dictionary

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

/**
 * The platform [PackDownloader]: it streams the gzipped pack over HTTP, gunzips
 * it into a temporary neighbour and renames it into place, so the whole pack
 * never sits on the heap and a failed download leaves no partial file (ADR-0017).
 *
 * It is shared by the desktop and Android targets — `java.net` and
 * `java.util.zip` are the same API on both — so only the SQLite reader differs.
 * Redirects are followed, which is what a GitHub release asset needs: the stable
 * `releases/latest/download/…` URL answers with a redirect to the object store.
 */
class JvmPackDownloader(
    private val connect: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
) : PackDownloader {

    @Suppress("TooGenericExceptionCaught") // Any failure must clean up the temporary file, then rethrow it.
    override fun download(url: String, destination: String) {
        val target = File(destination)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.download")
        try {
            stream(url, temporary)
            replace(temporary, target)
        } catch (failure: Throwable) {
            if (temporary.exists() && !temporary.delete()) {
                failure.addSuppressed(IOException("could not remove the partial download at '${temporary.path}'"))
            }
            throw failure
        }
    }

    private fun stream(url: String, temporary: File) {
        val connection = connect(url).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in HTTP_OK until HTTP_REDIRECT) {
                throw IOException("the dictionary download failed with HTTP $code")
            }
            connection.inputStream.buffered().use { raw -> decompress(raw, temporary) }
        } finally {
            connection.disconnect()
        }
    }

    private fun decompress(raw: InputStream, temporary: File) {
        GZIPInputStream(raw).use { gzip ->
            temporary.outputStream().buffered().use { gzip.copyTo(it) }
        }
    }

    private fun replace(temporary: File, target: File) {
        if (temporary.renameTo(target)) return
        if (target.delete() && temporary.renameTo(target)) return
        throw IOException("could not install the dictionary pack at '${target.path}'")
    }

    private companion object {
        /** A whole-pack read may be slow; the connect and read timeouts are generous but finite. */
        const val TIMEOUT_MILLIS = 30_000
        const val HTTP_OK = 200
        const val HTTP_REDIRECT = 300
    }
}
