package app.lekto.core.vault

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The identifier of the device that authored a [VaultRecord].
 *
 * ADR-0003 stamps every record with a `deviceId`, and ADR-0004 breaks an
 * `updatedAt` tie on it, so two devices converge on the same winner. It is a
 * value class rather than a bare `String` so a record can never be built with a
 * device id and a vocabulary id swapped.
 *
 * The id is stable for the life of the installation; resolving and persisting
 * it is the composition root's job, so the domain only ever receives one.
 */
@Serializable
@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "device id must not be blank" }
    }
}
