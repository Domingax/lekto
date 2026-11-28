# Architecture

Lekto is a Kotlin Multiplatform project: `core` is the domain and `app` is the
Compose Multiplatform client for Android and desktop. There is no backend; user
data lives in the **Vault** on the device (ADR-0002).

## Modules

| Module | What it is |
| ------ | ---------- |
| `core` | The domain: vault, records, merge, tokenisation, word identity, vocabulary, import and library, sync engine, parsers, dictionary-pack reader. Production code depends on no other Lekto module. |
| `testkit` | Contract suites and in-memory fakes for the other modules' tests. Test-scoped; never production. |
| `integrations/webdav` | The WebDAV sync driver (JVM and Android) behind one `WebDavTransport` seam. |
| `app` | The Compose Multiplatform application (Android + desktop). |
| `tools/dictionaries` | The standalone dictionary-pack pipeline and sample catalogue. |
| `architecture` | The architecture tests: module boundaries and naming conventions. Test-only; reads the tree and depends on no module. |

`tools/icons`, `tools/packaging` and `guide/` are not modules. `guide/` is the
end-user VitePress site, built by its own npm toolchain (ADR-0051).

## Allowed dependencies

```text
app ──────────────► core
app ──────────────► integrations/*
integrations/* ───► core
testkit ──────────► core
core (production) ─► nothing
nothing ──────────► app
architecture        (reads the tree; depends on no module)
tools/dictionaries  (standalone)
```

- `core` production code depends on no Lekto module. Its tests may depend on
  `testkit`; that edge is test-only.
- `integrations/*` depend on `core` through its published seams; `core` never
  names an integration.
- Only `app` may depend on an integration; it is the composition root.
- Nothing depends on `app`.
- `testkit` is test-scoped and must never reach production.
- `architecture` sits outside the graph it polices.

`./gradlew check` runs `:architecture:test`, so a violation fails the build
rather than waiting for review. Naming and placement rules are in
[`docs/development.md#naming-and-placement`](docs/development.md#naming-and-placement);
the decisions are ADR-0007 and ADR-0013.
