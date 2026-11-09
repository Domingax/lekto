---
status: accepted
---

# A dictionary pack is built from its reading language's own Wiktionary edition

ADR-0031 concluded that only English-source packs are derivable from Kaikki,
because the French extract carries 403,269 entries and zero translations. That is
true of **the all-languages file**, which is the extraction of en.wiktionary — and
not of the source landscape. Kaikki publishes a raw extract per Wiktionary
edition (`fr`, `de`, `es`, `it`, `pt`, `nl`, `ru`, `pl`, `ja`, `ko`, `zh`, `tr`,
`vi` among them), and those extracts carry translations.

The all-languages file has no French translations for a structural reason: on
en.wiktionary a page carries one section per language, and the Translations table
belongs to the English word. The French section of `chat` holds the French
definition, etymology and pronunciation, and no translation table. The file
describes hundreds of languages but translates from one.

**The rule is unchanged; only the file changes.** The reading language is the
entry's `lang_code`; the translation language is each element's
`translations[].lang_code`. For `fr → it` the pipeline reads `fr-extract` and
keeps the `fr` entries that carry an `it` translation. The `it` entries the same
file also contains are Italian words glossed in French and play no part. This is
exactly the rule the EN→FR build already applies.

**One edition, one pass.** An edition supplies every pair that reads it, so
reading it once replaces N−1 re-readings of the same bytes. The English edition
keeps the all-languages file because there is no `en-extract`: that file *is* the
enwiktionary extraction.

**The matrix is feasible, not guaranteed.** Coverage is uneven: the two
directions of one pair are different packs built from different files and can
differ by more than an order of magnitude (`fr → it` measured 31.3 % of entries
carrying a translation, `it → fr` 2.4 %). A thin pair is dropped from the
**Language manifest** rather than published (issue #301), and a full-pass spike
over the first roster decides which pairs ship — the measurements above come from
an alphabetical prefix and are indicative only.

Rejected: a single English source with pairs pivoted through English, which would
need a translation into English before every other pair and lose sense alignment
at each hop; Kaikki's post-processed per-language files, which strip about 94 % of
`translations` rows; keeping the English-only set, which refuses the product goal.

**Consequences**: `languages.json` grows from two pairs to a matrix; each
artefact's provenance records its own edition's URL, SHA-256 and extraction date
rather than the English one's; the run's input volume becomes the sum of the
editions (about 1.8 GiB gzipped for ten) instead of one 2.9 GiB file re-read per
pair. ADR-0031's single-release and per-run statements are superseded by
ADR-0046 and ADR-0047.
