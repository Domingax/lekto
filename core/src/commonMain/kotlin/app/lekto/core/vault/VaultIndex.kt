package app.lekto.core.vault

import app.lekto.core.vault.VaultFormat.MANIFEST_FILE

/**
 * The vault's file layout, split from [JsonVaultStore] so each class stays
 * small: this one knows where a record lives and how to read the vault back.
 *
 * The **record files are the source of truth**, not the manifest. The manifest
 * is an index ADR-0003 asks for and is rewritten on every change, but it is
 * derived from the files, so a write that lands the record and fails the
 * manifest (two files cannot be replaced in one atomic step) can never make that
 * record invisible — the reader lists the directory, not the index.
 */
internal class VaultIndex(private val files: VaultFileSystem) {

    /** The index, derived from the record files so it can never be stale. */
    fun manifest(): VaultManifest =
        VaultManifest(records = records().map(RecordRef::of).sortedBy { pathOf(it.kind, it.id) })

    /** Every record in the vault. */
    fun records(): List<VaultRecord> = recordPaths().mapNotNull { read(it) }

    /** The record with [id], or `null` when the vault holds none. */
    fun find(id: String): VaultRecord? {
        val path = recordPaths().firstOrNull { it.endsWith("/$id.${VaultFormat.RECORD_EXTENSION}") }
        return path?.let(::read)
    }

    fun writeRecord(record: VaultRecord) {
        VaultPaths.requireSegment(record.kind, "kind")
        VaultPaths.requireSegment(record.id, "id")
        val target = pathOf(record.kind, record.id)
        files.writeAtomically(target, VaultCodec.encodeRecord(record).encodeToByteArray())
        // A record's identity is its id, so a kind change is a move, not a
        // second record: drop the old path rather than orphan it.
        val stale = "/${record.id}.${VaultFormat.RECORD_EXTENSION}"
        recordPaths().filter { it.endsWith(stale) && it != target }.forEach(files::delete)
    }

    fun replaceManifest(refs: Collection<RecordRef>) {
        val manifest = VaultManifest(records = refs.sortedBy { pathOf(it.kind, it.id) })
        files.writeAtomically(MANIFEST_FILE, VaultCodec.encodeManifest(manifest).encodeToByteArray())
    }

    fun pathOf(kind: String, id: String): String = "$kind/$id.${VaultFormat.RECORD_EXTENSION}"

    private fun recordPaths(): List<String> = files.listFiles()
        .filter { it.endsWith(".${VaultFormat.RECORD_EXTENSION}") && it != MANIFEST_FILE }
        .filterNot { it.startsWith("${VaultFormat.ATTACHMENT_DIRECTORY}/") }

    private fun read(path: String): VaultRecord? = files.read(path)?.let {
        VaultCodec.decodeRecord(it.decodeToString())
    }
}
