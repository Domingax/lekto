# Word difficulty is a per-language derived artifact behind a version handshake

The default-mastery rule (ADR-0030) needs, for each word, a **difficulty**: how
hard it is for a learner of the language it is read in. The signal is
**frequency**, not a graded wordlist. A language's words are ranked from most to
least common and cut into six bands labelled A1–C2, so a word's difficulty and
the reader's own level (ADR-0030) sit on one ordinal axis and compare directly.

The frequencies come from **wordfreq** (data files CC BY-SA 4.0 — the same licence
as the dictionary pack, ADR-0011; code Apache-2.0). wordfreq's lists are **surface
forms** counted with their own tokeniser, while mastery and vocabulary are keyed
by `(language, lemma)` (ADR-0006); so the build **aggregates a form's frequency
onto its lemma** using the `form` table the pipeline already builds, and keys the
band by the normalised surface form when no lemma is known — the same key the
tokeniser computes, so difficulty and mastery never disagree on a word's identity.
wordfreq's `NOTICE` requires its attribution to travel with the data, which the
artifact carries inside itself (below).

The bands ship as a **derived artifact of their own, one per reading language**
(`difficulty-en`, …), not inside the dictionary pack. The pack is keyed by a
**pair** — reading language × translation language — while difficulty is a property
of the **reading language** alone: baked into the pack, the English band table
would be duplicated into every English→X pack, and changing one frequency cut
would force a republish of each. A separate artifact aligns the axes, gives each
language its own version, licence and attribution, and lets a language have
difficulty data even when no pack pair exists for it.

The artifact is a small SQLite file read through the **same `PackDatabase` seam**
as the pack (ADR-0018), so both clients execute one SQL and cannot answer
differently. Because it is small and the reader asks for a band per **Word token**
while paginating, `WordDifficulty` **loads it into memory once** rather than
querying per word. It carries its own `FORMAT_VERSION`, compared on install
against the constant duplicated in `tools/dictionaries` and kept in step by an
architecture test (ADR-0013); a mismatched artifact is refused and removed rather
than read wrong. Its installer owns the same four states as the pack's
(`NotInstalled`, `Ready`, `Incompatible`, `Corrupt`) and streams from a stable,
date-free per-language alias. The artifact carries its own `NOTICE` naming
**wordfreq / Robyn Speer**, **Google Books Ngram Viewer**, **OpenSubtitles** and
the **SUBTLEX** authors — the licence requires the credit to ship *inside* the
data, not merely in the app — and the app's attribution screen reads both the
pack's and the artifact's metadata.

A word the artifact has no band for — absent from the frequency list, a proper
noun, a form the pipeline could not place — carries no signal and reads as unknown
(ADR-0030). The band is nullable per word.

We rejected sourcing the bands from a ready-made **CEFR wordlist** (the openly
reachable ones are either proprietary — the English Vocabulary Profile, and the
Kaggle/GitHub copies of it that carry no grant — or non-commercial, such as EFLLex
and Kelly, so a frequency cut is both simpler and licence-clean); putting the
bands in the **dictionary pack** (the axis mismatch above); deriving them **at
install time** from a separate list (attaching a frequency to a Wiktionary lemma
needs the pack's `form` table, so the join must happen at build, not on the
device); and **bundling** an in-app table (CONTEXT.md's *Derived asset* is
something regenerated or re-downloaded, and bundling would forfeit the version
handshake a reader needs to refuse data it does not understand).

**Consequences**: the dictionary pack's own format does not change — only the new
artifact's. `tools/dictionaries` gains a second output; in this change it produces
`difficulty-en` only, because the pipeline is not yet generalised to arbitrary
reading languages, and that generalisation is future work the per-language
artifact already leaves room for. The English band cut points are a data choice
recorded in the artifact's manifest, so re-calibrating them needs no code change;
they are anchored to the published CEFR vocabulary sizes (Milton & Alexiou *XLex*)
and validated against the Wolfer & Lew (2025) frequency medians. The artifact is a
derived asset, so it is structurally outside the vault and can never reach an
export.
