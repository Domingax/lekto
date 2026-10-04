package app.lekto

import java.awt.Desktop
import java.net.URI

/**
 * Opens [url] in the desktop's default browser (issue #19) — the one platform
 * action behind the lookup panel's reference shortcuts. The panel deep-links
 * out; Lekto never embeds or scrapes the site.
 *
 * Best-effort by design: a desktop without browser support, or a malformed URL,
 * leaves the reading session uninterrupted rather than throwing. [browse] is the
 * seam a test injects; production uses [desktopBrowse].
 */
fun openInBrowser(url: String, browse: (URI) -> Unit = ::desktopBrowse) {
    runCatching { browse(URI(url)) }
}

private fun desktopBrowse(uri: URI) {
    if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(uri)
}
