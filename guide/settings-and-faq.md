# Settings and FAQ

Settings holds the app-wide choices the rest of the guide points at, and this page
answers the common questions.

## Settings

- **Appearance** — how Lekto looks: **System** (follow the device's light or dark
  setting), **Light**, **Dark** or **Sepia**. Each entry is shown on its own paper,
  so you choose by looking. Your choice is kept on this device, recolours the whole
  app at once — never the text of a book, which keeps its own face, size and margins
  — and never travels with your Vault. A dark or sepia choice also colours the window
  behind the app, so it does not flash white while it starts.
- **App language** — the language you want Lekto's own texts in. Only the languages
  Lekto ships are offered (English and French). Your choice is kept on this device,
  so it does not travel with your Vault between devices, and the whole interface
  follows it at once — no relaunch. The language your translations appear in is a
  separate choice, the **Translation language**.
- **Languages** — the languages Lekto offers for reading, ordered by the ones you
  have most books in. Choose the **Translation language** you want translations in —
  only the languages Lekto publishes an offline dictionary for are offered, and your
  choice is kept on this device. For each reading language, declare the CEFR level
  you read at (choose **Not set** to clear it), see and download its word-difficulty
  data where Lekto publishes it, and download each offline dictionary it offers,
  labelled by the pair it translates into ("English → French"). An artefact Lekto
  does not publish says so and offers no download. **Reload list** refetches the
  list at any time, and **Attribution** opens the licences and sources of the data
  you download. See [Reading](/reading) and [Word lookup](/word-lookup).
- **Sync** — connect your own WebDAV server, test it, turn sync on and see its
  status and last result; see [Vault and sync](/vault-and-sync).
- **Vault export and import** — write your whole Vault to a bundle, or restore one.
- **LLM provider** — choose a provider, enter an API key, load and pick a model, and
  test the connection. See [LLM provider and privacy](/llm-provider).

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
available to it — **Load models** lists what the provider offers, so you can pick a
valid one. See [LLM provider and privacy](/llm-provider).

### Every word is still highlighted

Lekto colours a word as known only when it can compare the word's difficulty with
your reader level, so both are needed: set your level for the language you are
reading in on the **Languages** surface, and download that language's
word-difficulty data there too. A language with no level set, or a word the
difficulty data does not cover, stays highlighted. See [Reading](/reading).

### The Languages list is empty

Lekto fetches the list of languages it offers once and keeps a copy on your
device, so it loads without a network next time. If nothing is stored yet and the
fetch cannot reach the server — offline, say — **Settings → Languages** says so;
the **Reload list** button at the top refetches it once you are back online.

### The second device does not see my data

Check **Settings → Sync** on both devices: each needs the same WebDAV server
address and a username and application password that reach it, and sync must be
on. Use **Test connection** to spot a wrong address or password, then **Sync
now**. A failed run says why and changes nothing locally. See
[Vault and sync](/vault-and-sync).

### Where do I get Lekto for my computer?

The [home page](/#download) links the latest Android and desktop releases.

## Screenshots

### Desktop

![Settings on desktop](/images/settings-desktop.png)

### Android

![Settings on Android](/images/settings-android.png)
