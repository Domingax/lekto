---
status: accepted
---

# The form index is built once per language and shared by every pack

A **Word key** is the lemma when a pack knows one and the normalised surface form
otherwise (ADR-0006), and the **Difficulty table** must key a word exactly as
mastery does, or the two disagree on what a word is (ADR-0029).

The index that resolves a surface form to its lemma is produced today by
`PackBuilder`, and only for entries that carry a translation into *that pack's*
target language. The two shipped packs therefore disagree: EN→FR carries 117,838
forms and EN→ES 107,476. `runCatalogue` then keys each difficulty table from the
first configured pair of its reading language, so English's bands depend on the
order of two lines in `languages.json`.

With nine pairs per language the fault becomes a correctness problem rather than
an ordering quirk. A reader installs exactly one pack — the one for their
**Translation language** — and that pack supplies `lemmaOf`. Two readers with
different translation languages would key the same word differently, and a reader
who changes their translation language would re-key the vocabulary they already
hold.

**One index per reading language, built once, written into every pack and into
the difficulty table.** It is built from the entries of the reading language that
carry at least one translation into a configured target, so every line points at
a lemma some pack defines. `PackBuilder` takes it as an input instead of building
it, and the `form` table is identical across a language's packs — an invariant a
test pins.

Rejected: keeping the per-pair index and designating a source pair, which leaves
the choice arbitrary and the divergence in place; keying difficulty and mastery by
surface form only, which drops the lemma collapsing that mastery exists for;
shipping the index as a separate artefact the runtime loads beside the pack, which
changes the one-file installation contract for a few megabytes.

**Consequences**: every pack of a language repeats the index, adding a few
megabytes to each and roughly 20–35 MB to an English release's nine;
`stats.formCount` becomes identical across a language's packs; `runCatalogue`
loses its `formsByReading.putIfAbsent` ordering dependency; a test pins the shared
index rather than the build order.
