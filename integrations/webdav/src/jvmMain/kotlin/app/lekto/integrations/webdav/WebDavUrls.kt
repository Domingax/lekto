package app.lekto.integrations.webdav

import java.net.URI

/**
 * The driver's remote layout as URLs: a root collection, a collection URL (with
 * its trailing slash, which WebDAV collections conventionally carry) and a
 * resource URL, plus the reverse — the item path a listing's `href` names.
 *
 * Ids never appear in a URL verbatim; the target encodes them first, so this
 * only ever joins safe segments onto the root.
 */
internal class WebDavUrls(baseUrl: String) {

    private val base = baseUrl.trimEnd('/')
    private val basePath = URI(base).path.trimEnd('/')

    /** The collection [relative] names, under the root. */
    fun collection(relative: String): String = if (relative.isEmpty()) "$base/" else "$base/$relative/"

    /** The resource [relative] names. */
    fun resource(relative: String): String = "$base/$relative"

    /**
     * The path of the file directly under [collection] that [href] names, or
     * `null` when [href] is the collection itself or lives outside it.
     */
    fun childOf(href: String, collection: String): String? {
        val relative = hrefPath(href)?.removePrefix(basePath)?.trimStart('/') ?: return null
        val prefix = if (collection.isEmpty()) "" else "$collection/"
        return relative.takeIf { it.startsWith(prefix) && it.substringAfterLast('/').isNotEmpty() }
    }

    private fun hrefPath(href: String): String? = try {
        URI(href).path
    } catch (_: IllegalArgumentException) {
        null
    }
}
