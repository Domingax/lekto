# Settings and FAQ

Settings holds the app-wide choices the rest of the guide points at, and this page
answers the common questions.

## Settings

- **Sync** — connect your own WebDAV server, test it, turn sync on and see its
  status and last result; see [Vault and sync](/vault-and-sync).
- **Vault export and import** — write your whole Vault to a bundle, or restore one.
- **Dictionary pack** — the status of the offline dictionary pack and its download.
- **LLM provider** — choose a provider, enter an API key, pick a model, and test the
  connection. See [LLM provider and privacy](/llm-provider).
- **Attribution** — the licences and sources Lekto builds on.

## Frequently asked questions

### My import failed, or the text looks wrong

Import reports a failure rather than blocking. Check the format: EPUB and TXT are
first-class, while PDF is best-effort and a scanned PDF may not extract. See
[Import](/import).

### I hear nothing when I tap Listen

The language may have no voice installed on your device. Lekto says so instead of
failing. Install a voice for that language in your system settings and try again.
See [Pronunciation](/pronunciation).

### My provider rejects the key

Use the connection test in Settings to see the failure. Check that the key is
correct, that the provider is the one the key belongs to, and that the model is
available to it. See [LLM provider and privacy](/llm-provider).

### The second device does not see my data

Check **Settings → Sync** on both devices: each needs the same WebDAV server
address and a username and application password that reach it, and sync must be
on. Use **Test connection** to spot a wrong address or password, then **Sync
now**. A failed run says why and changes nothing locally. See
[Vault and sync](/vault-and-sync).

### Where do I get Lekto for my computer?

The [home page](/#download) links the latest Android and desktop releases.
