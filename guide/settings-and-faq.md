# Settings and FAQ

Settings holds the app-wide choices the rest of the guide points at, and this page
answers the common questions. Open it from the third section of the bar — the left
column on a desktop.

Settings is an **index**: one row per area, with the area's current value under
its name. The rows are grouped **Reading**, **Data** and **Application**, and each
one opens the screen where the value is changed — nothing is edited on the list
itself.

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
- **Reading languages** — the languages your books are in, most-read first. The row
  names them; the **Languages** screen is one tap away.
- **Languages** — the languages Lekto offers for reading, ordered by the ones you
  have most books in. Choose the **Translation language** you want translations in —
  only the languages Lekto publishes an offline dictionary for are offered, and your
  choice is kept on this device. For each reading language, declare the CEFR level
  you read at (choose **Not set** to clear it), see and download its word-difficulty
  data where Lekto publishes it, and download each offline dictionary it offers,
  labelled by the pair it translates into ("English → French"). An artefact Lekto
  does not publish says so and offers no download. **Reload list** refetches the
  list at any time, and **Attribution** opens the licences and sources of the data
  you download, and of the icons the app draws. See [Reading](/reading) and
  [Word lookup](/word-lookup).
- **Vault and sync** — a screen of its own, reached from Settings. It writes your
  whole Vault to a bundle or restores one, then connects your own WebDAV server,
  tests it, turns sync on and shows its status and last result. See
  [Vault and sync](/vault-and-sync).
- **LLM provider** — a screen of its own, reached from Settings. Choose a provider,
  enter an API key, load and pick a model, and test the connection. See
  [LLM provider and privacy](/llm-provider).
- **Attribution** — the licences and sources of the data you download and of the
  icons the app draws.
- **About** — the version of Lekto you are running, the licence it is released
  under, and a link to its source code.

## Frequently asked questions

### My import failed, or the text looks wrong

Import reports a failure rather than blocking. Check the format: EPUB and TXT are
first-class, while PDF is best-effort and a scanned PDF may not extract. See
[Import](/import).

### I hear nothing when I tap the speaker icon

The language may have no voice installed on your device. Lekto says so instead of
failing. Install a voice for that language in your system settings and try again.
See [Pronunciation](/pronunciation).

### My provider rejects the key

Press **Connection** on the **LLM provider** screen (Settings → LLM provider) to
see the failure. Check that the key is correct, that the provider is the one the
key belongs to, and that the model is available to it — **Load models** lists what
the provider offers, so you can pick a valid one. See
[LLM provider and privacy](/llm-provider).

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

Check **Settings → Vault and sync** on both devices: each needs the same WebDAV
server address and a username and application password that reach it, and sync
must be on. Press **Connection** to spot a wrong address or password — it tests
first, then stores the password and turns sync on — then **Sync now**. A failed
run says why and changes nothing locally. See
[Vault and sync](/vault-and-sync).

### Where do I get Lekto for my computer?

The [home page](/#download) links the latest Android and desktop releases.

## Screenshots

### Desktop

![Settings on desktop](/images/settings-desktop.png)

### Android

![Settings on Android](/images/settings-android.png)
