# An LLM provider is reached directly by API key; no server and no OAuth

Phrase translation (issue #25) must work without a Lekto server (ADR-0002) and with
the user's own account, not a new one. That fixes both the network shape and the
credential shape: the client calls the provider directly with the user's API key, and
there is no sign-in flow.

We decided that the provider surface is a single **OpenAI-compatible**
chat-completions adapter with a configurable base URL and named presets. Every service
Lekto supports today speaks that API — OpenAI, Anthropic (its compatibility endpoint),
Google Gemini (`/v1beta/openai/`), the OpenCode Zen and Go gateways, and a local
Ollama — so the wire format is one path, not one per vendor. The concrete list lives
in the README; a new provider is a base URL and a model, not a code path. One provider
is active at a time, streaming responses arrive as server-sent events, and the model
is chosen by the user. The API key lives in the **`SecretStore`** (ADR-0021); the
provider and model, which are not secret, are app-private settings and never enter the
vault (ADR-0005).

We rejected subscription OAuth — "sign in with Claude", say — because Anthropic
forbids third-party applications to route through Free, Pro or Max credentials, and
the other providers expose no public inference OAuth; an API key is the honest
mechanism (ADR-0002 already scoped LLM OAuth out). We rejected a native adapter per
vendor: the compatibility endpoints make one transport enough, and a seam that carries
base URL and model absorbs future providers. We rejected a Lekto proxy, or a
user-run one (ADR-0002), and deferred a dedicated machine-translation API (e.g.
Google Cloud Translation v2), which has a simpler contract but a second credential and
no contextual prompt.

**Consequences**: every provider decision is data — base URL, model, key — so the
adapter is one tested path. A provider failure or an offline state is reported inline
in the panel, never thrown into the reading session. Because the client is native, the
browser-era CORS analysis in `docs/research/dictionary-and-ai-integration.md` no
longer applies; that report's web/keychain sections are superseded by this ADR and
ADR-0021. Ollama is reachable only from a desktop, since it is a local server. The
prompt is built in `core` and the adapter only transports it, so the translation
behaviour is testable without a network. The panel's zero-configuration fallback is a
**Translation shortcut** (CONTEXT.md), not the provider.
