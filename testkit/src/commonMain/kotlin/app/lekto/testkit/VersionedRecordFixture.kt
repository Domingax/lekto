package app.lekto.testkit

import app.lekto.core.vault.VersionedRecord

/**
 * A [VersionedRecord] for tests: a live record at [updatedAtMillis] on [device],
 * or — with [deleted] — a deletion with the same envelope. It is the common
 * shape the merge tests build, so the live-or-tombstone choice lives in one
 * place rather than being rebuilt in each suite.
 */
fun testVersionedRecord(
    id: String,
    updatedAtMillis: Long = 0,
    device: String = "device-a",
    deleted: Boolean = false,
): VersionedRecord = if (deleted) {
    VersionedRecord.of(testTombstone(id, updatedAtMillis = updatedAtMillis, device = device))
} else {
    VersionedRecord.of(testVaultRecord(id, updatedAtMillis = updatedAtMillis, device = device))
}
