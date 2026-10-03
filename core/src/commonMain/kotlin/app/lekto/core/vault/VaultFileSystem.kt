package app.lekto.core.vault

/**
 * The byte-level store the vault is built on: the one platform-specific seam
 * behind it (ADR-0010). `commonMain` never names a folder or a `File`, so the
 * vault is portable to any client that can supply this — an app-private
 * directory on Android, the OS app-data directory on desktop.
 *
 * Paths are `/`-separated segments relative to the store's root. The
 * implementation guarantees [writeAtomically]: a reader either sees the previous
 * bytes or the new bytes, never a partial write (ADR-0003, "write temp, rename").
 */
interface VaultFileSystem {

    /** The bytes at [path], or `null` when nothing lives there. */
    fun read(path: String): ByteArray?

    /**
     * Replaces [path] with [bytes] hermetically: the write lands at a temporary
     * neighbour and is renamed into place, so a concurrent reader can never
     * observe a half-written file.
     */
    fun writeAtomically(path: String, bytes: ByteArray)

    /** Removes [path] if it exists; removing an absent path is not an error. */
    fun delete(path: String)

    /** Every file under the root, as `/`-separated paths relative to it. */
    fun listFiles(): List<String>
}

/**
 * Validates the path segments a record's [VaultRecord.kind] and [id] become.
 *
 * The values are attacker-adjacent only in the sense that an imported vault is
 * untrusted input, and a record whose id is `../../secrets` must not escape the
 * vault root. A segment is conservative: ASCII letters and digits first, then
 * letters, digits, `.`, `_` and `-`.
 */
internal object VaultPaths {

    private val SEGMENT = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    fun requireSegment(value: String, field: String) {
        require(SEGMENT.matches(value)) {
            "$field '$value' is not a safe vault path segment"
        }
    }

    fun requireRelativePath(path: String) {
        require(path.isNotEmpty() && path.split('/').all { SEGMENT.matches(it) }) {
            "'$path' is not a safe vault-relative path"
        }
    }

    /**
     * The vault path of the attachment for record [id]. The `_attachments`
     * directory is not a legal record `kind`, so a record file can never sit at
     * the same path.
     */
    fun attachmentPath(id: String): String {
        requireSegment(id, "attachment id")
        return "${VaultFormat.ATTACHMENT_DIRECTORY}/$id"
    }
}
