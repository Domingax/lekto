package app.lekto.integrations.webdav

import kotlin.io.encoding.Base64

/**
 * The remote layout's names: where an item's file lives and what an id looks
 * like as a path segment. Ids are Base64-URL-encoded so any vault id — spaces,
 * slashes, non-ASCII — becomes a safe, collision-free file name, and the
 * `.json`/`.bin` suffixes keep the record and attachment channels apart.
 */
internal object WebDavNames {

    /** The collection holding one JSON file per item. */
    const val RECORDS: String = "records"

    /** The collection holding one binary file per book original. */
    const val ATTACHMENTS: String = "attachments"

    /** The record file for [id]. */
    fun recordPath(id: String): String = "$RECORDS/${encode(id)}$RECORD_SUFFIX"

    /** The attachment file for [id]. */
    fun attachmentPath(id: String): String = "$ATTACHMENTS/${encode(id)}$ATTACHMENT_SUFFIX"

    /** The id an attachment path names, or `null` when it names something else. */
    fun idFromAttachment(path: String): String? =
        if (path.startsWith("$ATTACHMENTS/") && path.endsWith(ATTACHMENT_SUFFIX)) {
            decode(path.removePrefix("$ATTACHMENTS/").removeSuffix(ATTACHMENT_SUFFIX))
        } else {
            null
        }

    /**
     * Whether [path] is a record file. A listing can carry a stray file the
     * driver did not write, and one is not a vault record to abort a sync over.
     */
    fun isRecord(path: String): Boolean = path.startsWith("$RECORDS/") && path.endsWith(RECORD_SUFFIX)

    /** An id as a URL-safe path segment. */
    fun encode(id: String): String = URL_SAFE.encode(id.toByteArray(Charsets.UTF_8))

    /** The id a path segment names, or `null` when it is not Base64-URL. */
    fun decode(name: String): String? = try {
        URL_SAFE.decode(name).decodeToString()
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val RECORD_SUFFIX = ".json"
    private const val ATTACHMENT_SUFFIX = ".bin"

    /** The URL-safe alphabet with no padding, so a name is one path segment. */
    private val URL_SAFE: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
}
