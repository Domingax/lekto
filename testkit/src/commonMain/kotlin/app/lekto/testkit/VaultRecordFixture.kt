package app.lekto.testkit

import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VaultRecord
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * A [VaultRecord] for tests: deterministic in every field, so a record built in
 * one suite equals one built in another, and a failure always reproduces.
 *
 * The schema version is fixed because no behaviour under test varies it; the
 * kind, timestamp and device id are the fields whose variation a store or a
 * merge (ticket #14) needs, so each is a parameter. The body names the id, which
 * is enough for a test to tell two records apart.
 */
fun testVaultRecord(
    id: String,
    kind: String = "vocabulary",
    updatedAtMillis: Long = 0,
    device: String = "device-a",
): VaultRecord = VaultRecord(
    id = id,
    kind = kind,
    schemaVersion = 1,
    updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
    deviceId = DeviceId(device),
    body = buildJsonObject { put("text", id) },
)
