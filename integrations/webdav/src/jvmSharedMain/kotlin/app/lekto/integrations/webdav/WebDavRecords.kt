package app.lekto.integrations.webdav

import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VersionedRecord

/**
 * The wire form of one remote item: a [VersionedRecord] — a live record or a
 * tombstone — encoded with the vault's own JSON, so a driver never invents a
 * second record format (ADR-0003, ADR-0024). A file on the target carries the
 * record's bytes and nothing else; its ETag, not its content, is the revision.
 */
internal object WebDavRecords {

    /** Encodes [version] as the bytes of a remote item file. */
    fun encode(version: VersionedRecord): ByteArray =
        VaultCodec.json.encodeToString(VersionedRecord.serializer(), version).encodeToByteArray()

    /** Decodes the bytes of a remote item file. */
    fun decode(bytes: ByteArray): VersionedRecord =
        VaultCodec.json.decodeFromString(VersionedRecord.serializer(), bytes.decodeToString())
}
