package app.lekto.integrations.webdav

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Where a driver syncs and as whom: the base URL, and the HTTP Basic credentials
 * — a username and an application password, which is what a self-hosted
 * Nextcloud, ownCloud or Synology issues per device. They travel together
 * everywhere, so they are one value.
 *
 * This is a plain class, **not** a data class, on purpose: the credentials are
 * folded into [authorization] at construction and never exposed, so no generated
 * `toString` can leak a password into a log.
 */
internal class WebDavConnection(baseUrl: String, username: String, password: String) {

    /** The root collection, without a trailing slash. */
    val baseUrl: String = baseUrl.trimEnd('/')

    /** The `Authorization` header value, built once from the credentials. */
    val authorization: String = "Basic " + Base64.getEncoder().encodeToString(
        "$username:$password".toByteArray(StandardCharsets.UTF_8),
    )
}
