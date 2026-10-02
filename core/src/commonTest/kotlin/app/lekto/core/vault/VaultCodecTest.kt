package app.lekto.core.vault

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlin.time.Instant

/**
 * The codec's own corners the store round-trips do not reach: the instant
 * serializer's descriptor, and a record whose body is left at its default.
 */
class VaultCodecTest :
    FunSpec({

        test("the instant serializer describes itself as a string") {
            InstantIso8601Serializer.descriptor.kind shouldBe PrimitiveKind.STRING
        }

        test("a record's body defaults to empty and still round-trips") {
            val record = VaultRecord(
                id = "a",
                kind = "vocabulary",
                schemaVersion = 1,
                updatedAt = Instant.fromEpochMilliseconds(0),
                deviceId = DeviceId("device-a"),
            )

            VaultCodec.decodeRecord(VaultCodec.encodeRecord(record)) shouldBe record
        }
    })
