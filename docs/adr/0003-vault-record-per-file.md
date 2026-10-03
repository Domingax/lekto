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

## Update — a record's identity is its id alone (issue #16)

The first reading-position record made a latent constraint explicit: a record's
identity is its `id`, not its `(kind, id)` pair, so two records cannot share one
id even under different kinds — the later write replaces the earlier, and the
whole-vault export refuses duplicate ids. A book and its reading position
therefore cannot both use the book's id. The position stays under the `progress/`
kind but derives its own id (the book id plus a `-position` suffix) and carries
the book id in its body, so the layout remains one immutable JSON file per
record. The filename example above is literal for vocabulary; for a reading
position the file is `progress/<bookId>-position.json`.
