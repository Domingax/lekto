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
 * The device id and schema version are fixed because no behaviour under test
 * varies them; [text] is the record's own body, which a test uses to tell two
 * versions of the same record apart.
 */
fun testVaultRecord(
    id: String,
    kind: String = "vocabulary",
    updatedAtMillis: Long = 0,
    text: String = id,
): VaultRecord = VaultRecord(
    id = id,
    kind = kind,
    schemaVersion = 1,
    updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
    deviceId = DeviceId("device-a"),
    body = buildJsonObject { put("text", text) },
)
