# The vault stores one immutable JSON file per record

User-authored vault data is written as **one JSON file per record** —
`vocabulary/<id>.json`, `progress/<bookId>.json` — plus a `manifest.json`, with atomic
writes (write temp, rename). Each record carries `id`, `schemaVersion`, `updatedAt` and
`deviceId`.

We rejected a single SQLite database as the source of truth: an opaque mutable file
fights byte-level sync tools (Joplin's conflict model and Obsidian's whole-vault
caveats both show this), and a per-record format lets a human read, diff and repair
their own data. SQLite stays available later as a query cache behind the storage seam,
never as the truth.