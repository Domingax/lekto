# A large, randomly-accessed derived asset is opened by path, not read into memory

The vault's byte seam (`VaultFileSystem`) reads and writes whole `ByteArray`s, which
is right for JSON records, parsed book text and small attachments. The dictionary
pack is different: it is a large, randomly-accessed, read-only artifact. Reading it
wholly into memory to query it would put its entire size on the Android heap for the
life of the process.

We decided that a derived asset which must be randomly accessed may be **stored and
opened by its app-private platform path** through the same seam, instead of being
materialised as bytes. The pack's reader — a pre-built, read-only SQLite file in the
current design — opens it in place, while small derived assets keep the byte methods.
`commonMain` still names no `File`; the path is opaque to it, and the root stays
app-private (ADR-0010), so ADR-0008's folder-independence still holds.

We rejected reading the pack as a `ByteArray`: the trimmed pack is expected at
10–30 MiB gzipped and larger decompressed, and Android's per-process heap makes that a
real risk, not a theoretical one. We rejected a bespoke byte-array format with an
in-memory index for the same reason, and SQLite's `deserialize` API is not dependably
reachable on Android. The pack's **file format is not the contract** — it is versioned
and rebuilt at will — so this decision records only the *access shape*, not SQLite.

**Consequences**: the seam grows a path/streaming surface alongside its byte methods,
and `DerivedAssetStore`'s claim to be "the device-local home of … the dictionary pack"
stays true. A download writes through the same surface. A web/OPFS `VaultFileSystem`
(ADR-0008) would have to supply a comparable handle; nothing here forecloses it.
