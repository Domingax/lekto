# The WebDAV driver: a per-item collection, ETag revisions, and retryable failure

ADR-0009 puts synchronisation behind the `SyncTarget` seam and ADR-0024 fixes
that seam's shape while leaving the remote layout to the driver: "WebDAV (ticket
#27) decides it." This is that driver. It is the first real one, so its choices
become a compatibility surface the moment a user syncs.

## The remote layout

The driver maps the vault onto a WebDAV collection addressed by a base URL and
HTTP Basic credentials — a username and an **application password**, which is
what Nextcloud and its kin call a per-device password, so no OAuth registration
and no maintainer secret are involved and forks are unaffected:

- `<root>/records/<base64url(id)>.json` — one file per item, holding the
  `VersionedRecord`'s JSON: a live `VaultRecord` or a `Tombstone`. One file per
  item is what gives each item its own ETag and therefore its own compare-and-
  swap; a single JSON document for the whole vault could not.
- `<root>/attachments/<base64url(id)>.bin` — one file per book original
  (ADR-0016).

The id is Base64-URL encoded, so any vault id becomes one safe path segment with
no collisions, and the `.json`/`.bin` suffixes keep the record and attachment
channels apart. Collections are created on demand with `MKCOL`, tolerating the
`405` of a collection that already exists.

## Revisions and conditional writes

An item's revision is its **ETag**. A write is conditioned: create-if-absent sends
`If-None-Match: *`, an update sends WebDAV's `If` header (`If: ([<etag>])`), and a
`412 Precondition Failed` becomes `WriteOutcome.Conflicted` — the value the engine
settles — never a silent overwrite. The driver therefore reports
`conditionalWrites = true`.

The update does **not** use HTTP `If-Match`, which requires a strong ETag
comparison: Apache `mod_dav` marks a file's ETag weak for the first second after a
write (`ap_make_etag_ex` cannot rule out a change within the same second), so an
immediate `If-Match` update is rejected. WebDAV's `If` header compares ETags
weakly (RFC 4918 §10.4.1), so it conditions correctly on both weak and strong
ETags; `dav_validate_request` evaluates it in `dav_validate_resource_state`.

A `PUT` response may omit an ETag (Apache's does), so after a write the driver
reads the revision from the response's `ETag` header or, when that is absent, from
a `HEAD`. A revision it cannot read is a failure, not a guess.

RFC 6578 `sync-collection` is server-dependent, so **no change cursor is
claimed** (`changeCursor = false`); the engine lists the whole collection and
diffs it. Capabilities are the contract's gate: the `SyncTargetContract` only
adds a case for a capability the driver claims, so a driver that claimed a cursor
or a compare-and-swap it did not honour fails the case it is asked to pass.

## Failure is typed and retryable

Transport failures, unexpected statuses and unreadable documents raise
`SyncTargetException`, a new domain type in `core.sync`. It aborts the run so
the caller retries, and it never carries a partial listing or a credential — the
alternative, returning an empty or truncated result, is silent data loss. A
conflicting write stays a value, not an exception, because the engine recovers it.

## Implementation

The client speaks HTTP over `java.net.http` (JDK 11+). `HttpURLConnection` rejects
`PROPFIND` and `MKCOL` outright — `ProtocolException: Invalid HTTP method`, the
two verbs WebDAV listings and collection creation need — so the JDK's newer client
is the dependency-free choice. The listing's XML is parsed with external entities
and doctypes disabled, because it arrives from the network.

## Consequences

The layout is now a compatibility surface: changing a path, an extension or the id
encoding breaks syncing against a collection a user already populated, so such a
change needs care and probably a migration. A server that issues no strong ETag
cannot honour the conditional contract and must not be offered. With no change
cursor, every sync lists every item; a cursor is a later, server-gated
optimisation. The driver is JVM-only for now (desktop); the Android client reaches
it only when a later ticket gives the module an Android target.

## Rejected

Storing the whole vault in one JSON document (no per-item ETag, so no per-item
compare-and-swap to catch a lost update); embedding the record in a DAV dead
property read back by `PROPFIND` (server-dependent persistence, no gain over the
`GET` the driver already needs); mirroring the vault as a plain folder of files
without ETag preconditions (WebDAV's whole advantage); OkHttp (a new dependency
where the JDK client suffices); claiming an RFC 6578 cursor (Apache `mod_dav` does
not offer one, so the claim would fail the contract).

## Update — a collection's existence is read, not inferred from MKCOL (issue #117)

The driver no longer sends `MKCOL` for a collection that already exists. It reads
existence with a zero-depth `PROPFIND` and only creates what it read as absent.
Servers disagree on the status a redundant `MKCOL` answers: RFC 4918 says `405`,
which the driver tolerated, but Infomaniak kDrive answers `404` even for the drive
root — so a correctly configured account failed with *"could not create collection
''"* before any sync. Reading existence makes the driver independent of that
status. The remote layout, the ETag revisions and the conditional-write contract
are unchanged.

## Update — the module gains an Android target (issue #116)

The "JVM-only for now" consequence below is superseded: `integrations/webdav` now
also targets Android, so **Settings → Sync** is usable on the first-class client.
The HTTP call moved behind a `WebDavTransport` seam — `java.net.http` on the JVM
(unchanged), OkHttp on Android, which performs the `PROPFIND`/`MKCOL` methods
`HttpURLConnection` refuses. ADR-0026 records that decision. The remote layout,
the ETag revisions and the conditional-write contract are unchanged.

## Update — the attachment suffix is `.data`, not `.bin` (issue #116)

The layout's second line above is superseded: a book original is now
`<root>/attachments/<base64url(id)>.data`. Koofr's WebDAV accepts a `.bin`
upload (`PUT` answers `201`) but **refuses to serve it back** — a `GET` returns
the headers and then closes the connection, so the driver saw an I/O failure and
an imported book could never follow its record to another device. Every other
suffix tested (`.data`, `.dat`, `.blob`, `.raw`, no extension) is served, so the
channel moved to `.data`. This is a remote-layout change and therefore a
compatibility break for a collection a user already populated with `.bin` files:
they are simply ignored, and the originals re-upload as `.data` on the next sync.
The change landed before the feature shipped, so no released client depends on
the old suffix.

