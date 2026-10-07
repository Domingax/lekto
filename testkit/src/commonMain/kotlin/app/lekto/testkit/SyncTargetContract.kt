package app.lekto.testkit

import app.lekto.core.sync.Revision
import app.lekto.core.sync.SyncTarget
import app.lekto.core.sync.WriteOutcome
import app.lekto.core.vault.VersionedRecord

/**
 * The specification of the [SyncTarget] seam, as executable cases (ticket #26;
 * ADR-0009). A driver passes it, or it is not a driver: the in-memory fake runs
 * it in `core/commonTest`, and every real driver — starting with WebDAV (ticket
 * #27) — runs the same cases.
 *
 * The cases are **capability-gated**: a target that reports no change cursor is
 * not asked to honour one, and a target with no compare-and-swap is not asked to
 * reject a stale revision. The gate only ever *adds* cases, so a driver that
 * claims a capability it lacks fails the case it would have to pass — the
 * honesty the ADR asks for.
 *
 * Each case builds a fresh target from [newTarget], so cases cannot leak state
 * into one another.
 */
class SyncTargetContract(private val newTarget: () -> SyncTarget) {

    /** Every behaviour the seam promises, as `(name, body)` pairs. */
    fun cases(): List<SuspendContractCase> {
        val capabilities = newTarget().capabilities()
        return buildList {
            addAll(baseCases())
            addAll(attachmentCases())
            if (capabilities.changeCursor) addAll(cursorCases())
            if (capabilities.conditionalWrites) addAll(conditionalCases()) else add(unconditionalCase())
        }
    }

    private fun baseCases(): List<SuspendContractCase> = listOf(
        SuspendContractCase("an unknown id reads as null") {
            expectTrue(newTarget().get("absent") == null, "an absent id must read as null")
        },
        SuspendContractCase("a written record reads back unchanged") {
            val target = newTarget()
            val record = testVaultRecord("a", updatedAtMillis = 1)
            target.write(VersionedRecord.of(record))
            expectEquals(VersionedRecord.of(record), target.get("a")?.version, "the record read back")
        },
        SuspendContractCase("a written tombstone reads back unchanged") {
            val target = newTarget()
            val tombstone = testTombstone("a", updatedAtMillis = 1)
            target.write(VersionedRecord.of(tombstone))
            expectEquals(VersionedRecord.of(tombstone), target.get("a")?.version, "the tombstone read back")
        },
        SuspendContractCase("a write replaces the previous version of an id") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 1)))
            val second = testVaultRecord("a", updatedAtMillis = 2)
            target.write(VersionedRecord.of(second))
            expectEquals(VersionedRecord.of(second), target.get("a")?.version, "the replaced record")
            expectEquals(1, target.list().count { it.id == "a" }, "one item per id")
        },
        SuspendContractCase("list returns every written item") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            target.write(VersionedRecord.of(testVaultRecord("b", kind = "progress")))
            expectEquals(listOf("a", "b"), target.list().map { it.id }.sorted(), "the listed ids")
        },
    )

    private fun attachmentCases(): List<SuspendContractCase> = listOf(
        SuspendContractCase("an id with no attachment reads as null") {
            val target = newTarget()
            expectTrue(target.attachment("absent") == null, "an absent attachment must read as null")
            expectTrue("absent" !in target.attachmentIds(), "an absent attachment must not be listed")
        },
        SuspendContractCase("an attachment reads back byte for byte") {
            val target = newTarget()
            target.putAttachment("a", SAMPLE_ATTACHMENT)
            expectTrue(target.attachment("a")?.contentEquals(SAMPLE_ATTACHMENT) == true, "the attachment read back")
            expectTrue("a" in target.attachmentIds(), "the attachment listed")
        },
        SuspendContractCase("putAttachment replaces an existing attachment") {
            val target = newTarget()
            target.putAttachment("a", SAMPLE_ATTACHMENT)
            target.putAttachment("a", REPLACEMENT_ATTACHMENT)
            expectTrue(
                target.attachment("a")?.contentEquals(REPLACEMENT_ATTACHMENT) == true,
                "the replaced attachment",
            )
            expectEquals(1, target.attachmentIds().count { it == "a" }, "one attachment per id")
        },
        SuspendContractCase("writing a tombstone drops the id's attachment") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            target.putAttachment("a", SAMPLE_ATTACHMENT)
            target.write(VersionedRecord.of(testTombstone("a", updatedAtMillis = 2)))
            expectTrue(target.attachment("a") == null, "a deleted id must have no attachment")
            expectTrue("a" !in target.attachmentIds(), "a deleted id must not be listed")
        },
    )

    private fun cursorCases(): List<SuspendContractCase> = listOf(
        SuspendContractCase("a null cursor returns every item and a cursor") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            val all = target.changes(null)
            expectEquals(listOf("a"), all.items.map { it.id }, "the items for a null cursor")
            expectTrue(all.cursor.value.isNotBlank(), "a cursor")
        },
        SuspendContractCase("a cursor returns only the items changed after it") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            val first = target.changes(null)
            target.write(VersionedRecord.of(testVaultRecord("b", kind = "progress")))
            expectEquals(listOf("b"), target.changes(first.cursor).items.map { it.id }, "the changed items")
        },
        SuspendContractCase("a cursor with nothing changed returns no items") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            val cursor = target.changes(null).cursor
            expectTrue(target.changes(cursor).items.isEmpty(), "no items after an unchanged cursor")
        },
    )

    private fun conditionalCases(): List<SuspendContractCase> = listOf(
        SuspendContractCase("a write with the current revision succeeds") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            val edited = VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 2))
            val again = target.put(edited, target.revisionOf("a"))
            expectTrue(again is WriteOutcome.Written, "a write conditioned on the current revision")
        },
        SuspendContractCase("a write with a stale revision is rejected") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            val stale = target.revisionOf("a")
            target.write(VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 2)))
            val outcome = target.put(VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 3)), stale)
            expectTrue(outcome is WriteOutcome.Conflicted, "a stale revision must be rejected")
        },
        SuspendContractCase("creating an id that already exists is rejected") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            val outcome = target.put(VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 2)), null)
            expectTrue(outcome is WriteOutcome.Conflicted, "create-if-absent must reject an existing id")
        },
    )

    private fun unconditionalCase(): SuspendContractCase =
        SuspendContractCase("without conditional writes the revision is ignored") {
            val target = newTarget()
            target.write(VersionedRecord.of(testVaultRecord("a")))
            val outcome = target.put(
                VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 2)),
                Revision("a revision the target never issued"),
            )
            expectTrue(outcome is WriteOutcome.Written, "a target with no compare-and-swap writes unconditionally")
        }

    /** Writes [version] as a fresh item, creating it where absent. */
    private suspend fun SyncTarget.write(version: VersionedRecord) {
        put(version, revisionOf(version.id))
    }

    private suspend fun SyncTarget.revisionOf(id: String): Revision? = get(id)?.revision
}

/** Sample bytes with a leading zero, a high bit and a negative byte, so encoding is exercised. */
private val SAMPLE_ATTACHMENT = byteArrayOf(0, 1, 2, 3, -1, 127)

/** Different bytes, so a replace can be told from the original. */
private val REPLACEMENT_ATTACHMENT = byteArrayOf(0, 1, 2, 3, -1, 126)
