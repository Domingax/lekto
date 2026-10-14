# The reader's level is a vault record, and default mastery derives from it

Today an unsaved word is mastery 0 (unknown), so a fresh reader sees every word
highlighted and must save each one by hand before the text calms down. The reader
declares a **Reader level** in the language they read — one of the six CEFR bands
A1–C2, the same axis a word's **Word difficulty** sits on (ADR-0029) — and a word
at or below that level reads as **known** (level 4, normal text) without a saved
entry.

The level is **user-authored data**, so it lives in the **Vault** (ADR-0005) as a
record of kind `reader-level`, one per reading language, its id derived from the
language (`level-<baseTag>`) so every device derives the same id and the records
merge last-writer-wins (ADR-0004) instead of duplicating. It travels in an export
like any record (ADR-0014). It is deliberately **not** an app-private setting
(ADR-0023): those hold the app's own configuration, whereas this is a fact about
the user that must move between their devices. The language is the **book's**,
collapsed to its base tag (`en-US` → `en`) as the pack already does (ADR-0018); a
book with no language has no level and the rule simply does not apply.

`MasteryLookup` stays a pure function over a word's identity; the rule is a
composition in `core`. A saved entry's level wins; otherwise the level is derived
from the word's difficulty band and the reader's band; otherwise it is unknown.
The derivation is one formula over the two bands:

    mastery = max(0, 4 − max(0, difficulty_band − reader_band))

so a word at or below the reader's level is level 4 (normal text), and each band
above it drops one level, flooring at 0. A word with **no** difficulty band, or a
language with **no** level set, is unknown: the rule only lights up when both are
present, so a reader who has set nothing sees exactly what they see today.

Because the reader memoises its word layer, a level change must recolour the open
book at once, the way a save does. One `MasteryController` in the app composes the
saved vocabulary, the reader level and the difficulty, and owns the single
`revision` the reader keys its rebuild on — so a save and a level change both
recolour with no toast, and the reader never reads a store itself. The lookup
panel's fresh-save default follows the level the word currently shows, floored at
the familiar level when the word has no signal, so a save does not appear to
"demote" a word.

We rejected a **global** level rather than a per-language one (a reader's French
and English levels differ, and the book's language is the natural key); storing
the level **outside the vault**, on ADR-0023's pattern (it would not export or
sync, which the feature requires); letting a **saved entry's level be recomputed**
from the rule (a save is the user's explicit act and must win, ADR-0020); and
giving derived difficulty a **separate visual scale** (the reader paints one
mastery level per token, and the feature asks the derived level to be a mastery
level).

**Consequences**: the reader level is a new vault record kind, so it syncs and
exports with no per-kind sync work, and it is the first user-owned per-language
fact in the vault. The reader-level UI lands in Settings for now; a dedicated
profile/languages surface is a companion issue, and the level's record is designed
so it can move there without changing the store. The pipeline and the app learn no
new dependency: the rule reads the `WordDifficulty` seam (ADR-0029) and the level
record, both already in `core`. The mastery scale's own level names are being
renamed in a separate change; this rule works on the level **numbers**, which do
not move.

## Update — the level UI moves to the Languages surface, and clearing deletes the record (issue #147)

The **Reader level** picker leaves Settings for the **Languages** surface, so it
sits beside the **Difficulty table** and the **Dictionary pack** the rule depends
on (issue #141). The record's shape, its `level-<baseTag>` id and the derivation
are unchanged, exactly as the "companion issue" in the consequences intended, and
the mastery composition keeps its single `revision`, so a level change still
recolours without a toast. The picker gains a **not set** choice that **clears**
the level: `ReaderLevels.clearLevel` removes the record — it does not write a
default — and the vault-backed store leaves a **Tombstone** in the same engine
store the vocabulary's delete already uses (ADR-0015), so a cleared level is
synchronised rather than copied back from a device that has not seen the change.
The composition root points the level store at that store; the record moves no
other way.
