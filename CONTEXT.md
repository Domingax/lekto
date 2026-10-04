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

**Tombstone**:
The mark a deleted item leaves behind so the deletion itself can sync: the item's
identity and update metadata without its content. A tombstone with a later update
timestamp beats a live copy, so a deletion does not resurrect on the next sync.
_Avoid_: Delete marker, soft delete, tomb

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
The list of books in the vault, ordered by title; the app's starting screen and the
way back from a reading session. Distinct from the **Vault**, which is the storage it
reads: the library is the view, not the store.
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
How well the user knows a word, on a five-point scale: 0 unknown, 1 familiar,
2 recognized, 3 mastered, 4 known. Levels 0–3 are highlighted; level 4 is invisible
(normal text).
_Avoid_: Confidence level, difficulty, proficiency

**Lemma**:
The dictionary form of a word — "manger" for "mangeais". Used as the canonical key for
mastery when known; otherwise the normalised surface form is used. The dictionary
pack stores lemmas already canonicalised, so the domain uses one as-is.
_Avoid_: Root, stem, base form

**Vocabulary entry**:
A word or phrase the user saved, with its translation, a context sentence, and a
mastery level.
_Avoid_: Card, item, saved word

**Context sentence**:
The sentence a vocabulary entry was saved from, kept so the word can be reviewed in
context.
_Avoid_: Example, snippet

**Dictionary source**:
An external reference service (WordReference, Reverso, Google Translate, Linguee) used
to look up a single word.
_Avoid_: Provider

**LLM provider**:
The user's own language-model service (OpenAI, Anthropic, Gemini, Ollama), connected
BYOK, used for phrase translation.
_Avoid_: AI, model
