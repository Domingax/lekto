package app.lekto.tools.dictionaries

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** What publishing one built asset knows about it: where it lives, what it hashes to, how big it is. */
private data class PublishedAsset(val url: String, val sha256: String, val sizeBytes: Long)

/**
 * Completes a run's **Language manifest** for publication (issue #305,
 * ADR-0047): every artefact it lists gains the address the app follows, the
 * SHA-256 of the bytes behind it and its compressed size.
 *
 * The manifest is the lock, not an index, so an artefact it names but the run did
 * not produce fails the publication rather than advertising an address that does
 * not resolve. An artefact the run did not produce is already absent from the
 * manifest, so it stays absent.
 */
internal fun publishLanguageManifest(
    manifest: LanguageManifest,
    assets: Path,
    assetBaseUrl: String,
): LanguageManifest {
    val base = assetBaseUrl.trimEnd('/')
    return manifest.copy(
        languages = manifest.languages.map { language ->
            language.copy(
                difficulty = language.difficulty?.let { artifact ->
                    val published = publishedAsset(artifact.name, assets, base)
                    artifact.copy(url = published.url, sha256 = published.sha256, sizeBytes = published.sizeBytes)
                },
                dictionaries = language.dictionaries.map { dictionary ->
                    val published = publishedAsset(dictionary.name, assets, base)
                    dictionary.copy(
                        url = published.url,
                        sha256 = published.sha256,
                        sizeBytes = published.sizeBytes,
                    )
                },
            )
        },
    )
}

private fun publishedAsset(name: String, assets: Path, base: String): PublishedAsset {
    val file = assets.resolve(name)
    require(Files.isRegularFile(file)) {
        "the manifest names '$name', which this run did not produce in $assets"
    }
    val digest = MessageDigest.getInstance(SHA_256)
    var size = 0L
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            size += read
        }
    }
    return PublishedAsset(
        url = "$base/$name",
        sha256 = digest.digest().toHex(),
        sizeBytes = size,
    )
}

private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val value = byte.toInt() and BYTE_MASK
        append(HEX_DIGITS[value ushr NIBBLE_BITS])
        append(HEX_DIGITS[value and NIBBLE_MASK])
    }
}

private const val SHA_256 = "SHA-256"
private const val HEX_DIGITS = "0123456789abcdef"
private const val BYTE_MASK = 0xFF
private const val NIBBLE_MASK = 0x0F
private const val NIBBLE_BITS = 4
private const val BUFFER_BYTES = 1 shl 16
