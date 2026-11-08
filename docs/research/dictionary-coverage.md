# The first dictionary roster: the ten most spoken readable languages

**Date:** 2026-11-08
**Issue:** #302 (part of #301)
**Scope:** Choose the first 10 **Reading languages** from measured
coverage, not from impression. The roster is the top-usage ten that can actually
produce a **Dictionary pack**; every pair in `tools/dictionaries/languages.json`
clears ADR-0047's 2,000-lemma floor.
**Method:** Full pass over 21 Kaikki editions and 41 candidate **Translation
languages**. This note keeps the ten reading editions and the ten-language matrix;
the target-only measurements are the next wave.

> **Headline.** **10 reading languages, 78 pairs.** The ten are the most spoken
> languages that can actually be read with Lekto: `en`, `zh`, `es`, `fr`, `pt`,
> `de`, `ja`, `tr`, `ko`, `it`. Ten languages give 90 possible pairs, of which 78
> clear 2,000 lemmas; the 12 below are listed. Hindi, Arabic, Bengali, Urdu,
> Indonesian and Russian cannot be reading languages — no Kaikki edition, or no
> translations in it — but they are measured as translation targets for the next
> wave.

## Why these ten

Ethnologue 2026, ranked by **total speakers** (native + second language). A
language is a reading language only if Kaikki publishes a raw edition *and* that
edition's entries carry translations.

| World rank | Language | Total speakers | Readable? | Pairs in the 10 |
| ---: | --- | ---: | :---: | ---: |
| 1 | `en` | 1.49 B | **yes** | 9 |
| 2 | `zh` | 1.18 B | **yes** | 9 |
| 3 | `hi` | 611 M | no | — |
| 4 | `es` | 561 M | **yes** | 6 |
| 5 | `ar` | 335 M | no | — |
| 6 | `fr` | 334 M | **yes** | 9 |
| 7 | `bn` | 274 M | no | — |
| 8 | `pt` | 269 M | **yes** | 9 |
| 9 | `id` | 255 M | no | — |
| 10 | `ur` | 246 M | no | — |
| 11 | `ru` | 210 M | no | — |
| 12 | `de` | 133 M | **yes** | 9 |
| 13 | `ja` | 126 M | **yes** | 9 |
| 17 | `vi` | 97 M | no | — |
| 21 | `tr` | 94 M | **yes** | 5 |
| 28 | `ko` | 82 M | **yes** | 8 |
| 30 | `th` | 71 M | no | — |
| 31 | `it` | 66 M | **yes** | 5 |

The top ten by usage is `en`, `zh`, `hi`, `es`, `ar`, `fr`, `bn`, `pt`, `id`,
`ur`. Only five of them are readable: `hi`, `ar`, `bn` and `ur` have **no Kaikki
raw edition** (the raw download is a 404), and `id`'s edition yields no translated
pair. The remaining five reading slots therefore go to the next most spoken
readable languages: `de` (12), `ja` (13), `tr` (21), `ko` (28), `it` (31).

`pl` and `nl`, the next readable editions, sit below Italian in total
speakers. So the ten are the ten most spoken **readable** languages, in order.

**Editions that are not reading languages, and why:**

| Edition | Why it is not a reading language |
| --- | --- |
| `ru` | 490,093 Russian entries, but only **3 lines** carry any `translations`; no pair clears the floor. It remains a strong translation target (`zh`→`ru` 159,371). |
| `id` | Best pair is 3 lemmas; no pair clears. Remains a target. |
| `vi` | Best pair is 687 lemmas; no pair clears. Remains a target. |
| `ms` | Best pair is 410 lemmas; no pair clears. |
| `simple` | Entries carry `lang_code: "en"`; no translated pair. An English source, not a language. |
| `th` | Only `en` clears (2,069), and wordfreq has no Thai list, so no **Difficulty table**. Remains a target. |
| `ku` | Pairs clear, but wordfreq has no Kurdish list, so no Difficulty table. |
| `el`, `cs`, `nl`, `pl` | Readable and wordfreq-supported, but ranked below Italian on total speakers. They are the next reading candidates. |

## Method

Kaikki publishes one raw Wiktextract extract per Wiktionary edition at
`https://kaikki.org/<edition>wiktionary/raw-wiktextract-data.jsonl.gz`; the
English edition is the all-languages file at
`https://kaikki.org/dictionary/raw-wiktextract-data.jsonl.gz`. The raw extract is
the input ADR-0044 requires: the post-processed per-language files strip about
94 % of the `translations` rows.

The pass reads each edition **once**. It uses the pack build's own
`ReadingEntries` stream and its normalisation, merges entries on
`(language, lemma, part of speech)`, and counts the lemmas the build would
publish. The count is not a percentage: `it`→`es` at 6,661 lemmas outweighs a
higher-yield small edition.

**Chinese is the one mapping.** English Wiktionary codes Mandarin translations
`cmn`, while every other edition and the app name Chinese `zh`. The build folds
`cmn` into `zh` (`canonicalLanguage`); `en`→`zh` is therefore **33,528** lemmas.
Without the fold it is 1,306 and would be dropped.

### Reproduce

```sh
./gradlew :tools:dictionaries:installDist
tools/dictionaries/build/install/dictionaries/bin/dictionaries \
  --measure-input en=/cache/en.jsonl.gz \
  --measure-input fr=/cache/fr.jsonl.gz \
  --measure-target en --measure-target zh --measure-target es \
  --measure-output coverage.json
```

`--measure-input` is repeated, `<edition>[:<language>]=<path>`; `--measure-target`
names the languages to count; targets default to the input languages. The output
is canonical JSON, one `editions[]` row per input, each with its `pairs[]` in
translation-language order.

## Provenance

Every file below was downloaded whole on 2026-11-08. The SHA-256 is of the gzip
bytes as served; the extraction date is the file's own `Last-Modified`, the rule
`.github/workflows/dictionary-pack.yml` already applies.

| Edition | Gzip bytes | SHA-256 | Extracted |
| --- | ---: | --- | --- |
| `en` | 3,082,476,978 | `5ff2c072f8099f2d2ce484bd6957bf179f046d2ad37bc36555edf8bb84cb5291` | 2026-10-30 |
| `zh` | 240,275,899 | `600f8dd8050369d614cd39c17d12ab24b8ce8b8bf97c7275f424ace0417220ea` | 2026-11-02 |
| `es` | 103,584,114 | `206001cbdfc783cf4076789366e2c0cb27860da739ddfcc0189c4c878b189da1` | 2026-11-02 |
| `fr` | 736,596,796 | `33c7b73bc11d2533c9c0e0be6484ded76bd9ff29337065f2070b4f1b7d2e4f3c` | 2026-11-02 |
| `pt` | 37,273,122 | `0990ec2517efca42cc04c3b854a46f28f3204ab5acd337f641c32c00dd45ddae` | 2026-11-02 |
| `de` | 312,173,123 | `3405adc801ae68406304f9317421d3df6bca74ffcaa041a88afc44c5a5debcbe` | 2026-11-01 |
| `ja` | 65,876,129 | `728782429f82dcb5ed0047672e7ca1ac343c44463a83ce43e45592178a2e7f8d` | 2026-11-02 |
| `tr` | 45,899,849 | `8404795fcb0e7a675e64bdfa4b157a896f537678b9202329cbfa4d811352be65` | 2026-11-03 |
| `ko` | 28,335,452 | `09d515e5f601058d0548c5cc712bf8c20b13ea6d68e5a075476ae43eb24de250` | 2026-11-02 |
| `it` | 44,076,364 | `075c981ca609a66d62471b9484b612b3269843aa6790806acd4bbb7615811d80` | 2026-11-02 |

## The ten-language matrix

Lemmas per pair from each edition's full pass. The diagonal is never a pair; `0`
would mean no translation into that target. The twelve entries below 2,000 are
the only cells that do not become a pack.

| Reading | en | zh | es | fr | pt | de | ja | tr | ko | it |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `en` | — | 33,528 | 45,679 | 51,039 | 40,913 | 45,947 | 26,300 | 20,077 | 18,621 | 39,501 |
| `zh` | 27,060 | — | 7,188 | 8,008 | 7,058 | 7,646 | 7,494 | 4,909 | 6,260 | 6,158 |
| `es` | 20,817 | 1,374 | — | 15,350 | 9,111 | 10,884 | 2,916 | 1,754 | 1,183 | 9,700 |
| `fr` | 85,277 | 7,719 | 38,864 | — | 20,534 | 34,571 | 11,276 | 6,701 | 5,798 | 51,711 |
| `pt` | 19,804 | 3,894 | 15,398 | 14,567 | — | 10,211 | 4,969 | 3,878 | 2,992 | 12,240 |
| `de` | 74,945 | 3,989 | 36,693 | 59,848 | 21,157 | — | 7,640 | 15,282 | 2,988 | 34,356 |
| `ja` | 15,713 | 5,041 | 4,753 | 6,093 | 4,180 | 5,930 | — | 2,586 | 4,559 | 4,439 |
| `tr` | 10,866 | 641 | 3,182 | 5,886 | 1,709 | 6,333 | 867 | — | 626 | 2,914 |
| `ko` | 22,089 | 4,798 | 2,666 | 3,681 | 2,493 | 3,724 | 594 | 2,086 | — | 2,472 |
| `it` | 31,642 | 607 | 6,661 | 7,256 | 3,210 | 7,756 | 848 | 574 | 385 | — |

### The 78 pairs, ordered by lemma count

`Entries with a translation` counts the reading language's entries carrying at
least one translation into the target; `Entries` is the merged
`(language, lemma, part of speech)` count — the pack's `entryCount`.

| Reading → translation | Entries with a translation | Lemmas | Entries |
| --- | ---: | ---: | ---: |
| `fr` → `en` | 91,881 | **85,277** | 89,995 |
| `de` → `en` | 77,152 | **74,945** | 76,343 |
| `de` → `fr` | 61,337 | **59,848** | 60,808 |
| `fr` → `it` | 54,460 | **51,711** | 53,829 |
| `en` → `fr` | 57,203 | **51,039** | 56,481 |
| `en` → `de` | 52,001 | **45,947** | 51,290 |
| `en` → `es` | 51,514 | **45,679** | 50,909 |
| `en` → `pt` | 46,065 | **40,913** | 45,527 |
| `en` → `it` | 43,697 | **39,501** | 43,210 |
| `fr` → `es` | 41,334 | **38,864** | 40,729 |
| `de` → `es` | 37,495 | **36,693** | 37,251 |
| `fr` → `de` | 36,833 | **34,571** | 36,191 |
| `de` → `it` | 35,138 | **34,356** | 34,850 |
| `en` → `zh` | 36,596 | **33,528** | 36,051 |
| `it` → `en` | 41,182 | **31,642** | 40,279 |
| `zh` → `en` | 27,175 | **27,060** | 27,167 |
| `en` → `ja` | 28,748 | **26,300** | 28,441 |
| `ko` → `en` | 22,576 | **22,089** | 22,250 |
| `de` → `pt` | 21,557 | **21,157** | 21,438 |
| `es` → `en` | 23,457 | **20,817** | 22,650 |
| `fr` → `pt` | 21,713 | **20,534** | 21,393 |
| `en` → `tr` | 21,956 | **20,077** | 21,779 |
| `pt` → `en` | 20,640 | **19,804** | 20,525 |
| `en` → `ko` | 19,959 | **18,621** | 19,793 |
| `ja` → `en` | 16,730 | **15,713** | 16,472 |
| `pt` → `es` | 16,090 | **15,398** | 15,992 |
| `es` → `fr` | 17,298 | **15,350** | 16,707 |
| `de` → `tr` | 15,525 | **15,282** | 15,456 |
| `pt` → `fr` | 15,067 | **14,567** | 14,988 |
| `pt` → `it` | 12,661 | **12,240** | 12,598 |
| `fr` → `ja` | 11,875 | **11,276** | 11,666 |
| `es` → `de` | 12,546 | **10,884** | 12,044 |
| `tr` → `en` | 11,331 | **10,866** | 11,323 |
| `pt` → `de` | 10,621 | **10,211** | 10,551 |
| `es` → `it` | 11,085 | **9,700** | 10,680 |
| `es` → `pt` | 10,369 | **9,111** | 10,007 |
| `zh` → `fr` | 8,109 | **8,008** | 8,103 |
| `it` → `de` | 10,072 | **7,756** | 9,754 |
| `fr` → `zh` | 8,080 | **7,719** | 7,985 |
| `zh` → `de` | 7,745 | **7,646** | 7,741 |
| `de` → `ja` | 7,773 | **7,640** | 7,723 |
| `zh` → `ja` | 7,569 | **7,494** | 7,564 |
| `it` → `fr` | 9,418 | **7,256** | 9,070 |
| `zh` → `es` | 7,282 | **7,188** | 7,278 |
| `zh` → `pt` | 7,152 | **7,058** | 7,148 |
| `fr` → `tr` | 6,967 | **6,701** | 6,898 |
| `it` → `es` | 8,794 | **6,661** | 8,507 |
| `tr` → `de` | 6,614 | **6,333** | 6,607 |
| `zh` → `ko` | 6,318 | **6,260** | 6,313 |
| `zh` → `it` | 6,241 | **6,158** | 6,238 |
| `ja` → `fr` | 6,341 | **6,093** | 6,262 |
| `ja` → `de` | 6,144 | **5,930** | 6,077 |
| `tr` → `fr` | 5,982 | **5,886** | 5,980 |
| `fr` → `ko` | 6,083 | **5,798** | 6,022 |
| `ja` → `zh` | 5,148 | **5,041** | 5,108 |
| `pt` → `ja` | 5,030 | **4,969** | 5,001 |
| `zh` → `tr` | 4,962 | **4,909** | 4,959 |
| `ko` → `zh` | 4,846 | **4,798** | 4,807 |
| `ja` → `es` | 4,920 | **4,753** | 4,864 |
| `ja` → `ko` | 4,648 | **4,559** | 4,612 |
| `ja` → `it` | 4,601 | **4,439** | 4,539 |
| `ja` → `pt` | 4,325 | **4,180** | 4,282 |
| `de` → `zh` | 4,027 | **3,989** | 4,013 |
| `pt` → `zh` | 3,948 | **3,894** | 3,925 |
| `pt` → `tr` | 3,946 | **3,878** | 3,923 |
| `ko` → `de` | 3,764 | **3,724** | 3,734 |
| `ko` → `fr` | 3,753 | **3,681** | 3,701 |
| `it` → `pt` | 4,344 | **3,210** | 4,172 |
| `tr` → `es` | 3,220 | **3,182** | 3,219 |
| `pt` → `ko` | 3,023 | **2,992** | 3,011 |
| `de` → `ko` | 3,010 | **2,988** | 3,001 |
| `es` → `ja` | 3,442 | **2,916** | 3,306 |
| `tr` → `it` | 2,952 | **2,914** | 2,951 |
| `ko` → `es` | 2,703 | **2,666** | 2,671 |
| `ja` → `tr` | 2,633 | **2,586** | 2,620 |
| `ko` → `pt` | 2,524 | **2,493** | 2,497 |
| `ko` → `it` | 2,505 | **2,472** | 2,477 |
| `ko` → `tr` | 2,114 | **2,086** | 2,092 |

### The 12 pairs below the floor

Not published, and not exceptions. The closest is `es`→`tr` at 1,754.

| Reading → translation | Lemmas |
| --- | ---: |
| `es` → `tr` | 1,754 |
| `tr` → `pt` | 1,709 |
| `es` → `zh` | 1,374 |
| `es` → `ko` | 1,183 |
| `tr` → `ja` | 867 |
| `it` → `ja` | 848 |
| `tr` → `zh` | 641 |
| `tr` → `ko` | 626 |
| `it` → `zh` | 607 |
| `ko` → `ja` | 594 |
| `it` → `tr` | 574 |
| `it` → `ko` | 385 |

## The next wave: target-only languages

These languages are among the most spoken in the world but cannot be reading
languages. They can still be **Translation languages**, because a target needs no
edition. They are *not* in `languages.json` yet; the table is the measured case
for adding them.

| Target | World rank | Readings covered | Packs added | Best pairs |
| --- | ---: | ---: | ---: | --- |
| `hi` | 3 | 2 / 10 | +2 | en:15,386, zh:3,684 |
| `ar` | 5 | 7 / 10 | +7 | en:17,801, fr:13,036, zh:4,929 |
| `bn` | 7 | 2 / 10 | +2 | en:6,077, zh:2,338 |
| `id` | 9 | 5 / 10 | +5 | en:11,918, fr:5,317, zh:3,247 |
| `ur` | 10 | 2 / 10 | +2 | en:6,457, zh:2,218 |
| `ru` | 11 | 9 / 10 | +9 | zh:159,371, en:42,995, de:27,671 |
| `vi` | 17 | 5 / 10 | +5 | en:11,963, zh:4,559, fr:3,343 |
| `fa` | 27 | 5 / 10 | +5 | en:10,868, de:3,815, zh:3,768 |
| `th` | 30 | 3 / 10 | +3 | en:10,154, zh:4,032, ja:2,258 |

Adding `ru`, `ar` and `hi` — the three largest absent languages by usage — takes
the roster from 78 to **96 packs**. Adding all nine takes it to **118**. The
first release stays at 78.

## The first roster

`tools/dictionaries/languages.json` lists the 78 pairs and the ten Difficulties:
`en`, `zh`, `es`, `fr`, `pt`, `de`, `ja`, `tr`, `ko`, `it`. Every listed pair
clears 2,000 lemmas; no exception is taken.

> **Caveat — the live workflow still streams one file.** `dictionary-pack.yml`
> passes the all-languages file and cannot construct this roster; #303 and #304
> replace its input model with one edition per reading language. Until they land,
> the roster is the target, not something the current job can publish.
