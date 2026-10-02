package app.lekto.core.vault

/**
 * The app-private collection of everything the user authored or imported
 * (CONTEXT.md, "Vault"; ADR-0010).
 *
 * Records live one JSON file each — `vocabulary/<id>.json`,
 * `progress/<bookId>.json` — indexed by a `manifest.json` (ADR-0003). This is
 * the seam the rest of the domain talks to; the platform supplies a
 * [VaultFileSystem] and never leaks a folder assumption into `commonMain`.
 *
 * Derived assets do not pass through here at all: parsed text and the dictionary
 * pack belong in a [DerivedAssetStore], which is device-local and never
 * exported (ADR-0005).
 */
interface VaultStore {

    /** Writes [record], creating or replacing the file its `(kind, id)` names. */
    fun put(record: VaultRecord)

    /** The record with [id], or `null` when the vault holds none. */
    fun get(id: String): VaultRecord?

    /** Deletes the record with [id]; an absent id is not an error. */
    fun remove(id: String)

    /** Every record in the vault. */
    fun all(): List<VaultRecord>

    /** The vault index. */
    fun manifest(): VaultManifest

    /**
     * The whole vault as a single file's bytes: all records and the manifest,
     * with nothing else. This is the portability guarantee, so it is always
     * available and contains no [DerivedAssetStore] content.
     */
    fun exportBundle(): ByteArray

    /**
     * Replaces the vault with the bundle in [bytes], restoring exactly what
     * [exportBundle] produced. Existing records not in the bundle are removed.
     *
     * @throws VaultFormatException when the bundle's format version is not
     *   [VaultFormat.VERSION].
     */
    fun importBundle(bytes: ByteArray)
}
