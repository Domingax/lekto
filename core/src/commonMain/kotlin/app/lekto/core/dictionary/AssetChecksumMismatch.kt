package app.lekto.core.dictionary

/**
 * Thrown by a [PackDownloader] when the bytes it fetched hash to a different
 * SHA-256 than the one the **Language manifest** published (issue #305,
 * ADR-0047).
 *
 * The bytes are never installed: the downloader refuses before replacing the
 * destination, so a crash cannot leave a mismatched file at an installed path. It
 * is its own type so an installer can report the mismatch honestly without
 * turning a network failure into the same message.
 */
class AssetChecksumMismatch : Exception("the downloaded asset does not match the SHA-256 the manifest published")
