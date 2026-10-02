package app.lekto.testkit

import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.Tombstone
import kotlin.time.Instant

/**
 * A [Tombstone] for tests: the deletion counterpart of [testVaultRecord], built
 * from the same fields so a live version and its tombstone can be compared on
 * `updatedAt` and `deviceId` alone.
 */
fun testTombstone(id: String, updatedAtMillis: Long = 0, device: String = "device-a"): Tombstone = Tombstone(
    id = id,
    updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
    deviceId = DeviceId(device),
)
