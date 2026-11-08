package app.lekto.core.dictionary

import app.lekto.core.http.withHttpGet
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * The platform [PackDownloader]: it streams the gzipped pack over HTTP, gunzips
 * it into a temporary neighbour, checks the published bytes' SHA-256 and only
 * then renames it into place, so the whole pack never sits on the heap, a failed
 * download leaves no partial file and a mismatched one never reaches the
 * destination (ADR-0017, ADR-0047).
 *
 * It is shared by the desktop and Android targets — `java.net` and
 * `java.util.zip` are the same API on both — so only the SQLite reader differs.
 * The wire rules (redirects, the 2xx check, timeouts) live in
 * [app.lekto.core.http.withHttpGet].
 */
class JvmPackDownloader : PackDownloader {

    @Suppress("TooGenericExceptionCaught") // Any failure must clean up the temporary file, then rethrow it.
    override fun download(url: String, destination: String, sha256: String) {
        val target = File(destination)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.download")
        val digest = MessageDigest.getInstance(SHA_256)
        try {
            withHttpGet(url) { raw -> decompress(DigestInputStream(raw, digest), temporary) }
            if (!digest.digest().toHex().equals(sha256, ignoreCase = true)) throw AssetChecksumMismatch()
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

    private companion object {
        const val SHA_256 = "SHA-256"
    }
}

/** The digest as the manifest writes it: lower-case hex, no separators. */
private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val value = byte.toInt() and BYTE_MASK
        append(HEX_DIGITS[value ushr NIBBLE_BITS])
        append(HEX_DIGITS[value and NIBBLE_MASK])
    }
}

private const val HEX_DIGITS = "0123456789abcdef"
private const val BYTE_MASK = 0xFF
private const val NIBBLE_MASK = 0x0F
private const val NIBBLE_BITS = 4
