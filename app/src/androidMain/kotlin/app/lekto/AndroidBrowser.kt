package app.lekto

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens [url] in the Android device's browser (issue #19) — the one platform
 * action behind the lookup panel's reference shortcuts. The panel deep-links
 * out; Lekto never embeds or scrapes the site.
 *
 * Best-effort by design: a device with no browser, or a malformed URL, leaves the
 * reading session uninterrupted rather than crashing.
 */
fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
