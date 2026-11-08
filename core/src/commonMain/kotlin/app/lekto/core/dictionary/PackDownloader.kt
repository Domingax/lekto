package app.lekto.core.dictionary

/**
 * Downloads the published dictionary pack and writes it, decompressed, at
 * [destination], refusing bytes whose SHA-256 is not [sha256].
 *
 * [destination] is an app-private platform path obtained from the derived-asset
 * seam (ADR-0017); the download streams the gzip archive and decompresses it in
 * place, so the whole pack never sits on the heap. The digest is measured on the
 * compressed bytes — the ones the **Language manifest** names — and checked
 * before the asset is installed, so a downloaded file the manifest did not vouch
 * for never reaches [destination] (issue #305, ADR-0047). A mismatch throws
 * [AssetChecksumMismatch]. The call is blocking: the application runs it off the
 * UI thread, exactly as it runs book import.
 */
fun interface PackDownloader {
    fun download(url: String, destination: String, sha256: String)
}
