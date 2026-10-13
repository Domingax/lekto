package app.lekto.core.dictionary

import app.lekto.core.http.withHttpGet
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * The platform [PackDownloader]: it streams the gzipped pack over HTTP, gunzips
 * it into a temporary neighbour and renames it into place, so the whole pack
 * never sits on the heap and a failed download leaves no partial file (ADR-0017).
 *
 * It is shared by the desktop and Android targets — `java.net` and
 * `java.util.zip` are the same API on both — so only the SQLite reader differs.
 * The wire rules (redirects, the 2xx check, timeouts) live in
 * [app.lekto.core.http.withHttpGet].
 */
class JvmPackDownloader : PackDownloader {

    @Suppress("TooGenericExceptionCaught") // Any failure must clean up the temporary file, then rethrow it.
    override fun download(url: String, destination: String) {
        val target = File(destination)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.download")
        try {
            withHttpGet(url) { raw -> decompress(raw, temporary) }
            replace(temporary, target)
        } catch (failure: Throwable) {
            if (temporary.exists() && !temporary.delete()) {
                failure.addSuppressed(IOException("could not remove the partial download at '${temporary.path}'"))
            }
            throw failure
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
}
