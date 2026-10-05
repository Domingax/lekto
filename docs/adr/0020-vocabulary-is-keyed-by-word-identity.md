# A vocabulary entry is a vault record keyed by the word's identity

One tap in the lookup panel saves the tapped word (issue #22): its translation, the
context sentence it was found in and a mastery level, with the reader's colour changing
at once and no toast. The entry must be keyed so that an inflected form does not become
a second entry for the same lemma (ADR-0006), and it must survive a restart. It is
user-authored data, so it lives in the **vault** (ADR-0005) as a record of kind
`vocabulary` (ADR-0003), and its mastery travels with the vault like any other record.

The record id is derived from the word's `WordKey` — `word-<fnv1a64(language, key)>` —
so every device that saves the same word derives the same id and the two records merge
last-writer-wins (ADR-0004) instead of duplicating. The key is the **lemma** when the
dictionary pack knows one and the normalised surface form otherwise (ADR-0006), computed
by the tokeniser, which is given the pack's reverse index as a `LemmaLookup`. Saving an
inflection therefore writes the lemma's one record, so it updates the entry in place.

We rejected keying the record on the surface form (it would fragment `mangeais` /
`mange` / `manger` into three entries — the exact failure ADR-0006 exists to prevent),
keying it on a generated id with a separate key field (two devices would create two
records for one word and merge could not deduplicate them), and storing vocabulary in a
single list record (it would reintroduce the per-record conflict granularity ADR-0004
chose against). We chose an FNV-1a hash of the key as the id rather than the key itself
because a word key is arbitrary Unicode — a phrase with punctuation or a long form could
overflow a filename — while a fixed 16-hex-digit digest is always a legal vault id; a
collision would merge two words' entries, accepted at 2⁻⁶⁴ odds for a single-user vault.

**Consequences**: mastery is no longer read from a static lookup: the reader paints each
token by the level of its keyed entry, and an unsaved word reads as `unknown`. Because
the page's word layer is memoised, recolouring after a save is keyed by an explicit
`ReaderDocument.masteryRevision` that the save bumps — a plain state read would not force
the rebuild. `MasteryLookup` is now keyed by `WordKey`, not by a surface form and a
language, so it can distinguish a lemma from an unrelated homograph and asks no store
itself. The save is a blocking vault write, so the application runs it off the UI thread;
the panel's Save button and level chips re-render from the loaded entry, which is the
only confirmation the product gives. The entry's `surface` is kept beside the key so the
word the user actually tapped is what the vocabulary shows. The reader still tokenises
one **Word token** at a time, so a saved entry is one word's; saving a multi-word
**phrase** — the selection the panel's phrase mode will offer — needs the reader's
selection work and lands with it, not here. The `translation` stored is the offline
result's first gloss, a single coherent translation; a richer, ordered list is future
work if the vocabulary screen wants it.
