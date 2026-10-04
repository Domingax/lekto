package app.lekto.testkit

import app.lekto.core.vault.VaultBundle
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultFormat
import app.lekto.core.vault.VaultFormatException
import app.lekto.core.vault.VaultManifest
import app.lekto.core.vault.VaultStore

/** One named behaviour every [VaultStore] must exhibit, and how to run it. */
data class ContractCase(val name: String, val body: () -> Unit)

/**
 * The specification of the [VaultStore] seam, as executable cases.
 *
 * The contract is framework-free on purpose: `testkit` is a plain KMP library,
 * so each consumer registers [cases] with whatever runner its module uses. The
 * in-memory store runs it in `commonTest`, the JVM file store in `jvmTest` —
 * the same nine behaviours, so a driver cannot silently differ (docs/testing.md,
 * "Test levels").
 *
 * Each case builds a fresh store from [newStore], so cases cannot leak state
 * into one another.
 */
class VaultStoreContract(private val newStore: () -> VaultStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unknown id reads as null") { readsUnknownAsNull() },
        ContractCase("a written record reads back unchanged") { readsWrittenRecord() },
        ContractCase("all returns every written record") { listsEveryRecord() },
        ContractCase("put replaces an existing record") { replacesRecord() },
        ContractCase("remove deletes the record and its manifest entry") { removesRecord() },
        ContractCase("the manifest indexes one entry per record") { indexesManifest() },
        ContractCase("an unknown attachment reads as null") { readsUnknownAttachmentAsNull(newStore()) },
        ContractCase("an attachment reads back as its exact bytes") { readsWrittenAttachment(newStore()) },
        ContractCase("removing a record removes its attachment") { removesAttachmentWithRecord(newStore()) },
        ContractCase("attachments survive export then import") { roundTripsAttachments(newStore(), newStore()) },
        ContractCase("export then import restores the vault exactly") { roundTripsBundle() },
        ContractCase("import replaces the vault's existing content") { importReplacesContent() },
        ContractCase("import rejects an unknown format version") { rejectsUnknownFormat() },
    )

    private fun readsUnknownAsNull() {
        expectTrue(newStore().get("absent") == null, "an absent id must read as null")
    }

    private fun readsWrittenRecord() {
        val store = newStore()
        val record = testVaultRecord("a")
        store.put(record)
        expectEquals(record, store.get(record.id), "the record read back")
    }

    private fun listsEveryRecord() {
        val store = newStore()
        val first = testVaultRecord("a")
        val second = testVaultRecord("b", kind = "progress")
        store.put(first)
        store.put(second)
        expectEquals(listOf(first, second).associateBy { it.id }, store.all().associateBy { it.id }, "all records")
    }

    private fun replacesRecord() {
        val store = newStore()
        store.put(testVaultRecord("a", updatedAtMillis = 1))
        val updated = testVaultRecord("a", updatedAtMillis = 2)
        store.put(updated)
        expectEquals(updated, store.get("a"), "the replaced record")
        expectEquals(1, store.all().size, "the number of records after a replace")
    }

    private fun removesRecord() {
        val store = newStore()
        store.put(testVaultRecord("a"))
        store.put(testVaultRecord("b"))
        store.remove("a")
        expectTrue(store.get("a") == null, "a removed record must read as null")
        expectEquals(listOf("b"), store.all().map { it.id }, "the surviving record ids")
        expectEquals(listOf("b"), store.manifest().records.map { it.id }, "the manifest after removal")
    }

    private fun indexesManifest() {
        val store = newStore()
        val first = testVaultRecord("a")
        val second = testVaultRecord("b", kind = "progress")
        store.put(first)
        store.put(second)
        expectEquals(setOf("a", "b"), store.manifest().records.map { it.id }.toSet(), "the indexed ids")
        expectEquals(
            listOf(first, second).associate { it.id to it.updatedAt },
            store.manifest().records.associate { it.id to it.updatedAt },
            "the indexed timestamps",
        )
    }

    private fun roundTripsBundle() {
        val source = newStore()
        source.put(testVaultRecord("a"))
        source.put(testVaultRecord("b", kind = "progress"))
        val restored = newStore()
        restored.importBundle(source.exportBundle())
        expectEquals(source.all().associateBy { it.id }, restored.all().associateBy { it.id }, "the restored records")
        expectEquals(source.manifest(), restored.manifest(), "the restored manifest")
    }

    private fun importReplacesContent() {
        val source = newStore()
        source.put(testVaultRecord("a"))
        val target = newStore()
        target.put(testVaultRecord("stale"))
        target.importBundle(source.exportBundle())
        expectEquals(setOf("a"), target.all().map { it.id }.toSet(), "the records after an import")
    }

    private fun rejectsUnknownFormat() {
        val future = VaultBundle(
            formatVersion = VaultFormat.VERSION + 1,
            manifest = VaultManifest(),
            records = emptyList(),
        )
        val failure = runCatching {
            newStore().importBundle(VaultCodec.encodeBundle(future).encodeToByteArray())
        }.exceptionOrNull()

        expectTrue(failure is VaultFormatException, "an unknown format must be rejected, but threw <$failure>")
    }
}

/** Sample bytes with a leading zero, a high bit and a negative byte, so encoding is exercised. */
private val SAMPLE_ATTACHMENT = byteArrayOf(0, 1, 2, 3, -1, 127)

/** An unknown id has no attachment. */
private fun readsUnknownAttachmentAsNull(store: VaultStore) {
    expectTrue(store.getAttachment("absent") == null, "an absent attachment must read as null")
}

/** An attachment is stored and read back byte for byte, including nulls and high bits. */
private fun readsWrittenAttachment(store: VaultStore) {
    store.put(testVaultRecord("a"))
    store.putAttachment("a", SAMPLE_ATTACHMENT)

    expectTrue(store.getAttachment("a")?.contentEquals(SAMPLE_ATTACHMENT) == true, "the attachment read back")
}

/** An attachment has no identity of its own: removing the record removes it too. */
private fun removesAttachmentWithRecord(store: VaultStore) {
    store.put(testVaultRecord("a"))
    store.putAttachment("a", SAMPLE_ATTACHMENT)
    store.remove("a")

    expectTrue(store.getAttachment("a") == null, "a removed record's attachment must be gone")
}

/** An attachment is part of what an export carries and an import restores. */
private fun roundTripsAttachments(source: VaultStore, restored: VaultStore) {
    source.put(testVaultRecord("a"))
    source.put(testVaultRecord("b", kind = "progress"))
    source.putAttachment("a", SAMPLE_ATTACHMENT)

    restored.importBundle(source.exportBundle())

    expectTrue(restored.getAttachment("a")?.contentEquals(SAMPLE_ATTACHMENT) == true, "the restored attachment")
    expectTrue(restored.getAttachment("b") == null, "an attachment with no record")
}
