# Lekto

Lekto is a local-first immersive reading app for language learning: the user imports
books, reads them with words coloured by how well they know them, looks up words and
phrases, and saves vocabulary. All user data lives on the user's device in a **Vault**.

## Language

**Vault**:
The portable collection of all of a user's Lekto data (books, vocabulary, reading
progress). It is the unit of data ownership and the thing a user may relocate to a
synced directory.
_Avoid_: Library, data folder, workspace

**Book**:
An imported piece of reading content — EPUB or TXT as first-class, PDF as best-effort
text extraction. Comprises an original file plus the text Lekto renders.
_Avoid_: Document, text

**Word token**:
A single tappable unit of text in the reader — the unit of lookup and colouring.
_Avoid_: Word, term

**Mastery level**:
How well the user knows a word, on a five-point scale: 0 unknown, 1 familiar,
2 recognized, 3 mastered, 4 known. Levels 0–3 are highlighted; level 4 is invisible
(normal text).
_Avoid_: Confidence level, difficulty, proficiency

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
