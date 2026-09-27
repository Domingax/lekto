# No backend server, ever — including optional ones

Lekto is local-first with no server component. We rejected even an optional
self-hosted backend or a small dictionary proxy, because a server would let dictionary
integration be "solved" server-side and would quietly erode the offline, own-your-data
promise. All network calls go directly from the client to the user's chosen services
(dictionary sites, the user's own LLM).

**Consequences**: anything that requires a server is either done another way or
dropped — CORS-blocked dictionary APIs, native cloud-storage APIs (Google Drive,
Dropbox), and LLM OAuth among them. See `docs/research/dictionary-and-ai-integration.md`.
