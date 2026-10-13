package app.lekto.core.language

import app.lekto.core.http.withHttpGet

/**
 * The platform [ManifestDownloader]: it fetches the published **Language
 * manifest** — a small, raw JSON document — over HTTP and returns its bytes
 * (issue #146). Unlike the pack, the manifest is not compressed, so the whole
 * document is read into memory; it is small and parsed once.
 *
 * It is shared by the desktop and Android targets, and blocking: the application
 * runs [download] off the UI thread.
 */
class JvmManifestDownloader : ManifestDownloader {

    override fun download(url: String): ByteArray = withHttpGet(url) { body -> body.readBytes() }
}
