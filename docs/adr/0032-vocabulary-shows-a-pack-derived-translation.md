# The vocabulary list derives its translation from the installed pack

A **Vocabulary entry** stores the translation it was saved with — the offline
result's first gloss (ADR-0020) — so installing or updating a **Dictionary pack**
later leaves the list showing a stale, or missing, translation (issue #67). The
list now resolves a translation from the pack at the moment it is read, and falls
back to the entry's stored translation when no pack answers.

The pack is only read, never written back. A resolved gloss is a **Derived
asset** (ADR-0005): re-downloadable, device-local, and outside the **Vault**. The
entry keeps the `translation` it was saved with, unchanged, and that value
becomes the offline fallback. It is shown when no pack is installed, when the
pack does not know the word, and when the reader's **Translation language**
(ADR-0033) has no pack; a pack that is incompatible or corrupt counts as not
installed. A word the pack does not know and that carries no stored translation
shows no translation line at all, rather than a placeholder.

The list searches what it displays, so a search matches the resolved translation
rather than the stored one; otherwise a row would match on a word it does not
show.

Because nothing is written, one entry can read differently on two devices with
different packs installed. That is accepted and correct: the derived gloss is a
view, while the word, its **Mastery level** and its **Context sentence** are the
user-authored data that syncs and merges (ADR-0004).

We rejected **refreshing the stored translation when a pack is installed or
updated**: it would put a derived, device-local, re-downloadable value into the
Vault, make it export and sync like user data, move `updatedAt` on every entry,
and reintroduce per-record merge churn (ADR-0004) on a mere download. We rejected
**showing only the stored translation** — the status quo the issue exists to fix.
We rejected **storing the pack's gloss beside the frozen one**: two translations
per entry, one of them derived, with no way to tell which device's last download
won.

**Consequences**: the stored `translation` is now a fallback and a seed, not the
truth, and a record's shape is unchanged, so no migration is needed (ADR-0014).
The display path needs the pack, so the list resolves through the same
`DictionaryServices` seam the reader already uses (ADR-0018), against the
**Translation language** the reader chose (ADR-0033). A future user-authored or
LLM-provided translation must outrank the pack and so needs a provenance marker
the record does not carry today; that change is deliberately deferred, and this
ADR is the reason it is not a surprise.

## Update — the language is the translation language, and the app has its own (ADR-0034)

The "**Translation language** the reader chose (ADR-0033)" above is superseded:
the list resolves against the reader's **Translation language**, a device-local
setting rather than a **Vault** record (ADR-0034, which supersedes ADR-0033). The
language of the app's own texts is a separate **App language**. Nothing else here
moves.
