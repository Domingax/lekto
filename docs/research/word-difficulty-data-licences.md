# Per-lemma English difficulty bands: source licences and provenance

**Date:** 2026-10-11
**Scope:** Choose a build-time source for a per-English-lemma **difficulty band** (frequency band or CEFR band) to be baked into Lekto's offline **dictionary pack** (SQLite, ADR-0011) and to drive the "default mastery from the reader's level" feature. The pack is a **separate** release artifact already licensed **CC BY-SA 4.0** (derived from Wiktionary via Wiktextract/Kaikki) with a `NOTICE`, the full CC BY-SA 4.0 text, and metadata keys `license`, `attribution`, `source`, `source_data`, `modifications`, `share_alike`. The application is **AGPL-3.0**. The question is whether a band can be derived from a given upstream source and shipped **inside that same pack**, and on exactly what terms.
**Method:** Primary sources only — the licence files and `NOTICE`s in the source repositories, the CC licence legal code, the official distribution/terms pages, and the peer-reviewed calibration literature. Every licence clause that decides the outcome is reproduced verbatim in **Appendix A**. Live pages were fetched 2026-10-11; the Google Books Ngrams dataset page, the wordfreq repository files and the CC BY-SA 4.0 legal code were read in full. Where a source's licence is *asserted by a redistributor* rather than by the rights holder, that is stated. Nothing in this repo was modified except this file.

> **Headline.** **wordfreq is safe to bake into the existing CC BY-SA 4.0 pack.** Its **data files are CC BY-SA 4.0** (code is Apache-2.0), the same licence as the pack, so there is *no licence conflict* and ShareAlike is already satisfied by the pack's existing licence. The obligations are therefore **attribution only**: credit **wordfreq / Robyn Speer by that exact name**, link the CC BY-SA 4.0 licence, **indicate the modifications** (quantity → band), and carry **three extra credit lines wordfreq itself carries** — *Google Books Ngram Viewer* + link, *OpenSubtitles*, and the *SUBTLEX authors* (with the statement that SUBTLEX is freely available). A derived **band is Adapted Material** (a transformation of the frequencies), so ShareAlike attaches — which is free, because the pack is already CC BY-SA 4.0. **The cleanest more-permissive alternative is Google Books Ngrams v3 itself (CC BY 3.0, attribution only, no ShareAlike)** — a pre-cleaned English 1-gram list with cumulative shares already exists — but it is *books-only* and dated 2019, so it is a *preference*, not a drop-in. **No candidate forces the signal into a separate artifact for licence reasons** given the pack is already CC BY-SA 4.0. What *would* force a separate artifact is pulling in a **non-commercial** source (EFLLex, Kelly) or a **proprietary** one (EVP) — those must be excluded or kept out of the pack entirely.

---

## 1. wordfreq — code, data, and the licence chain

Repository: <https://github.com/rspeer/wordfreq> (default branch `master`), author **Robyn Speer**. Current release cited by the project: `v3.0.2`, DOI <https://doi.org/10.5281/zenodo.7199437>. The data is a **snapshot of usage through ~2021** and the author has stated it **will not be updated** (`SUNSET.md`).

### 1.1 The code is Apache-2.0 — and that is *not* the licence for the data

`LICENSE.txt` (reproduced in full in Appendix A) is the Apache License 2.0, "Copyright 2022 Robyn Speer", and ends with the decisive line:

> "This license applies to the code only. See NOTICE.md for particular details about the licensing of the code and the data."

So the Apache grant covers the Python library, not the frequency lists.

### 1.2 The data files are CC BY-SA 4.0

`NOTICE.md` and the README's *License* section both state, in the same words:

> "`wordfreq` is freely redistributable under the Apache license (see `LICENSE.txt`), and it includes data files that may be redistributed under a Creative Commons Attribution-ShareAlike 4.0 license (<https://creativecommons.org/licenses/by-sa/4.0/>)."

That is the operative licence for the numbers we would band. It is the **same** licence as the Wiktionary-derived pack, so the two data sets are licence-compatible by construction.

### 1.3 The attribution demand is unusually specific: credit "Robyn Speer" by that name

`NOTICE.md` opens with a clause that is part of the licence conditions, not a courtesy:

> "Robyn Speer must be credited as Robyn Speer, which is her maiden name, used on academic work. Crediting her as Elia Robyn Lake (her married name) will make the credit less effective, as it will not line up with other work.
> Crediting Robyn Speer by a different name than one of the above is a serious violation of the license, in which case you do not have permission to use, copy, or redistribute wordfreq."

So the pack's attribution string must contain the literal name **"Robyn Speer"**, not just "wordfreq" and not a renamed variant.

### 1.4 wordfreq explicitly warns against ripping the data into a form that drops attribution

The README answers a "can I convert this to CSV?" question with **No**, and the reason is exactly the failure mode a build pipeline could recreate:

> "**Can I convert wordfreq to a more convenient form for my purposes, like a CSV file?**
> No. The CSV format does not have any space for attribution or license information, and therefore does not follow the CC-By-SA license. Even if you tried to include the proper attribution in a header or in another file, someone would likely just strip it out."

**Consequence for Lekto:** the band **must travel with its attribution inside the pack** (in the existing `NOTICE` + metadata keys, which is exactly the mechanism the pack already uses for Wiktionary), not merely in the app UI and not in a bare `lemma,band` table. A SQLite pack that carries a `NOTICE` table and the metadata keys satisfies this; a stripped two-column export does not.

### 1.5 The upstream sources wordfreq aggregates, and their terms

The README's *Sources and supported languages* section says wordfreq draws on a Luminoso project ("Exquisite Corpus") compiling **8 domains**: Wikipedia; Subtitles (OPUS OpenSubtitles 2018 + SUBTLEX); News (NewsCrawl 2014 + GlobalVoices); Books (Google Books Ngrams 2012); Web (OSCAR); Twitter; Reddit; and (for Chinese only) Jieba. **English uses 7 of these** (all but the misc/Jieba source). The `NOTICE.md` is the document that conveys the *upstream terms a redistributor must honour*; it names:

**(a) Google Books Ngrams and Google Books Syntactic Ngrams.** `NOTICE.md` quotes the terms-of-use text:

> "Ngram Viewer graphs and data may be freely used for any purpose, although acknowledgement of Google Books Ngram Viewer as the source, and inclusion of a link to http://books.google.com/ngrams, would be appreciated."

Independently, the Google Books **dataset** download page states the *compilation's* licence more strongly: *"This compilation is licensed under a Creative Commons Attribution 3.0 Unported License."* So the raw Google data is **CC BY 3.0** (attribution, **no** ShareAlike) — see §3.2.

**(b) OPUS OpenSubtitles 2018.** `NOTICE.md`:

> "It contains data from OPUS OpenSubtitles 2018 (<http://opus.nlpl.eu/OpenSubtitles.php>), whose data originates from the OpenSubtitles project (<http://www.opensubtitles.org/>) and may be used with attribution to OpenSubtitles."

The upstream OpenSubtitles licensing is **inconsistent across redis tributors** and is a real caveat: an archived META-SHARE record for the OPUS subtitle corpus carries "Academic – Non Commercial Use, Attribution" and "Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License"; the current OpenSubtitles2024 release on Hugging Face is labelled **ODC-BY**; a Tatoeba mirror says **CC BY-NC-SA 4.0**. The subtitle *text* is user-contributed and its rights were never cleared from film/TV owners. This is precisely why relying on **wordfreq's aggregation and its explicit CC BY-SA 4.0 grant** (with the OpenSubtitles credit retained) is safer than going to the raw OpenSubtitles corpus ourselves.

**(c) SUBTLEX-US, -UK, -CH, -DE, -NL (Brysbaert et al.).** `NOTICE.md` records a **bespoke email permission**, not a standard open licence:

> "I (Robyn Speer) have obtained permission by e-mail from Marc Brysbaert to distribute these wordlists in wordfreq, to be used for any purpose, not just for academic use, under these conditions:
> - Wordfreq and code derived from it must credit the SUBTLEX authors.
> - It must remain clear that SUBTLEX is freely available data.
> These terms are similar to the Creative Commons Attribution-ShareAlike license."

The Ghent University SUBTLEX-US distribution page itself **states no licence** (it only documents the files and asks for a citation); a third-party mirror (`openlexicon`) labels SUBTLEX-US "LICENSE: CC-BY-SA". The safe, primary-source-backed formulation is therefore the wordfreq one: a **permission with conditions** (credit the SUBTLEX authors; keep clear that SUBTLEX is free), not an official CC licence we could point a scanner at.

**(d) CC-licensed corpora.** `NOTICE.md` lists *"The Leeds Internet Corpus … Wikipedia … ParaCrawl"* as "Creative Commons-licensed sources". The README's composition also lists OSCAR (Web), NewsCrawl 2014 and GlobalVoices (News), and Reddit.

**(e) Twitter.** `NOTICE.md`: *"Some additional data was collected by a custom application that watched the streaming Twitter API, in accordance with Twitter's Developer Agreement & Policy. This software gives statistics about words …; it does not display or republish any Twitter content."* i.e. only derived statistics, no content — the basis is "statistics, not text".

### 1.6 Is a derived *band* an "Adaptation", and does ShareAlike attach?

Yes. CC BY-SA 4.0 §1(a) defines **Adapted Material** as material "derived from or based upon the Licensed Material and in which the Licensed Material is translated, **altered, arranged, transformed, or otherwise modified in a manner requiring permission**…". Turning a frequency value into a **band** (a coarse re-expression: dropping the numeric value, re-binning, and re-keying form→lemma) is a transformation → **Adapted Material**. Two consequences:

- §3(b)(1) requires the Adapter's License to be "a Creative Commons license with the same License Elements, this version or later, or a BY-SA Compatible License" → the band data must be **CC BY-SA 4.0 (or later)**. Since the pack *is* CC BY-SA 4.0, this is already satisfied — we simply keep the pack under the licence it already carries.
- §3(a)(1)(B) requires us to "**indicate if You modified the Licensed Material and retain an indication of any previous modifications**". The pack's `modifications` metadata key exists for exactly this; it must say, e.g., *"frequency quantified to a 6-band scale; form frequencies aggregated to Wiktionary lemmas"*.

§4 (Sui Generis Database Rights) reinforces the same result: if we "include all or a substantial portion of the database contents in a database in which You have Sui Generis Database Rights, then the database … (but not its individual contents) is **Adapted Material**" and must carry the §3(a) notices. Our pack is such a database.

**Caveat on the strength of the grant.** wordfreq grants CC BY-SA 4.0 over its *aggregate*; some of its inputs (SUBTLEX, OpenSubtitles, Google) carry their own bespoke terms, which is why `NOTICE.md` reproduces them. A careful redistributor treats the grant as valid **provided the upstream credits are retained**, which is what `NOTICE.md` asks. We are not aware of any upstream source forbidding a share-alike redistribution that carries its credit; none of the quoted clauses is non-commercial or no-derivatives.

---

## 2. Can a derived band live in the existing CC BY-SA 4.0 pack?

**Yes, and mixing changes nothing, because every adapted element is already CC BY-SA 4.0.**

- **Adaptation vs. collection.** CC BY-SA 4.0 does not need a "collection" carve-out here. Wiktionary/Wiktextract data is an adaptation of Wiktionary (already CC BY-SA 4.0); the band is an adaptation of wordfreq (CC BY-SA 4.0). Both adapted elements carry **the same Adapter's License (CC BY-SA 4.0)**, so the combined work can be licensed CC BY-SA 4.0 without conflict. (§1(a) "Adapted Material"; §3(b)(1) Adapter's License.)
- **Mixing Wiktionary + wordfreq.** Both sources are CC BY-SA 4.0, so there is no "BY-SA + something incompatible" problem. The pack simply accumulates **two sets of attribution/citation notices and one shared ShareAlike notice**. §3(a)(1)(A) requires retaining each licensor's attribution notices; the pack's existing `attribution`/`source`/`source_data` keys should gain a **wordfreq block**.
- **Modification marking** is mandatory (§3(a)(1)(B)) and already has a home: the `modifications` key.
- **The AGPL app boundary.** The application is AGPL-3.0 and the pack is a *separate* artifact (ADR-0011). CC BY-SA data sitting *next to* AGPL code is an aggregation, not a derivative of the program; the two copylefts do not collide. This is the arrangement ADR-0011 already assumes for the Wiktionary data and it is unchanged by adding wordfreq-derived bands. The one discipline to keep: **do not compile the data into the AGPL binary** in a way that would make it look like a single work, and keep the pack's own `NOTICE`/licence text shipping with the pack.

**Attribution lines the pack must carry (minimum).** Extending the existing attribution block:

> Difficulty bands derived from **wordfreq** by **Robyn Speer** (<https://github.com/rspeer/wordfreq>), data licensed **CC BY-SA 4.0** (<https://creativecommons.org/licenses/by-sa/4.0/>). Modified: word frequencies re-expressed as bands and aggregated to lemmas. wordfreq itself contains data from: **Google Books Ngram Viewer** (<https://books.google.com/ngrams>) and Google Books Syntactic Ngrams; **OpenSubtitles** (<https://www.opensubtitles.org/>); **Wikipedia**; **ParaCrawl**; the **Leeds Internet Corpus**; and the **SUBTLEX** word lists by **Brysbaert et al.** (SUBTLEX is freely available data). wordfreq's code is Apache-2.0.

Everything in that block is traceable to `NOTICE.md`. The three additions over and above a generic "CC BY-SA" line are the ones the NOTICE demands: **the exact name "Robyn Speer"**, **Google Books Ngram Viewer + link**, and **the SUBTLEX authors + "freely available data"**.

**Bottom line for §2:** no separate artifact is required for *licence* reasons. A separate artifact is optional engineering hygiene (it isolates the wordfreq notices so they cannot be lost, per §1.4) but not a legal necessity.

---

## 3. Alternatives

### 3.1 Ready CEFR-graded English lists

**(i) CEFR-J Wordlist v1.6 — bespoke permission, not a standard licence.** The list is compiled by **Yukio Tono, Tono Laboratory, Tokyo University of Foreign Studies**; v1.5 is mirrored by `openlanguageprofiles/olp-en-cefrj`. The repository's terms:

> "CEFR-J vocabulary and grammar profile datasets can be used for research and commercial purposes with no charge, provided that you cite the dataset properly. The copyright belongs to Tono Laboratory at TUFS."

The Japanese source page adds that the list **may be modified** to create another list but **must be cited**, and that commercial use may attract a separate supervision fee in some cases. Size/coverage (v1.6, per the TUFS page): **A1 1,166 / A2 1,411 / B1 2,445 / B2 2,779 items (7,801 total; 6,868 headwords)**; **A1–B2 only — no C1/C2**. Trap: this is a **bespoke permission, not an OSI/CC licence**; it grants "use … provided you cite", which reads permissive but does **not** include an explicit right to **relicense** the data. We should not merge it into the CC BY-SA pack as if it were CC BY-SA.

**(ii) Octanove Vocabulary Profile C1/C2 — CC BY-SA 4.0.** Fills CEFR-J's C1/C2 gap; same repository. Terms: *"The Octanove Vocabulary Profile for C1/C2 levels can be used under a Creative Commons Attribution-ShareAlike 4.0 International License."* Because it is already CC BY-SA 4.0 it is licence-compatible with the pack. Trap: it is a small, LLM/curated supplement (Octanove Labs), not an authority.

**(iii) English Vocabulary Profile (EVP) — proprietary; its Kaggle/GitHub "copies" are unauthorised.** EVP is a Cambridge University Press & Assessment product. The EVP terms page states that *"Cambridge University Press owns or is licensed to use all intellectual property rights in and to the EVP Site and all technical infrastructure relating to it"*, and the site footer is "Copyright 2026 Cambridge University Press & Assessment / Terms of Use". Two independent research groups document the restriction:
  - Wolfer & Lew, *Humanities and Social Sciences Communications* 12:1151 (2025), open access: they used EVP but note *"Due to copyright restrictions, we were unable to use the [Oxford Learner's Word Lists]"* — i.e. the closed graded lists are known to be unusable as source data.
  - Kikuchi et al., *CEFR-Annotated WordNet* (arXiv:2510.18466): *"We accessed the EVP Online strictly in accordance with the EVP website's Terms of Use and Cambridge University Press's text and data mining (TDM) terms. Any EVP content temporarily cached to local storage for this project was deleted upon the project's completion. All released artifacts are built on WordNet and SemCor, whose licenses permit copying, modification, and redistribution, and include only derived CEFR labels mapped to WordNet sense keys."*

  **This is the single biggest trap in the landscape:** the numerous "English words by CEFR level" Kaggle/GitHub datasets (`Maximax67/Words-CEFR-Dataset`, "CEFR word lists", etc.) are widely **re-uploads of EVP**. EVP is not openly licensed, so those datasets carry no valid grant; do **not** build on them. A legitimate CEFR signal must be rebuilt on a permissive base (WordNet/SemCor) or come from an openly-licensed list.

**(iv) EFLLex / CEFRLex — CC BY-NC-SA 4.0 (non-commercial trap).** The English CEFR-graded lexicon of **15,280 lemmas** across A1–C1, from 13 EFL textbooks and 8 online resources (Dürlich & François, LREC 2018). The download page states: *"This work is licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0) License."* **The NonCommercial element is incompatible with a pack that may be used commercially** — it cannot be merged into a CC BY-SA 4.0 pack without tainting it, and must be kept out (or in a separate NC artifact whose use is restricted). Coverage is A1–C1 (no C2) and keyed by lemma+POS.

**(v) Kelly project lists — CC BY-NC-ND-NC-SA 2.0.** Listed on Wiktionary's frequency-list page and flagged there as *"incompatible with wiktionary"* for exactly the NC reason. Same exclusion as EFLLex.

**(vi) CEFR-Annotated WordNet — permissive base, EVP-derived labels.** Built on WordNet (WordNet License, permissive) and SemCor, with CEFR labels transferred from EVP via an LLM; released on Zenodo (`10.5281/zenodo.17395388`). It covers 5,645 lemmas / 10,644 senses, sense-level, but is **an inferred signal** (F1 ≈ 0.81 versus gold labels) and its provenance runs through EVP. Treat as a research-grade cross-check, not a clean primary source.

### 3.2 Permissive (CC BY / CC0-ish / public-domain) frequency sources

**(i) Google Books Ngrams v3 (2020-02-17) datasets — CC BY 3.0, attribution only.** The dataset page states the compilation licence (quoted in §1.5a). English 1-grams are ~2.0 trillion tokens all-years. A **pre-cleaned, language-learning-oriented derivative already exists**: `orgtre/google-books-ngram-frequency` ships the **top 10,000 English 1-grams** for 2010–2019 with a `cumshare` column (cumulative coverage), and its own licence is **CC BY 3.0**. Because CC BY carries **no ShareAlike**, a band derived from it need not be CC BY-SA — it only needs attribution to the Google Books Ngram Viewer (+ link). Traps: **books-only register** (no subtitles/web) and a **2019 cutoff**; wordfreq itself exists partly to correct for books-only skew.

**(ii) Leipzig Corpora Collection / Wortschatz Leipzig — CC BY 4.0.** English word lists from 10K to ~1M words, from news + web, plain-text, with frequency statistics. Multiple independent listings (Wiktionary's frequency-list pages; the OpenCorpus index) state **CC BY 4.0**. More permissive than CC BY-SA. Traps: served behind a bot-protection gate; lists are **full forms**, not lemmas (needs a form→lemma mapping, same trap as wordfreq); register is news/web.

**(iii) Wikipedia word frequency (IlyaSemenov) — MIT repo, CC BY-SA underlying.** `wikipedia-word-frequency` generates an English list (**2,747,823 words appearing in ≥3 articles**), repo under **MIT**. Trap: the *underlying* text is Wikipedia (CC BY-SA 4.0/GFDL). Raw counts are arguably non-copyrightable facts, but the safest reading is that the output derives from CC BY-SA text; if we use it, treat it as **CC BY-SA**, not MIT. Register is encyclopedic.

**(iv) Leeds Internet Corpus — CC BY 2.5 (attribution only).** The list wordfreq uses; the Wiktionary frequency pages describe the Leeds lists as *"derived from corpora assembled by Leeds University's Centre for Translation Studies (CC BY-2.5)"*. More permissive than CC BY-SA. Small/old.

**(v) Hermit Dave FrequencyWords — CC BY-SA 4.0 content.** 50K+ English word lists derived from **OpenSubtitles** (2016/2018). README: *"MIT License for code. CC-by-sa-4.0 for content."* Same licence family as wordfreq and same OpenSubtitles upstream; a direct-from-source alternative whose licence is CC BY-SA 4.0 but whose upstream (OpenSubtitles) carries the same ambiguity noted in §1.5b.

**(vi) BNC frequency lists (Kilgarriff / UCREL) — CC BY-SA 2.0 UK.** *"These lists are licensed under a Creative Commons Attribution-Share Alike 2.0 UK: England & Wales License."* Older (BNC ≈ 100M words, 1990s). ShareAlike (older version).

**(vii) Open English WordNet — CC BY 4.0.** Not a frequency list; a lexical resource usable to key lemmas and (with permissive bases) to rebuild CEFR labels. Per the Wiktionary frequency page.

### 3.3 Candidate comparison

| Source | Licence (data) | Provenance / register | Size / coverage | Keys to Wiktionary lemmas? | Trap |
|---|---|---|---|---|---|
| **wordfreq** data | **CC BY-SA 4.0** | aggregated 7 English corpora (Wikipedia, subtitles, news, books, web, Twitter, Reddit) | large 1–8 Zipf, ≥1/10⁸ | **No — surface forms**, needs form→lemma | upstream credits (Google/OpenSubtitles/SUBTLEX) must be carried; frozen ~2021 |
| Google Books Ngrams v3 (datasets / orgtre) | **CC BY 3.0** | books only, ≤2019 | 2.0T tokens; orgtre: top 10K 1-grams + cumshare | forms | books-only skew; dated |
| Leipzig Corpora (Wortschatz) | **CC BY 4.0** | news + web | 10K–1M words | forms (POS/lemma tagging varies) | bot gate; forms |
| Wikipedia word frequency (IlyaSemenov) | MIT repo / **CC BY-SA underlying** | encyclopedic | 2.75M words (≥3 articles) | forms | licence mismatch risk |
| Leeds Internet Corpus | **CC BY 2.5** | web corpus | small/old | forms | date/coverage |
| Hermit Dave FrequencyWords | **CC BY-SA 4.0** | OpenSubtitles 2016/2018 | 50K+ | forms | OpenSubtitles upstream ambiguity |
| BNC lists (Kilgarriff/UCREL) | **CC BY-SA 2.0 UK** | balanced 1990s corpus | ~100M words | word families/forms | age |
| **CEFR-J Wordlist v1.6** | **bespoke** (cite; TUFS copyright) | Japanese school textbook corpora + EVP diff | 7,801 items, A1–B2 only | headword+POS | not a standard licence; no relicensing grant; no C1/C2 |
| **Octanove C1/C2** | **CC BY-SA 4.0** | curated supplement | C1/C2 | lemma+POS | small |
| **EFLLex / CEFRLex** | **CC BY-NC-SA 4.0** | 13 EFL textbooks + 8 online | 15,280 lemmas, A1–C1 | lemma+POS | **NonCommercial → unusable in the pack** |
| Kelly project | **CC BY-NC-ND-NC-SA 2.0** | learner lists | several languages | lemma | **NonCommercial + NoDerivs** |
| **EVP (Cambridge)** | **proprietary** | Cambridge Learner Corpus | A1–C2, sense-level | sense | **not licensable; Kaggle/GitHub copies are infringing** |
| CEFR-Annotated WordNet | WordNet licence base; EVP-derived | WordNet + SemCor + EVP labels | 5,645 lemmas, sense-level | WordNet senses | inferred (F1≈0.81); EVP provenance |

---

## 4. Calibrating frequency → CEFR band

This is a *modelling* choice, independent of the licence, but it must be **citable** and recorded in the pack manifest so the band is reproducible. There is no official CEFR vocabulary size or list; the framework is explicitly descriptor-based. Four usable, published anchors:

1. **Coverage thresholds (Nation).** Nation (2006), *How large a vocabulary is needed for reading and listening?*: **98% coverage** of written text needs **8,000–9,000 word families**, spoken **6,000–7,000**; ~2,000 families is the earlier comprehension threshold. This gives the *top* of the band scale.
2. **Vocabulary size per CEFR level (Milton & Alexiou / Meara & Milton).** From the EUROSLA monograph, Table 5 (adapted from Meara & Milton 2003), the **XLex** mapping: **A1 <1500; A2 1500–2500; B1 2750–3250; B2 3250–3750; C1 3750–4500; C2 4500–5000.** Applied to a rank-ordered lemma list, this gives a **rank→CEFR** banding directly: rank ≤1500 → A1, ≤2500 → A2, ≤3250 → B1, ≤3750 → B2, ≤4500 → C1, ≤5000 → C2.
3. **Frequency ↔ CEFR medians (Wolfer & Lew 2025, CC BY 4.0 open access).** Their Random-Forest imputation reports median **corpus frequency (per million words)** per coarse level: **A ≈ 84.9, B ≈ 3.9, C ≈ 0.18**. On the Zipf scale wordfreq uses (log₁₀ occurrences per billion) those are **A1/A2-ish ≈ Zipf 4.93, B-ish ≈ 3.59, C-ish ≈ 2.26** — a direct empirical anchor for *frequency→band* cut points, useful to validate band edges.
4. **Threshold-share method (GerVLPro, LREC 2026, open).** A reproducible algorithm: assign a lemma to the lowest level ℓ where its normalised frequency share `s(LP,ℓ) ≥ x` for a tunable threshold `x`, with the reference level distribution built as the average of existing graded lists. Useful if we want a data-driven rather than hand-set boundary.

**Practical recipe for the pack (recorded in the manifest).** Rank English **lemmas** by frequency (wordfreq `zipf_frequency`, or the CC BY 3.0 Google list), cut the ranking at the Milton/Alexiou sizes (1500/2500/3250/3750/4500/5000) to assign A1…C2, and **validate the resulting Zipf cut points against the Wolfer & Lew medians**; record `source`, `calibration` (the source citations above), and `modifications` in the pack.

**Mandatory integration trap: wordfreq is *forms*, the pack is *lemmas*.** wordfreq's lists contain tokenised **surface forms** (its `tokenize`/`top_n_list` operate on words, not lemmas); English is not lemmatised. The Wiktextract pack is keyed by **lemma**. So a build step must **aggregate form frequencies to a lemma** (via the `forms`/`form_of` reverse index the pack already builds for lookup, or the lemma's own surface frequency). Bands computed on raw wordfreq rows and keyed by form would mis-level every inflected lemma. All the permissive alternatives in §3.2 share the same forms-not-lemmas property.

---

## Appendix A — verbatim licence clauses and key quotes

### A.1 wordfreq — `LICENSE.txt` (full text; code only)

```
Copyright 2022 Robyn Speer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

This license applies to the code only. See NOTICE.md for particular details
about the licensing of the code and the data.
```

### A.2 wordfreq — `NOTICE.md` (full text; the data licence)

```
wordfreq
Copyright 2022 Robyn Speer

# Attribution notes

Robyn Speer must be credited as Robyn Speer, which is her maiden name, used on academic work.
Crediting her as Elia Robyn Lake (her married name) will make the credit less effective, as it will
not line up with other work.

Crediting Robyn Speer by a different name than one of the above is a serious violation of the license,
in which case you do not have permission to use, copy, or redistribute wordfreq.

If you use wordfreq in academic work, you must cite it. See "Citing wordfreq" in README.md.

# Included licenses

`wordfreq` is freely redistributable under the Apache license (see
`LICENSE.txt`), and it includes data files that may be
redistributed under a Creative Commons Attribution-ShareAlike 4.0
license (<https://creativecommons.org/licenses/by-sa/4.0/>).

`wordfreq` contains data extracted from Google Books Ngrams
(<http://books.google.com/ngrams>) and Google Books Syntactic Ngrams
(<http://commondatastorage.googleapis.com/books/syntactic-ngrams/index.html>).
The terms of use of this data are:

    Ngram Viewer graphs and data may be freely used for any purpose, although
    acknowledgement of Google Books Ngram Viewer as the source, and inclusion
    of a link to http://books.google.com/ngrams, would be appreciated.

`wordfreq` also contains data derived from the following Creative Commons-licensed
sources:

- The Leeds Internet Corpus, from the University of Leeds Centre for Translation
  Studies (<http://corpus.leeds.ac.uk/list.html>)

- Wikipedia, the free encyclopedia (<http://www.wikipedia.org>)

- ParaCrawl, a multilingual Web crawl (<https://paracrawl.eu>)

It contains data from OPUS OpenSubtitles 2018
(<http://opus.nlpl.eu/OpenSubtitles.php>), whose data originates from the
OpenSubtitles project (<http://www.opensubtitles.org/>) and may be used with
attribution to OpenSubtitles.

It contains data from various SUBTLEX word lists: SUBTLEX-US, SUBTLEX-UK,
SUBTLEX-CH, SUBTLEX-DE, and SUBTLEX-NL, created by Marc Brysbaert et al.
(see citations below) and available at
<http://crr.ugent.be/programs-data/subtitle-frequencies>.

I (Robyn Speer) have obtained permission by e-mail from Marc Brysbaert to
distribute these wordlists in wordfreq, to be used for any purpose, not just
for academic use, under these conditions:

- Wordfreq and code derived from it must credit the SUBTLEX authors.
- It must remain clear that SUBTLEX is freely available data.

These terms are similar to the Creative Commons Attribution-ShareAlike license.

Some additional data was collected by a custom application that watched the
streaming Twitter API, in accordance with Twitter's Developer Agreement &
Policy. This software gives statistics about words that were commonly used on
Twitter; it does not display or republish any Twitter content, and does not
contain any content from after Twitter's sale.
```

### A.3 wordfreq — README, *License* and the CSV answer

> "`wordfreq` is freely redistributable under the Apache license (see `LICENSE.txt`), and it includes data files that may be redistributed under a Creative Commons Attribution-ShareAlike 4.0 license (<https://creativecommons.org/licenses/by-sa/4.0/>)."

> "**Can I convert wordfreq to a more convenient form for my purposes, like a CSV file?**
> No. The CSV format does not have any space for attribution or license information, and therefore does not follow the CC-By-SA license. Even if you tried to include the proper attribution in a header or in another file, someone would likely just strip it out."
>
> "wordfreq isn't particularly separable from its code, anyway. It depends on its normalization and word segmentation process, which is implemented in Python code, to give appropriate results."

### A.4 wordfreq — README, corpus composition (English = 7 sources)

> "Exquisite Corpus compiles 8 different domains of text, some of which themselves come from multiple sources:
> - **Wikipedia**, representing encyclopedic text
> - **Subtitles**, from OPUS OpenSubtitles 2018 and SUBTLEX
> - **News**, from NewsCrawl 2014 and GlobalVoices
> - **Books**, from Google Books Ngrams 2012
> - **Web** text, from OSCAR
> - **Twitter**, representing short-form social media
> - **Reddit**, representing potentially longer Internet comments
> - **Miscellaneous** word frequencies: in Chinese … Jieba …"

English row in the supported-languages table is marked `Yes` for WP, Subs, News, Books, Web, Twit., Redd. (7 sources).

### A.5 wordfreq — `SUNSET.md` (data frozen; author's wishes)

> "The wordfreq data is a snapshot of language that could be found in various online sources up through 2021. There are several reasons why it will not be updated anymore."
> …
> "So I don't want to work on anything that could be confused with generative AI, or that could benefit generative AI."

### A.6 CC BY-SA 4.0 — the decisive clauses (legalcode)

**§1(a) — "Adapted Material":**

> "Adapted Material means material subject to Copyright and Similar Rights that is derived from or based upon the Licensed Material and in which the Licensed Material is translated, altered, arranged, transformed, or otherwise modified in a manner requiring permission under the Copyright and Similar Rights held by the Licensor. …"

**§3(a)(1) — Attribution:**

> "If You Share the Licensed Material (including in modified form), You must:
> A. retain the following if it is supplied by the Licensor with the Licensed Material:
>   i. identification of the creator(s) …;
>   ii. a copyright notice;
>   iii. a notice that refers to this Public License;
>   iv. a notice that refers to the disclaimer of warranties;
>   v. a URI or hyperlink to the Licensed Material …;
> B. indicate if You modified the Licensed Material and retain an indication of any previous modifications; and
> C. indicate the Licensed Material is licensed under this Public License, and include the text of, or the URI or hyperlink to, this Public License."

**§3(b)(1) — ShareAlike:**

> "The Adapter's License You apply must be a Creative Commons license with the same License Elements, this version or later, or a BY-SA Compatible License."

**§4 — Sui Generis Database Rights (relevant part):**

> "… if You include all or a substantial portion of the database contents in a database in which You have Sui Generis Database Rights, then the database in which You have Sui Generis Database Rights (but not its individual contents) is Adapted Material, including for purposes of Section 3(b); and You must comply with the conditions in Section 3(a) if You Share all or a substantial portion of the contents of the database."

### A.7 Google Books Ngrams — dataset licence

From the corpus download page (<https://storage.googleapis.com/books/ngrams/books/datasetsv3.html>):

> "**Usage:** This compilation is licensed under a [Creative Commons Attribution 3.0 Unported License](http://creativecommons.org/licenses/by/3.0/)."

(The `NOTICE.md` above, by contrast, quotes the *viewer's* softer terms: "… acknowledgement … would be appreciated".)

### A.8 `orgtre/google-books-ngram-frequency` — pre-cleaned CC BY 3.0 list

> "The content of this repository is licensed under the [Creative Commons Attribution 3.0 Unported License](https://creativecommons.org/licenses/by/3.0/)."

Ships, for English, the "10.000 most frequent 1-grams" restricted to books published 2010–2019, each with `freq` and a `cumshare` (cumulative coverage) column.

### A.9 CEFR-J Wordlist — terms (TUFS Tono Laboratory)

English (mirror README):

> "CEFR-J vocabulary and grammar profile datasets can be used for research and commercial purposes with no charge, provided that you cite the dataset properly. The copyright belongs to Tono Laboratory at TUFS (Tokyo University of Foreign Studies)."

Japanese (source download page), the modification clause:

> "本語彙表を改変して別の語彙表を作ることはかまわないが、必ず本語彙表を適切に引用しなければならない。"
> *(You may modify this wordlist to create another wordlist, but you must properly cite this wordlist.)*

and

> "なお、本語彙表の著作権は東京外国語大学投野研究室に帰属するが、適切な引用を行っていただければ研究教育および商用においても無償で利用できる。"
> *(The copyright belongs to Tono Laboratory at TUFS, but it can be used free of charge for research/education and commercial purposes provided it is properly cited.)*

Required citation: *"The CEFR-J Wordlist Version 1.6. Compiled by Yukio Tono, Tokyo University of Foreign Studies."*

### A.10 Octanove Vocabulary Profile C1/C2 — licence

> "The Octanove Vocabulary Profile for C1/C2 levels can be used under a [Creative Commons Attribution-ShareAlike 4.0 International License](https://creativecommons.org/licenses/by-sa/4.0/)."

### A.11 EFLLex / CEFRLex — licence (NonCommercial trap)

From the EFLLex download page:

> "This work is licensed under a [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0) License](https://creativecommons.org/licenses/by-nc-sa/4.0/)."

### A.12 Wiktionary frequency-list page — the Kelly NC/ND flag

> "Frequency lists for learners … as part of the Kelly project. (CC BY-ND-NC-SA 2.0)"
> footnote: "Creative Commons Attribution-NonCommercial-ShareAlike 2.0 Generic Licence, meaning it is incompatible with wiktionary."

And, for the permissive enumerations used in §3.2:

> "Word frequency lists for English and other languages from 10K up to 1M … as part of the Leipzig Corpora Collection (CC BY-4.0)"
> "50K and larger word lists based on www.opensubtitles.org … (CC BY-SA-4.0)"
> "Frequency lists … derived from corpora assembled by Leeds University's Centre for Translation Studies (CC BY-2.5)"
> "Wikipedia word frequency list (2023) … (MIT License)"
> "Open English WordNet … (CC BY-4.0)"

### A.13 SUBTLEX — Ghent University page (no licence stated)

The SUBTLEX-US distribution page (<https://www.ugent.be/pp/experimentele-psychologie/en/research/documents/subtlexus>) offers the files and says only:

> "If you use SUBTLEX-UK in your research, please cite the publication below." (analogous on the US page)

It states **no licence**. A third-party mirror (`chrplr/openlexicon`) labels it "LICENSE: CC-BY-SA". The authoritative redistribution condition for a wordfreq reuser is therefore the **email permission reproduced in wordfreq's `NOTICE.md`** (Appendix A.2).

### A.14 OPUS / OpenSubtitles — inconsistent upstream licences

- wordfreq `NOTICE.md`: "may be used with attribution to OpenSubtitles."
- Archived META-SHARE record for the OPUS subtitle corpus: "Restrictions: Academic - Non Commercial Use, Attribution" and "Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License".
- Hugging Face `Helsinki-NLP/OpenSubtitles2024`: "This dataset is released under the ODC-BY license. The dataset redistributes subtitle texts originally contributed to OpenSubtitles.org. The authors do not claim ownership of the subtitle content and maintain a takedown policy for copyright holders."

The disagreement is the reason to rely on wordfreq's aggregate CC BY-SA 4.0 grant (with the OpenSubtitles credit) rather than the raw corpus.

### A.15 EVP (Cambridge) — proprietary

- EVP Terms of Use page (<https://englishprofile.org/?menu=evp-terms-of-use>): "Cambridge University Press owns or is licensed to use all intellectual property rights in and to the EVP Site and all technical infrastructure relating to it."
- Site footer: "Copyright 2026 Cambridge University Press & Assessment / Terms of Use".
- Kikuchi et al. (arXiv:2510.18466), on handling EVP data: "We accessed the EVP Online strictly in accordance with the EVP website's Terms of Use and Cambridge University Press's text and data mining (TDM) terms. Any EVP content temporarily cached to local storage for this project was deleted upon the project's completion. All released artifacts are built on WordNet and SemCor, whose licenses permit copying, modification, and redistribution, and include only derived CEFR labels mapped to WordNet sense keys."
- Wolfer & Lew (HSSC 12:1151, 2025): "Due to copyright restrictions, we were unable to use the [Oxford Learner's Word Lists]."

### A.16 Calibration — Milton & Alexiou / Meara & Milton `Table 5`

> "Table 5. Approximate vocabulary size scores associated with CEFR levels (adapted from Meara & Milton, 2003, p. 8)

| CEFR | Cambridge exams | XLex (5000 max) |
|---|---|---|
| A1 | Starters, Movers and Flyers | <1500 |
| A2 | Kernel English Test | 1500 – 2500 |
| B1 | Preliminary English Test | 2750 – 3250 |
| B2 | First Certificate in English | 3250 – 3750 |
| C1 | Cambridge Advanced English | 3750 – 4500 |
| C2 | Cambridge Proficiency in English | 4500 – 5000 |"

### A.17 Calibration — Wolfer & Lew (2025) median frequencies per level

> "the most basic level A is associated with the highest word frequencies (with a median of 84.9 per million words), while C corresponds to the lowest corpus frequency ranges (median of 0.18 per million words). Level B is found in between the two extremes, with a median of 3.9 occurrences per million running words."

(Converted to wordfreq's Zipf scale, log₁₀ per billion: A ≈ **4.93**, B ≈ **3.59**, C ≈ **2.26**.)

### A.18 Calibration — Nation (2006) coverage thresholds

> "If we take 98% as the ideal coverage, a 8,000–9,000 word-family vocabulary is needed for dealing with written text, and 6,000–7,000 families for dealing with spoken text."

---

## Appendix B — references

**wordfreq**
- Repository — https://github.com/rspeer/wordfreq
- `LICENSE.txt` — https://raw.githubusercontent.com/rspeer/wordfreq/master/LICENSE.txt
- `NOTICE.md` — https://raw.githubusercontent.com/rspeer/wordfreq/master/NOTICE.md
- `README.md` (License, CSV answer, sources) — https://raw.githubusercontent.com/rspeer/wordfreq/master/README.md
- `SUNSET.md` — https://raw.githubusercontent.com/rspeer/wordfreq/master/SUNSET.md
- Citation / DOI — Speer, R. (2022). *rspeer/wordfreq: v3.0* (v3.0.2). Zenodo. https://doi.org/10.5281/zenodo.7199437

**Licence texts**
- CC BY-SA 4.0 legal code — https://creativecommons.org/licenses/by-sa/4.0/legalcode
- CC BY-SA 4.0 deed — https://creativecommons.org/licenses/by-sa/4.0/
- CC BY 3.0 — https://creativecommons.org/licenses/by/3.0/
- CC BY-NC-SA 4.0 — https://creativecommons.org/licenses/by-nc-sa/4.0/

**Upstream source terms**
- Google Books Ngram datasets (usage/licence) — https://storage.googleapis.com/books/ngrams/books/datasetsv3.html
- `orgtre/google-books-ngram-frequency` (CC BY 3.0) — https://github.com/orgtre/google-books-ngram-frequency
- OPUS — https://opus.nlpl.eu/ · OpenSubtitles — https://www.opensubtitles.org/
- Simard/Bryant-Coutts META-SHARE record for the OPUS subtitle corpus — http://metashare.ilsp.gr:8080/repository/browse/opus-subtitles-corpus/ (archived)
- Hugging Face `Helsinki-NLP/OpenSubtitles2024` (ODC-BY) — https://huggingface.co/datasets/Helsinki-NLP/OpenSubtitles2024
- Ghent University SUBTLEX-US page (no licence) — https://www.ugent.be/pp/experimentele-psychologie/en/research/documents/subtlexus
- `openlexicon` SUBTLEX-US mirror ("LICENSE: CC-BY-SA") — https://github.com/chrplr/openlexicon/blob/master/datasets-info/SUBTLEX-US/README-SUBTLEXus.md

**CEFR-graded lists**
- CEFR-J resources (Tono Lab, TUFS) — https://www.cefr-j.org/download.html
- `openlanguageprofiles/olp-en-cefrj` (CEFR-J + Octanove terms) — https://github.com/openlanguageprofiles/olp-en-cefrj
- EFLLex / CEFRLex (CC BY-NC-SA 4.0) — https://cental.uclouvain.be/cefrlex/efllex/
- English Profile / EVP (proprietary) — https://englishprofile.org
- Kelly project lists — http://corpus.leeds.ac.uk/serge/kelly/

**Permissive frequency sources**
- Leipzig Corpora Collection (CC BY 4.0) — https://wortschatz.uni-leipzig.de/en/
- `IlyaSemenov/wikipedia-word-frequency` — https://github.com/IlyaSemenov/wikipedia-word-frequency
- Leeds Internet Corpus — http://corpus.leeds.ac.uk/list.html
- `hermitdave/FrequencyWords` (CC BY-SA 4.0) — https://github.com/hermitdave/FrequencyWords
- BNC frequency lists (UCREL) — https://ucrel.lancs.ac.uk/bncfreq/
- Wiktionary:Frequency lists/English — https://en.wiktionary.org/wiki/Wiktionary:Frequency_lists/English
- Open English WordNet (CC BY 4.0) — https://github.com/globalwordnet/english-wordnet

**Calibration literature**
- Nation, I.S.P. (2006). *How large a vocabulary is needed for reading and listening?* — https://www.lextutor.ca/cover/papers/nation_2006.pdf
- Milton, J. & Alexiou, T. (2009). *The development of vocabulary breadth across the CEFR levels.* EUROSLA Monograph Series 1 — https://www.eurosla.org/monographs/EM01/211-232Milton.pdf
- Wolfer, S. & Lew, R. (2025). *Supplementing CEFR-graded vocabulary lists …* Humanities and Social Sciences Communications 12:1151 (open access, CC BY 4.0) — https://www.nature.com/articles/s41599-025-05446-y
- Kikuchi, M. et al. (2026). *CEFR-Annotated WordNet* (arXiv:2510.18466) — https://arxiv.org/html/2510.18466v2
- GerVLPro (LREC 2026) — frequency-share level assignment method — https://aclanthology.org/2026.lrec-1.935.pdf
- Brysbaert, M. & New, B. (2009). *Moving beyond Kučera and Francis … (SUBTLEX)* Behav. Res. Methods 41(4). — https://link.springer.com/article/10.3758/BRM.41.4.977

**Repo companions**
- `docs/research/open-dictionaries.md` (Wiktionary/Wiktextract pack; CC BY-SA obligations)
- `docs/adr/0011-agpl3-with-separate-ccbysa-pack.md` (pack is a separate CC BY-SA artifact; AGPL source)
- `docs/adr/0002-no-backend-server.md` (local-first)

---

## Verdict

**(a) Is wordfreq safe to bake into the CC BY-SA 4.0 pack — and with exactly what attribution?**

**Yes.** wordfreq's **data files are CC BY-SA 4.0** and its **code is Apache-2.0**; the data licence is identical to the pack's, so there is no conflict and no new copyleft obligation beyond keeping the pack CC BY-SA 4.0. A derived band is **Adapted Material**, so ShareAlike attaches — satisfied for free. The obligations are: **attribute**, **link the licence**, **indicate the modifications**, and retain the upstream credits. The exact minimum attribution (all traceable to `NOTICE.md`) is:

- **wordfreq**, by **Robyn Speer** (*this exact name* — a renamed credit is a licence violation), <https://github.com/rspeer/wordfreq>, data under **CC BY-SA 4.0** with the licence URL;
- a **modification notice** (frequencies → bands; form frequencies aggregated to lemmas);
- **Google Books Ngram Viewer** + link <https://books.google.com/ngrams>;
- **OpenSubtitles** (<https://www.opensubtitles.org/>);
- the **SUBTLEX** word lists by **Brysbaert et al.**, with the statement that **SUBTLEX is freely available data**.

This must live **inside the pack** (the existing `NOTICE` + `attribution`/`source`/`source_data`/`modifications` keys), not only in the app — wordfreq's README explicitly warns that a stripped export "does not follow the CC-By-SA license". Caveats, not blockers: wordfreq is **frozen at ~2021** and the author states she does not want it used for (or done in a way that could benefit) **generative AI** — Lekto is a reading app, so this is a courtesy note, not a term. Reliance is on wordfreq's **aggregate CC BY-SA 4.0 grant**; the upstreams (SUBTLEX email permission; OpenSubtitles attribution; Google "appreciated") are all satisfied by retaining their credits.

**(b) Is there a cleaner / more permissive source we should prefer?**

**The single cleanest option is to build the band from Google Books Ngrams v3 (CC BY 3.0, attribution only — no ShareAlike), using the pre-cleaned `orgtre/google-books-ngram-frequency` English list, whose own licence is also CC BY 3.0.** It needs no ShareAlike and only a "Google Books Ngram Viewer + link" credit. But it is **books-only and ends in 2019**, i.e. a worse *signal* than wordfreq's 7-source blend; prefer it only if licence minimalism outweighs quality. A reasonable middle path: compute the primary band from **wordfreq** (best quality, CC BY-SA 4.0, already compatible) and treat **Google Ngrams / Leipzig (CC BY)** as cross-checks. **Avoid** the tempting ready-made CEFR lists: **EVP** (proprietary — its Kaggle/GitHub copies are unauthorised) and **EFLLex / Kelly** (**NonCommercial**). The safest *openly-licensed* ready CEFR sources are **CEFR-J v1.6** (A1–B2, bespoke "cite-and-use" permission — usable with credit, but **do not relicense it as CC BY-SA**) plus **Octanove C1/C2 (CC BY-SA 4.0)**.

**(c) Does any candidate force the signal into a separate artifact rather than the pack?**

**For licence reasons, wordfreq (and the CC BY / CC BY-SA frequency sources) do not — they can go straight into the existing CC BY-SA 4.0 pack.** What *does* force separation:

- **NonCommercial sources (EFLLex, Kelly)** cannot be merged into the pack at all: their NC term is incompatible with a pack Lekto may use commercially. They would have to live in a **separate, NC-licensed artifact** (or be excluded) — and even a separate artifact is awkward because the app's use of it may be commercial. **Recommendation: exclude.**
- **Proprietary sources (EVP)** cannot be used in any artifact without a licence from Cambridge; their third-party copies carry no valid grant. **Recommendation: exclude.**
- **CEFR-J v1.6** is a soft case: its bespoke terms permit use-with-citation but grant no explicit relicensing right, so it is cleanest to keep its data in its **own labelled table/file with its own NOTICE** rather than silently merge it into the CC BY-SA pack.

Net: keep the wordfreq-derived band **inside the pack** (with the full attribution block above and the modification notice), record the **calibration** citations in the manifest, and keep the NC/proprietary options out entirely.
