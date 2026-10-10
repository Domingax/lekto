# The WebDAV driver gains an Android target behind a transport seam

ADR-0025 shipped the WebDAV driver as a **JVM-only** module and recorded the
deferral: "the Android client reaches it only when a later ticket gives the module
an Android target." This is that target, and the transport decision it needs. It
is the change that makes **Settings → Sync** work on the first-class client, so
it touches the module graph, the shipped Android classpath and the licence gate.

## The problem

The driver speaks HTTP over `java.net.http` (JDK 11+), which ADR-0025 chose
because `HttpURLConnection` rejects `PROPFIND` and `MKCOL`
(`ProtocolException: Invalid HTTP method`). **Android ships no `java.net.http`**,
so the driver cannot compile or run there, and the settings section honestly said
*"Sync isn't available in this build"* on the client the product leads with.

The remote layout, the ETag revisions and the conditional-write contract are
unchanged and remain ADR-0025's. Only the transport — the one platform call —
differs.

## Decision

Give `integrations/webdav` an **Android target** alongside its JVM one, and put
the HTTP call behind a small seam:

- **`WebDavTransport`** (in `commonMain`) is the whole platform surface: it takes
  a platform-neutral `WebDavRequest` (method, URL, headers, body bytes) and
  returns a `WebDavResponse` (status, `ETag`, body). No connection pool, redirect
  policy or cookie store leaks through it.
- The driver's logic — the remote layout, the conditional writes, the
  Multi-Status parse, the record codec — lives in a shared `jvmSharedMain` source
  set compiled once for both targets, so the two clients cannot drift.
- `defaultWebDavTransport()` is an `expect`/`actual`: **`java.net.http` on the
  JVM** (dependency-free, ADR-0025's choice, unchanged) and **OkHttp on Android**
  (it performs arbitrary HTTP methods, which `HttpURLConnection` refuses).

Both implementations run the **same `SyncTargetContract`**, so the seam is proved
by behaviour, not by inspection: the JVM lane runs it against the in-process
`FakeWebDavServer` and against a `mod_dav` container; the Android host lane runs
it against the same `FakeWebDavServer` under Robolectric, on the runtime Android
uses, without Docker and without a network.

## OkHttp on Android

OkHttp (Apache-2.0, AGPL-compatible; ADR-0011) is the Android transport because
it is the maintained, Android-first HTTP client that performs any method. The
JVM keeps `java.net.http` and never carries OkHttp, so the dependency ships only
where it is needed.

It is pinned at **5.4.0**: 5.5.0's Android artifact declares `minCompileSdk=37`,
and this repository is pinned to `compileSdk` 36 (AGP 8.13.2). A later OkHttp
needs a compile-SDK and AGP bump first; the version is held in the version
catalog like every other.

## The fake server runs through the Robolectric host lane

`testkit`'s `FakeWebDavServer` is an in-process server built on
`com.sun.net.httpserver`, which is a **host-JVM** API Android does not ship. It
stays in `testkit`'s `jvmMain` and is not given an Android target; the Android
host lane consumes it through Kotlin Multiplatform's Android-to-JVM dependency
compatibility and Robolectric runs it on the host JVM. That keeps the fake a
single implementation and avoids putting a JDK-only server class on the Android
classpath, where it would never work on a device anyway.

## Consequences

- The driver is now a shipped part of the Android app: its `androidMain` and the
  OkHttp dependency are on the app's production classpath and held to the licence
  gate, and a regression there breaks the first-class client, not only desktop.
- The transport seam is the only place a platform HTTP stack enters the driver.
  A second driver, or a second Android transport, slots in behind the same
  interface and must pass the same contract.
- Android's `minSdk` is 24 and `java.util.Base64` is API 26+, so the layout's id
  encoding and the Basic credentials use Kotlin's own `Base64` (as `core` already
  does), which is portable and removes the API-level trap.

## Rejected

- **`HttpURLConnection` on Android** — it rejects `PROPFIND`/`MKCOL`, the two
  verbs a WebDAV driver cannot do without (the reason ADR-0025 rejected it too).
- **OkHttp on the JVM as well** — a new dependency where the JDK client already
  works; the seam is what makes the split cheap, so there is no reason to pay it.
- **A hand-rolled HTTP stack on Android** — reimplementing a client to avoid one
  well-licensed, Android-first dependency is not a trade worth making.
- **An Android target for `testkit`** — the fake server is host-JVM-only, so an
  Android variant would compile a class that could never run on a device.
