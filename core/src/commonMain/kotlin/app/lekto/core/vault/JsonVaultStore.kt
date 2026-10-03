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
 *
 * Binary content is a record's **attachment**, one file per record under the
 * reserved `_attachments` directory (ADR-0016). An attachment has no independent
 * identity: [remove] takes it with the record, and [exportBundle] carries it
 * with the bundle.
 */
class JsonVaultStore(private val files: VaultFileSystem) : VaultStore {

    private val index = VaultIndex(files)
    private val attachments = VaultAttachments(files)

    override fun put(record: VaultRecord) {
        index.writeRecord(record)
        index.replaceManifest(index.manifest().records)
    }

    override fun get(id: String): VaultRecord? = index.find(id)

    override fun remove(id: String) {
        val ref = index.manifest().records.firstOrNull { it.id == id }
        if (ref != null) {
            files.delete(index.pathOf(ref.kind, ref.id))
            index.replaceManifest(index.manifest().records.filterNot { it.id == id })
        }
        attachments.remove(id)
    }

    override fun all(): List<VaultRecord> = index.records()

    override fun manifest(): VaultManifest = index.manifest()

    override fun putAttachment(id: String, bytes: ByteArray) = attachments.put(id, bytes)

    override fun getAttachment(id: String): ByteArray? = attachments.get(id)

    override fun removeAttachment(id: String) = attachments.remove(id)

    override fun exportBundle(): ByteArray = VaultCodec.encodeBundle(
        VaultBundle(
            manifest = index.manifest(),
            records = all(),
            attachments = attachments.encodeAll(),
        ),
    ).encodeToByteArray()

    override fun importBundle(bytes: ByteArray) {
        val bundle = VaultCodec.decodeBundle(bytes.decodeToString())
        bundleProblem(bundle)?.let { throw VaultFormatException(it) }
        bundle.records.forEach(index::writeRecord)
        val ids = bundle.records.map { it.id }.toSet()
        index.records().filter { it.id !in ids }.forEach { files.delete(index.pathOf(it.kind, it.id)) }
        index.replaceManifest(bundle.manifest.records)
        attachments.replaceAll(bundle.attachments)
    }

    /** Why [bundle] cannot be imported, or `null` when it is sound. */
    private fun bundleProblem(bundle: VaultBundle): String? = when {
        bundle.formatVersion != VaultFormat.VERSION ->
            "unsupported vault format ${bundle.formatVersion}; this build reads ${VaultFormat.VERSION}"

        bundle.manifest.records.toSet() != bundle.records.map(RecordRef::of).toSet() ->
            "the export's manifest and records disagree"

        bundle.records.map { it.id }.toSet().size != bundle.records.size ->
            "the export carries more than one record with the same id"

        else -> null
    }
}
