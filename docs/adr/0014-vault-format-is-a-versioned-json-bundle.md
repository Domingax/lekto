# The vault format is pure Kotlin over a byte-level file seam; export is one versioned JSON bundle

The vault's **format and the vault's storage** are separate concerns. A `VaultStore`
lives in `commonMain` and owns everything a user would recognise — one JSON file
per record, a `manifest.json` index, and the whole-vault export. A `VaultFileSystem`
is the platform seam that supplies bytes, lists files and guarantees an atomic
write (temp + rename); `JvmVaultFileSystem` roots it in the app-private directory
(ADR-0010). Records serialise with kotlinx.serialization, and the export is a
single `VaultBundle` JSON document carrying a `formatVersion`, the manifest and
every record.

This keeps ADR-0008's "no folder assumptions" honest: `commonMain` names no
`File`, so the web/OPFS `VaultFileSystem` the ADR leaves open is still addable,
and Android and desktop differ only in the root they pass. A single, versioned,
human-readable document is the simplest thing that satisfies "export produces one
file and import restores exactly", and `formatVersion` is the compatibility gate.

We rejected a **ZIP of the vault directory**: the vault's only content so far is
JSON records, and a binary-container layout is a decision better made once book
originals (which are binary) actually need carrying. When they do, a
`formatVersion` bump extends the bundle to attachments; nothing here reverses.

**Consequences**: readers derive the vault from the record files, not the
manifest, so a write that lands a record and fails the manifest — two files cannot
be replaced in one atomic step — can never make that record invisible. The
manifest is an index ADR-0003 asks for, rewritten on every change. Derived assets
never enter either store (ADR-0005), and binary book originals are deferred to
the import work with the version bump above.
