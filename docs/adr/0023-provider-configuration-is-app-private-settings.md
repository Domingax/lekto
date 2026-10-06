# Provider configuration is app-private settings outside the vault

Connecting an **LLM provider** (issues #88, #25; ADR-0022) means the app holds more
than a secret: the chosen preset, the model name and a custom base URL. ADR-0022
already settled that these are "app-private settings and never enter the vault"
(ADR-0005), and ADR-0021 settled that the **API key** lives in the **`SecretStore`**.
What it did not settle is where the non-secret configuration lives, and the vault is
the only persisted, app-private store the project had besides the derived assets and
the keychain.

We decided on a `LlmSettingsStore` seam in `core`'s shared sources with a single
`load`/`save` pair, backed on both clients by one JSON document through the existing
byte-level file seam in an app-private root beside the vault and the derived assets.
It is not a vault record, so it can never appear in an export; it is not a derived
asset, so it is not swept by a rebuild or a re-download; and it is not a secret, so it
does not belong in the keychain — a store that may one day prompt for user
authentication would be the wrong home for a model name. The document is a
human-readable `LlmProviderConfig`, and a document this version cannot read is treated
as "not configured" rather than a crash: the user re-enters their choice, and no secret
is involved either way. An in-memory fake lives in `testkit`, and the application
wires the platform store through `AppEnvironment`, as it does the other seams.

We rejected keeping the configuration only in memory: a "connected" provider that
forgets its model on every launch is not connected. We rejected the alternatives for
its home: the vault (ADR-0005 forbids it, and it would travel in every export), the
`SecretStore` (it is not a secret, and the store's honest-unavailable outcome would
turn a preference into an outage), and the `DerivedAssetStore` (CONTEXT.md defines a
derived asset as something regenerable, and this is not). We rejected a
`SharedPreferences`/registry store per platform: `java.io.File` backs both clients
identically, so a second platform seam would buy nothing the file seam has not already
proved.

**Consequences**: the configuration survives a restart and switching providers replaces
it, so one provider is active at a time (ADR-0022). It is device-local, like the vault
(ADR-0010) — moving devices does not carry it, which is consistent with the key it
pairs with also being device-local; portability of user data stays the vault's export.
The seam is synchronous, as `SecretStore`'s is; a store that must prompt adds a method,
not a module. The document is JSON without an explicit version, because it holds three
primitive fields and a revision can add a defaulted field without a migration; a future
change that cannot read the old shape degrades to "not configured", which is the same
outcome as a corrupt file.
