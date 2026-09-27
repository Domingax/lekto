# Web is out of scope; the clients are Android and desktop

Lekto ships two clients: **Android first, desktop second**. The browser target is **closed,
not deferred**.

The browser was originally excluded because the vault was a user-chosen folder and neither
Firefox nor Safari can reach one. Moving the vault to an app-private store later removed
that specific blocker — but the constraints **moved rather than vanished**: browser storage
is evictable (Safari deletes script-created data after seven days without interaction), the
sync drivers that matter to our self-hoster persona (WebDAV on Nextcloud/Synology) do not
send CORS headers, browser OAuth cannot hold a Google Drive refresh token at all, and
Compose/Wasm renders to a canvas with documented accessibility and text-input gaps — the
worst possible trade-off for a *reading* app.

We keep `VaultStore` and `SyncTarget` free of folder assumptions in `commonMain` so a
browser client would not be foreclosed; we simply do not build one. See
`docs/research/web-client-viability.md`.
