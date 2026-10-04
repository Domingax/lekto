package app.lekto.core.dictionary

/**
 * Downloads the published dictionary pack and writes it, decompressed, at
 * [destination].
 *
 * [destination] is an app-private platform path obtained from the derived-asset
 * seam (ADR-0017); the download streams the gzip archive and decompresses it in
 * place, so the whole pack never sits on the heap. It is a blocking call: the
 * application runs it off the UI thread, exactly as it runs book import.
 */
fun interface PackDownloader {
    fun download(url: String, destination: String)
}
