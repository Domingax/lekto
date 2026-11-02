---
version: 1
slug: "app-src-commonmain-kotlin-app-lekto-guide"
primary_target: "app/src/commonMain/kotlin/app/lekto/guide"
related_targets: []
---

# First-run guide

Mode: Operate. The guide is a setup task; the tutorial book it opens is Read.

## Audience and job

A first launch on Android or desktop, by a self-directed learner who has installed an app whose
purpose they half-know, may be offline, and has about twelve seconds of patience. Three things
must land, in this order: the words of your own book are coloured by what you know; tapping one
saves it; tell Lekto your level and it will guess the rest.

Success: the reader opens a real book and its colours already mean something, knows that a tap
saves, and knows where the guide lives afterwards.

## Locked structure

Three front-matter pages ahead of the shell, then the Library with the tutorial book as its
first row.

1. **What Lekto does** — a serif headline; four words from the tutorial book, set in the reading
   serif and coloured by the real ramp; the sentence that names the mechanism.
2. **The tap** — the lookup footer's anatomy: a word, five levels, one sentence saying the level
   is the save.
3. **The pact** — the tutorial book's Reading language, its Reader level, and the two Derived
   assets' downloads; the ramp above recolours in place as the level moves.

Then the Library, with the tutorial book first. `Skip` sits on every page in the muted ink; Guide
becomes a row in Settings ▸ Application afterwards.

## Constraints and anti-goals

- Offline: everything but the downloads works with no network. A download can be deferred with
  `Later`, and the guide never blocks on one.
- Never a trap: `Skip` on every page, and the guide is reopenable from Settings after it ends.
- The tutorial book is a real Book: listed, readable, deletable like any other. It is a local
  Derived asset — never in the Vault export, never synced (ADR-0005).
- Identity only: paper, ink, hairlines, the reading serif for titles, the mastery ramp. No new
  colour, no new type role, no illustration, no accent.
- 360 dp phone first; the pages must hold at 320 dp and in Sepia.
- Copy in English and French in the same pull request; `docs/design.md` §6–§7 and
  `guide/getting-started.md` updated with it.

Anti-goals: a carousel with decorative dots, a coach-mark overlay on the Library, a modal that
cannot be dismissed, a welcome illustration, confetti, a setup percentage, a separate account or
tour.

Memorable moment: page 3 — the reader picks a level and the four coloured words above recolour in
place, so the setting's effect is visible before any book is opened.

## Direction contract

THESIS: The first launch is a book's front matter — three liminary pages that make the colour
system mean something before a single page of reading — refusing both the coach-mark overlay and
the empty shelf that explains nothing.

OWN-WORLD: Warm paper, ink, hairline rules, the reading serif for titles, the mastery ramp as the
only saturated colour. No illustration, no accent, no chrome beyond a page counter and two text
actions.

STORY: "This app colours the words of my own book by what I know; tapping one saves it; if I tell
it my level, it guesses the rest." Then the Library, holding a short book that proves it.

FIRST VIEWPORT: Page 1 of 3 at 360 × 640. Serif headline in the top third; four coloured words
from the tutorial book in the reading serif below it; the sentence naming the mechanism; `Skip`
top-right in muted ink; `1 · 2 · 3` and a chevron at the bottom. No image, no card, no filled
button until page 3, where the pact's one action is filled.

FORM: the roll's dealt lead (the setup pact) fused into three front-matter pages.
seed:bfff6da0c56e2b47

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the
verdict, DESIGN.md, and every shipping raster carrying its provenance.

## Open decisions

- The exact tutorial text and its word list (a few short paragraphs in English, one Reading
  language, words chosen so all four highlighted levels appear on page 1).
- Whether the Library shows the tutorial book before the guide ends: no — it appears after, as the
  guide's outcome.
- The Guide row's label: `Guide` in English, `Guide` in French.
