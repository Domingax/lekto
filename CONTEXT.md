# Lekto

Lekto is a local-first immersive reading app for language learning: the user imports
books, reads them with words coloured by how well they know them, looks up words and
phrases, and saves vocabulary. All user data lives on the user's device in a **Vault**.

## Language

**Vault**:
The collection of everything the user authored or imported (vocabulary, reading
progress, book originals). It is the unit of data ownership, app-private on every
client, and moves between devices by export and import.
_Avoid_: Library, data folder, workspace

**Derived asset**:
Anything Lekto can regenerate or re-download rather than something the user authored —
parsed book text, the dictionary pack. Lives device-local and is never synced.
_Avoid_: Cache, generated data

**Dictionary pack**:
The offline EN→FR reference Lekto derives from Wiktionary: the words it knows,
with their senses and French translations, and enough morphology to collapse an
inflected form onto its lemma — or onto the normalised surface form when it knows
no lemma. A **Derived asset**, versioned so a reader can refuse a pack it does
not understand; never authored by the user and never synced.
_Avoid_: Dictionary, lexicon, word list

**Difficulty table**:
The per-language **Derived asset** that maps each **Word key** to its **Word
difficulty** band: one per language, built from an open frequency list, and
versioned so a reader can refuse one it does not understand. It is what the
default-mastery rule reads; never authored by the user and never synced.
_Avoid_: Frequency list, word list, difficulty pack

**Language manifest**:
The catalogue of what Lekto offers for reading: each **Reading language** and the
artefacts published for it — a **Difficulty table** per reading language, and a
**Dictionary pack** per (Reading language × **Translation language**) pair, each
naming its translation language. A **Derived asset**, versioned so a reader can
refuse one it does not understand, and never authored by the user.
_Avoid_: Catalog, index, registry

**Tombstone**:
The mark a deleted item leaves behind so the deletion itself can sync: the item's
identity and update metadata without its content. A tombstone with a later update
timestamp beats a live copy, so a deletion does not resurrect on the next sync.
_Avoid_: Delete marker, soft delete, tomb

**Tombstone retention window**:
The longest offline window the product supports: how long a **Tombstone** is kept
before the engine may drop it. A device that has been away longer is treated as
new and restarts from a full resync, so an old live copy cannot resurrect a
deleted item.
_Avoid_: Expiry, TTL, grace period

**Sync**:
Reconciling the **Vault** with a remote copy the user owns, so the same books,
reading positions and vocabulary appear on every device. It is off by default,
turned on by an explicit act, and its status and last outcome are always visible.
_Avoid_: Folder sync, backup, mirror

**Sync target**:
The remote store Lekto reconciles the **Vault** with, reached over WebDAV and
addressed by a base URL and a username.
_Avoid_: Backend, server, remote

**Application password**:
A per-device credential a self-hosted **Sync target** issues for Lekto, kept in
the **Secret store** and never in the **Vault**. It is not the user's account
password.
_Avoid_: Password, token

**Book**:
An imported piece of reading content — EPUB or TXT as first-class, PDF as best-effort
text extraction. Comprises an original file plus the text Lekto renders.
_Avoid_: Document, text

**Book original**:
The file the user imported — an EPUB or a TXT — stored in the vault as the book's
attachment so export carries it. Parsed text is a **Derived asset** re-derivable from
it; the original is not reproducible and is never left device-local.
_Avoid_: Source file, raw file

**Import**:
Getting a book original into the vault: read the file, parse it to text, store the
book and its original, and list it in the library. An occasional action that reports
progress and surfaces failure rather than blocking.
_Avoid_: Scan, upload, add

**Library**:
The list of books in the vault: the three most recently read books first, most recent
first, then every other book by title. It is the app's starting screen and the way back
from a reading session. Distinct from the **Vault**, which is the storage it reads: the
library is the view, not the store.
_Avoid_: Shelf, collection

**Reading session**:
The act of opening a book to read it: a book plus the text Lekto renders for it, from
which the reader draws its pages.
_Avoid_: Opening, view

**Reading position**:
Where the user left off in a book: a character offset into the text the reader
renders, chosen so the place survives a reflow onto a different screen size or font.
Saved per book in the vault and restored when the book is reopened.
_Avoid_: Page number, bookmark, scroll position

**Last read**:
The moment a book's **Reading position** last moved — a page turned, not the book
opened. It orders the **Library** and says nothing about how far through the book the
reader is.
_Avoid_: Last opened, recent, progress

**Word token**:
A single tappable unit of text in the reader — the unit of lookup and colouring.
Each carries its **Word key**, the identity mastery and vocabulary hang off.
_Avoid_: Word, term

**Word key**:
A word's identity: `(language, lemma)` when the dictionary pack knows the lemma,
and `(language, normalised surface form)` otherwise. Two spellings that share a
key share one mastery level and one vocabulary entry.
_Avoid_: Word id, hash


**Mastery level**:
How well the user knows a word, on a five-point scale: 0 unknown, 1 recognized,
2 familiar, 3 learned, 4 known. The names read in the order a reader acquires a word:
you recognize it before it becomes familiar, and it is learned before it is known.
Levels 0–3 are highlighted; level 4 is invisible
(normal text). Held per **Word key**, so inflected forms share one level, and stored on
the **Vocabulary entry** that key names.
_Avoid_: Confidence level, difficulty, proficiency

**Word difficulty**:
How hard a word is for a learner of its language: the frequency band it falls in,
on the same six-point CEFR scale as the **Reader level**, so the two compare
directly. A property of the word, derived from the **Difficulty table**, not of the
user; distinct from **Mastery level**, which is how well this user knows it.
_Avoid_: Frequency, rarity, difficulty score

**Reading language**:
The language a **Book** is read in, reduced to its base language tag (`en-US` → `en`).
A **Reader level** is declared per Reading language, and a **Difficulty table** is
held per Reading language.
_Avoid_: Source language, book language

**Translation language**:
The language the reader wants translations in — the language a **Dictionary pack**
translates a **Reading language** into. Chosen once per device, app-private and
never synced, and separate from the **App language**; a pack is keyed by the
(Reading language × Translation language) pair.
_Avoid_: Target language, output language, user language, native language

**App language**:
The language the reader wants the app in: the language of its own texts. One
choice per device, app-private and never synced, so it does not travel with the
**Vault** (ADR-0034). Distinct from the **Translation language**, the language the
app translates into.
_Avoid_: Locale, UI language, device language

**App theme**:
The colours the app paints itself in: **Light**, **Dark** or **Sepia**. One choice per
device, app-private and never synced, so it does not travel with the **Vault**; it is not
a **Reading preference**, because it recolours the whole app rather than a book's text
(ADR-0036). Light and Dark are also what the device may choose, so the setting names a
fourth possibility, **System**, which resolves to one of those two and never to Sepia.
_Avoid_: Colour scheme, dark mode, skin, reading theme

**Reader level**:
The level a reader declares in a language they read, on the six-point CEFR scale
A1–C2, held per **Reading language** in the **Vault** so it exports and syncs with it. The
reader's own level, on the same scale as a word's **Word difficulty**, and
user-authored data rather than app configuration.
_Avoid_: Proficiency, language level, user level

**Reading preference**:
How Lekto renders a book's text for the reader on this device: the face, size,
leading and margins. One choice per device and not per **Reading language**,
app-private and never synced, so it does not travel with the **Vault** the way
the **Reader level** does (ADR-0035).
_Avoid_: Reading settings, reader style, font settings, typography

**Lemma**:
The dictionary form of a word — "manger" for "mangeais". Used as the canonical key for
mastery when known; otherwise the normalised surface form is used. The dictionary
pack stores lemmas already canonicalised, so the domain uses one as-is.
_Avoid_: Root, stem, base form

**Vocabulary entry**:
A word or phrase the user saved, with its translation, a context sentence, and a
mastery level. Keyed by the word's **Word key**, so inflected forms share one entry;
stored per record in the **Vault**, so a save survives a restart. The stored
translation is the offline fallback the word was saved with; the translation the
list shows is derived from the installed **Dictionary pack** (ADR-0032).
_Avoid_: Card, item, saved word

**Context sentence**:
The sentence a vocabulary entry was saved from, kept so the word can be reviewed in
context. Cut from the reader's chapter text, so it is not truncated by a page break.
_Avoid_: Example, snippet

**Dictionary source**:
An external reference service (WordReference, Reverso, Google Translate, Linguee) used
to look up a single word.
_Avoid_: Provider

**Dictionary shortcut**:
An outbound link from the lookup panel to a word's canonical page on a
**Dictionary source**. Lekto never embeds or scrapes the source; opening the
shortcut hands the page to the platform browser.
_Avoid_: Dictionary tab, deep link

**Translation shortcut**:
An outbound link from the lookup panel that opens an external translation service with
a selected phrase or sentence already filled in. Like a **Dictionary shortcut**, Lekto
never embeds or scrapes the service; opening it hands the page to the platform browser,
and it is what the panel offers when no **LLM provider** is connected.
_Avoid_: Deep link, machine translation

**Pronunciation**:
Playing a selected word or phrase aloud through the platform's own speech engine,
never a network service and never stored. A language with no installed voice is
reported to the reader rather than failing.
_Avoid_: TTS, speech, audio

**Secret store**:
The device's own secure storage for a value that must never enter the **Vault**,
an export, or a log — an API key, say. A device with no usable one reports it
unavailable rather than writing the value in the clear.
_Avoid_: Keystore, keychain, credential store

**API key**:
The user's own credential for their **LLM provider**, kept in the **Secret store**
and never in the **Vault**.
_Avoid_: Token, secret key

**LLM provider**:
The user's own language-model service, connected by the user's own API key, that
translates a selected phrase or sentence in context. Lekto reaches it directly, with no
Lekto server in between.
_Avoid_: AI, model
