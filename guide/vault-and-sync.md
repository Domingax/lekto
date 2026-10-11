# Vault and sync

The **Vault** is the collection of everything you authored or imported: your
vocabulary, your reading progress and your book originals. It is the unit of data
ownership, it is app-private on every device, and it moves between devices by export
and import.

## Where your data lives

Everything in the Vault lives on your own device. There is no Lekto account and no
Lekto server, so nothing is uploaded anywhere unless you choose to move it.

Derived assets — parsed book text, the dictionary pack — are not part of the
Vault. Lekto can regenerate or re-download them, and they are never synced.

## Syncing your Vault

Sync works on Android and on the desktop, through the same **Settings → Sync**
section on both. Sync is off until you turn it on. In **Settings → Sync**, enter
the address of your own WebDAV server — Nextcloud, ownCloud, Synology or any
WebDAV host — a username and an **application password**, test the connection,
then enable sync.

The address is kept on the device and the application password in the **Secret
store**; neither enters the Vault, an export or a log. Only the Vault travels
between your devices — the dictionary pack and your **API key** do not.

Once sync is on, it runs **by itself**: when you open Lekto, every so often while
it is open, and when you come back to it. **Sync now** runs one straight away.
Each run reconciles this device with the server and shows the last outcome in
plain language. A run that fails says so and changes nothing locally, so the app
stays fully usable offline; Lekto waits longer between runs after a failure, so a
server that is not answering is not hammered. **Disconnect** clears the address
and the password and leaves your Vault on the device.

## Deletions, and coming back after a long time

When you delete a word, a book or your reading progress, Lekto remembers the
deletion so it travels to your other devices instead of the item coming back. It
remembers it for **90 days** — the longest we support a device being offline —
and then forgets it, so deletions do not pile up.

If a device has not synced for longer than that, it is treated as a new device:
it takes what is on the server rather than sending its own older copy back, so a
deletion you made on another device cannot reappear. When that happens, **Sync
now** says the device restarted from the server. Anything you did on that device
since its last sync is replaced by the server's copy, so keep a device you care
about syncing within 90 days.

::: warning Do not point a folder-sync tool at your Vault
Syncthing, Dropbox, iCloud Drive and the like put a second writer on the same
files and can corrupt them. Use Lekto's own sync instead — it merges changes
safely.
:::

Only the Vault moves. Secrets, such as an **API key**, live in the **Secret store**
and are never part of the Vault or an export; see
[LLM provider and privacy](/llm-provider).

## Export and import

Settings can export your Vault as a single bundle and import one back, so you can
back it up or move it deliberately. See [Settings and FAQ](/settings-and-faq).
