# The dictionary pack is read in place behind a version handshake, per platform

The dictionary pack is a release artifact (ADR-0011) the app must **download on
demand and query offline**. `core` defines the seam and the query, and each
platform opens the pre-built SQLite pack **in place** by its app-private path
(ADR-0017): `sqlite-jdbc` on the JVM, the Android framework's `SQLiteDatabase`
on Android. The SQL and the row mapping live once, in `SqlDictionaryPack`, and a
tiny `PackDatabase` executes it, so the two clients cannot answer a query
differently.

`DictionaryPackInstaller` owns the lifecycle: it streams the gzipped pack from
the stable `releases/latest/download/lekto-dictionary-en-fr.sqlite.gz` alias,
gunzips it into the **derived store** (ADR-0005) atomically, and validates the
format handshake against `DictionaryPack.FORMAT_VERSION` — the value duplicated
in `tools/dictionaries` and kept in step by an architecture test (ADR-0013). A
pack of another format, or an unreadable one, is refused and removed rather than
left to fail mid-read; a missing pack degrades to an honest "not installed"
message. A lookup collapses a BCP-47 tag to its base language (`en-US` → `en`),
because the pack is keyed by the base tag, and reports honestly when the book's
language is unknown. The pack is a derived asset, so it is structurally outside
the vault and can never reach an export.

We rejected reading the pack as a `ByteArray` (it is 10–30 MiB gzipped and
larger decompressed, exactly the heap risk ADR-0017 exists to avoid), a
pure-Kotlin SQLite reader (a large, error-prone surface for no dependency gain),
and a single KMP SQLite library such as `androidx.sqlite` (a new production
dependency on every platform where the framework and an existing JVM driver
already suffice).

**Consequences**: `core`'s JVM target takes `sqlite-jdbc` (Apache-2.0,
AGPL-compatible, ADR-0011) as a **production** dependency, so it now reaches the
application's licence gate; the Android app supplies the framework SQLite behind
the same seam instead. The architecture test that forbids the domain from naming
Android is scoped to the domain's **shared** sources: its `androidMain`
platform set may name the Android API it backs, as the planned `android.icu`
actual (ADR-0007) will. The dictionary-pack workflow publishes a **date-free
asset alias** beside the dated one so the app can hold one constant download
URL, and the mandatory attribution screen reads its text from the pack's own
metadata.
