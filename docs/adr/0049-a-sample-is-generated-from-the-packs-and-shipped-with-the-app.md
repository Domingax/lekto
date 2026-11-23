---
status: accepted
---

# A sample is generated from the packs and shipped with the app

The first launch demonstrates the reader on a short text before any book is
imported. That text needs colours before the **Difficulty table** is downloaded
and meanings before the **Dictionary pack** is downloaded, so neither can be
computed on the device at first launch. A hand-written palette would be a second
definition of the colouring rule and would drift from the reader's own books the
first time the table changed.

**The sample's maps and glossaries are build outputs of the packs.** The producer
reads the reading language's built **Difficulty table** and every built
**Dictionary pack** for that language and writes one catalogue:

```
tools/dictionaries/samples/<language>.json     authored text
        + built difficulty table + built packs
        -> core/src/commonMain/resources/samples/catalogue.json
```

The word keys are the reader's own identity — the form index's lemma, or the
normalised surface form — and each of the six colour maps is the table's band for
every word composed with the default-mastery rule (ADR-0030). The catalogue also
records each word's band and the surface forms whose key differs from the
spelling, so `core` can recompute the maps with its own rule and the guide's page
can key a tap before any pack is installed. They are regenerated when the table or
a pack is, in the same commit, so the demonstration cannot show a colouring or a
meaning the product no longer produces. A glossary is a compact cut of one pack:
the highlighted words' first defined sense, at most two parts of speech and three
translations each. It covers every word the sample highlights, and the build fails
rather than shipping a sample whose tap would open an empty sheet.

**The sample's roster follows the published manifest.** The producer reads the
run's **Language manifest**, not a directory listing, so a stale pack cannot enter
the catalogue and a pair that failed to publish cannot appear. A language the
manifest offers no **Difficulty table** or no **Dictionary pack** is refused: with
no table there is no colour, and with no dictionary there is no meaning, so the
guide would promise something the product cannot deliver. A reading language with
packs but a particular translation language without one keeps its sample; the
glossary for that pair is simply absent, which is the state #277 degrades in.

**The catalogue ships in the app, the materialised sample does not.** The
catalogue is the build-time input; #278 writes the chosen language's entry to the
derived store under `sample:<language>`. It is a **Derived asset** (ADR-0005): a
different store from the **Vault**, absent from an export and from every sync
target. The text is authored in this repository and dedicated to the public domain;
the glossary carries the dictionary data's CC BY-SA 4.0 attribution in the same
catalogue, and the packs themselves remain a separate artefact (ADR-0011).

**The roster is data.** A sample source in `samples/` plus its published
**Difficulty table** and **Dictionary pack**s is all a reading language needs; the
tool scans the sources and the manifest and writes every sample it finds. Adding a
language changes no code and no roster list outside the build. A language the
manifest offers no table or no dictionary for is refused, and a pair with no
dictionary has no glossary, which is the state #277 degrades in.

**The launch roster is eight: EN, FR, ES, DE, IT, PT, TR, JA.** The four the
first-launch boards draw are all there, and four more whose packs can explain a
natural text. Korean and Chinese wait: their packs hold the lemmas but not the
surface forms a natural sentence produces — Korean attaches particles (`남자가`,
`집에`) and ICU merges Chinese words (`在家`, `很大`) — so a highlighted word
would open an empty sheet. Shipping them needs the pipeline to map those forms,
not a hand-written glossary.

**It is not the pack.** The glossary is a few hundred words, not the dictionary
data: a 23-word excerpt from one sense per highlighted word, attributed in the
catalogue, shipped so the first launch works offline. The full packs remain in
their own CC BY-SA 4.0 release (ADR-0011), and ADR-0046's rejection of committing
the pipeline's content files is not reopened — those are the bulk intermediate,
not this catalogue.

Rejected: a hand-written palette or meanings (a second definition that drifts);
computing the maps at first launch (the table is a download, and the demonstration
must work before it); publishing the sample only in the data release (the first
launch is exactly when no download has happened); committing a full pack (ADR-0011
keeps dictionary data in its own release); and letting a missing highlighted word
silently produce a sample (the guide would promise a lookup it cannot answer).

**Consequences**: regenerating a sample needs a current data release's difficulty
table and packs, which is a local or CI run of the same CLI; the shipped catalogue
is committed with its six maps and glossaries, so a review sees both the input
change and the produced sample; adding a reading language is a text and its packs;
and the sample format has its own version handshake, duplicated on the producer and
consumer sides and kept in step by an architecture test (ADR-0013).

## Update — the sample is materialised, kept and dismissed outside the vault

The language answer materialises the chosen entry under `samples/<language>.json` in
the **Derived asset** store. The domain identity stays `sample:<language>`, while
`VaultPaths` rejects `:` in a path segment, so the file name encodes it. The sample's
reading position and its **Finished** stamp live beside that file in the same derived
store — never a **Vault** record and never synced — and removing the sample deletes
them with it. The dismissal is a document of its own (`sampleDismissed`), so a corrupt
tutorial cannot resurrect a removed sample and a removal cannot replay the **Guide**;
the guide's progress is a third document (`guide.json`), on ADR-0023's pattern. Saving
a word from the sample records the sample identity `sample:<language>` in the entry's
`bookIds` (ADR-0050): a history entry that never resolves to a **Vault** **Book**.

An explicit replay from Settings ▸ Application may re-run the materialisation —
the answer replaces the device's sample, so the shelf follows the demonstration and
there is still exactly one sample at any moment.

## Update — the authored text is held to the guide's coverage bars

The guide demonstrates one paragraph per band and recolours that same page on every
choice, so the text must carry the colours the screen promises. The sample stage now
refuses a catalogue whose demonstrated paragraph misses the bars (issue #324): at
**A1** one word at every highlighted level (0–3), so the coloured beat's four notes
always exist, and from A1 to **B2** at least three coloured words spanning two
levels, so the level screen never collapses to a lone word. C1 and C2 are exempt: a
reader at that level knows the text, and the thin colour says so. The demonstrated
paragraph is the one the guide would pick — the one covering the most distinct
levels, ties keeping the earlier — and the rule is restated in `tools/dictionaries`
and in `core`'s committed-catalogue suite because neither module may depend on the
other (ADR-0013).

The texts are authored to that spread rather than the mechanism being widened: the
composition stays the real **Difficulty table**'s output, and the maps are
recomposed over the new texts. Every highlighted word keeps its glossary entry in
every published translation language, so the guide's sheet still answers every
colour. The reusable dictionary-data workflow runs the same stage over each
snapshot it publishes and uploads the catalogue for the review that commits it; the
committed bytes are the app's copy and the `core/jvmTest` suite pins the same bars.
