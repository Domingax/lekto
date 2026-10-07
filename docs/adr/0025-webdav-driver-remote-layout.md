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

An item's revision is its **strong ETag**. A write is conditioned per RFC 7232:
create-if-absent sends `If-None-Match: *`, an update sends `If-Match: <etag>`, and
a `412 Precondition Failed` becomes `WriteOutcome.Conflicted` — the value the
engine settles — never a silent overwrite. Apache `mod_dav` evaluates exactly
these preconditions in `dav_validate_request`, so the driver reports
`conditionalWrites = true`. (Apache 2.4.10–2.4.14 shipped a broken
`ap_condition_if_match` that rejects a matching `If-Match`; fixed in 2.4.16, so
the integration lane must not pin an older server.)

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
