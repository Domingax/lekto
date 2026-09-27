# The vault is app-private; portability is export, not a folder

The vault lives in an **app-private store on every client** — the OS app-data directory on
desktop, app-private storage on Android. It is never a user-chosen folder, so there is no
directory picker and **no SAF**.

The folder existed to *be* the sync mechanism: point an external tool (Syncthing, rclone, a
cloud folder) at it. Now that sync is an app-level engine (ADR-0009), the folder has no job
left. Its only remaining justification was live inspection, and we judged universal
export/import — plus "reveal in file manager" on desktop, where the app-private store is an
ordinary folder — a better trade than keeping the whole SAF surface: persistable URI grants
that break when a folder moves, storage roots that cannot be picked, and the double-sync
corruption trap that Joplin explicitly documents.

**Supersedes ADR-0001.** There is no capability-driven vault any more: `VaultStore` is a
plain local store per platform, and the portability guarantee is export/import plus whatever
`SyncTarget` the user configures. Derived assets (parsed text, the dictionary pack) stay
device-local (ADR-0005).
