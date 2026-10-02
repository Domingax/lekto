package app.lekto.core.vault

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * Serialises a [Instant] as its ISO-8601 string — the form a human reading a
 * record file can understand, which ADR-0003 asks for ("plain, readable
 * records"). [Instant.toString] emits UTC with a `Z` and a fractional second
 * that [Instant.parse] reads back exactly.
 *
 * A custom serializer is declared rather than relying on a library default so
 * the on-disk format is a decision this module owns.
 */
object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.lekto.core.vault.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
