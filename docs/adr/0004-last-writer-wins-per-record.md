# Sync conflicts resolve last-writer-wins per record

When two devices edit the vault offline, conflicts resolve at the granularity of one
record via `updatedAt`, ties broken by `deviceId`. We rejected CRDTs at this stage: the
machinery is not justified by the MVP's conflict volume, and per-record LWW is exactly
what a file-level sync tool can express. An append-only per-device log
(`log/<device>.jsonl`) is kept optional for traceability, and a CRDT upgrade stays open
behind the storage seam.

**Consequence**: two devices editing the *same* record offline silently overwrite one
another. Accepted for a single-user vault.