package app.lekto.core.vault

/**
 * The default [VaultStore]: one JSON file per record plus a manifest, over any
 * [VaultFileSystem] (ADR-0003).
 *
 * The store owns the format, not the storage. On Android and desktop the
 * composition root hands it a filesystem rooted at an app-private directory
 * (ADR-0010); tests hand it an in-memory filesystem, so the same logic is
 * exercised everywhere and the platform is the only thing that differs.
 *
 * Reads go to the record files, so the store is consistent even if a manifest
 * write fails; the manifest is an index that is rewritten on every change.
 */
class JsonVaultStore(private val files: VaultFileSystem) : VaultStore {

    private val index = VaultIndex(files)

    override fun put(record: VaultRecord) {
        index.writeRecord(record)
        index.replaceManifest(index.manifest().records)
    }

    override fun get(id: String): VaultRecord? = index.find(id)

    override fun remove(id: String) {
        val ref = index.manifest().records.firstOrNull { it.id == id } ?: return
        files.delete(index.pathOf(ref.kind, ref.id))
        index.replaceManifest(index.manifest().records.filterNot { it.id == id })
    }

    override fun all(): List<VaultRecord> = index.records()

    override fun manifest(): VaultManifest = index.manifest()

    override fun exportBundle(): ByteArray =
        VaultCodec.encodeBundle(VaultBundle(manifest = index.manifest(), records = all())).encodeToByteArray()

    override fun importBundle(bytes: ByteArray) {
        val bundle = VaultCodec.decodeBundle(bytes.decodeToString())
        if (bundle.formatVersion != VaultFormat.VERSION) {
            throw VaultFormatException(
                "unsupported vault format ${bundle.formatVersion}; this build reads ${VaultFormat.VERSION}",
            )
        }
        if (bundle.manifest.records.toSet() != bundle.records.map(RecordRef::of).toSet()) {
            throw VaultFormatException("the export's manifest and records disagree")
        }
        bundle.records.forEach(index::writeRecord)
        val ids = bundle.records.map { it.id }.toSet()
        index.records().filter { it.id !in ids }.forEach { files.delete(index.pathOf(it.kind, it.id)) }
        index.replaceManifest(bundle.manifest.records)
    }
}
