# Vault portability is capability-driven, not guaranteed

Lekto promises a portable "Vault" folder that the user can relocate to a cloud-synced
directory. No single mechanism delivers that on every target: the File System Access
API exists on Chromium (desktop, and Android/WebView 132+) but not on Firefox or any
Safari; and stock Capacitor cannot write to a user-chosen Android directory (SAF tree
URIs are read-only there). We therefore treat a real shared folder as a **progressive
enhancement**: an always-available private store (OPFS/IndexedDB on the web, app-private
storage on Android) is the default, universal export/import is always offered, and a
genuinely relocatable folder is surfaced only where the platform supports it.

Rejected: requiring a real folder on every platform (forces a native SAF plugin plus,
on Android, a Play-restricted all-files permission), and abandoning the folder for a
sync-engine model like Joplin's.

**Consequences**: the storage layer is a capability-driven seam (`VaultStore`), not a
single implementation. Features must query capabilities instead of assuming a folder,
and the "change vault location" journey degrades to export/import where relocation is
unavailable. See `docs/research/cross-platform-file-access.md`.
