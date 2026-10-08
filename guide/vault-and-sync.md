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

Sync is off until you turn it on. In **Settings → Sync**, enter the address of
your own WebDAV server — Nextcloud, ownCloud, Synology or any WebDAV host — a
username and an **application password**, test the connection, then enable sync.

The address is kept on the device and the application password in the **Secret
store**; neither enters the Vault, an export or a log. Only the Vault travels
between your devices — the dictionary pack and your **API key** do not.

Once sync is on, **Sync now** reconciles this device with the server and reports
what moved in plain language. A run that fails says so and changes nothing
locally, so the app stays fully usable offline. **Disconnect** clears the address
and the password and leaves your Vault on the device.

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
