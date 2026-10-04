package app.lekto.core.vault

/**
 * The device-local home of everything Lekto can regenerate rather than something
 * the user authored: parsed book text, the dictionary pack (CONTEXT.md, "Derived
 * asset"; ADR-0005).
 *
 * It is deliberately a different store over a different [VaultFileSystem] root
 * than [VaultStore], never a corner of the vault. That is what makes ADR-0005 a
 * structural fact rather than a convention: the export builds from records
 * alone, so nothing written here can reach a bundle, and a record can never
 * name a derived path.
 */
class DerivedAssetStore(private val files: VaultFileSystem) {

    /** Writes [bytes] at the vault-relative [path], atomically. */
    fun put(path: String, bytes: ByteArray) {
        VaultPaths.requireRelativePath(path)
        files.writeAtomically(path, bytes)
    }

    /** The bytes at [path], or `null` when nothing lives there. */
    fun get(path: String): ByteArray? {
        VaultPaths.requireRelativePath(path)
        return files.read(path)
    }

    /** Removes [path] if it exists. */
    fun remove(path: String) {
        VaultPaths.requireRelativePath(path)
        files.delete(path)
    }

    /** Every derived path currently stored. */
    fun paths(): List<String> = files.listFiles()

    /**
     * The app-private platform path of [path], for a derived asset opened in
     * place rather than read into memory (ADR-0017). The value is opaque to the
     * domain; a platform implementation interprets it.
     */
    fun storedPath(path: String): String {
        VaultPaths.requireRelativePath(path)
        return files.storedPath(path)
    }

    /** Drops every derived asset, as a rebuild or a re-download would. */
    fun clear() {
        files.listFiles().forEach(files::delete)
    }
}
