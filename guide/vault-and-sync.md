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

## Moving and syncing your Vault

Lekto does not run its own sync service. Instead, you can relocate your Vault to a
folder that a sync service you already use and trust is watching, and let that
service carry the files between your devices.

The privacy trade is yours to make: you choose the sync method and where the data
travels. There is no Lekto server in the middle. Point a second device at the same
folder and it reaches the same data.

Only the Vault moves. Secrets, such as an **API key**, live in the **Secret store**
and are never part of the Vault or an export; see
[LLM provider and privacy](/llm-provider).

## Export and import

Settings can export your Vault as a single bundle and import one back, so you can
back it up or move it deliberately. See [Settings and FAQ](/settings-and-faq).
