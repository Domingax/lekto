# Lekto design language

How Lekto looks, and the rules that keep it looking like itself. It is written for whoever
changes a screen next — a person or an agent — and it is the reference the code answers to.

The mockups this document records are on the `prototype/visual-identity` branch, under
`docs/prototype/visual-identity/`; each image answers one question and is named after it. They
are throwaway; the decisions are here.

## 1. The identity in one line

**Ink on paper, and the only saturated colours are the ones that mean something.**

Three consequences, and they settle most questions on their own:

- The app has **no accent colour**. A filled button is ink on paper — near-black on warm white.
  Anything that would have been the "brand colour" is instead the ink itself.
- The **Mastery level** ramps are the only decorative colour in the product, and they are not
  decorative: they carry the reader's progress.
- Two exceptions, and only two: the **language hues** on the Library's vignettes, and the **red**
  reserved for irreversible actions and errors. Both are explained in §2.

## 2. Colour

### 2.1 The three themes

An **App theme** is one of three (ADR-0036). Each is a paper and an ink:

| Theme | Paper (background) | Surface (cards, sheets, panels) | Ink | Muted |
| ----- | ------------------ | ------------------------------- | --- | ----- |
| Light — paper | `#FAF7F2` | `#F3EEE6` | `#1C1A17` | `#6B6357` |
| Dark — warm night | `#15130F` | `#1F1C17` | `#E8E2D8` | `#9A9083` |
| Sepia — rag paper | `#F6ECD8` | `#EFE3C9` | `#3B2F1E` | `#6B5B41` |

The separator between surfaces is `#D6CCBC` on Light, `#3A342B` on Dark and `#D8C7A6` on Sepia.

Ink on its own paper measures 16.2:1 (Light), 14.4:1 (Dark) and 11.1:1 (Sepia) — far above AA,
which is what makes the text calm: there is no contrast to fight.

**System, Light, Dark, Sepia** is what the reader is offered. System resolves to Light or Dark
and never to Sepia, because no operating system has a sepia state.

### 2.2 The mastery ramps

One ramp per theme, because a single table cannot serve both backgrounds (§ADR-0036). The hues
keep their meanings; their darkness is chosen per paper.

| Level | Meaning | Light & Sepia | Dark | Minimum |
| ----- | ------- | ------------- | ---- | ------- |
| 0 | unknown | `#1D4ED8` | `#8AB4F8` | 5.7:1 / 8.8:1 |
| 1 | recognized | `#A16207` | `#F2C14E` | 4.6:1 / 11.1:1 |
| 2 | familiar | `#C2410C` | `#EE9A5F` | 4.8:1 / 8.3:1 |
| 3 | learned | `#15803D` | `#7BD88F` | 4.7:1 / 10.7:1 |
| 4 | known | — | — | normal text |

Sepia needs three hues very slightly darker than Light's: `#92400E` (recognized), `#9A3412`
(familiar), `#166534` (learned), each at or above 6:1 on `#F6ECD8`.

**The rule that is not negotiable**: a coloured word is 18 sp of running text, so its bar is
**4.5:1 against its own paper**, measured, not estimated. The palette as delivered fails it — the
yellow was 1.5:1, the orange and green 2.3:1 — and that is a defect, not a style. Level 4 carries
no colour because "known" is normal text, and the absence is the reward.

Colouring is applied to the **letters**, with the **single underline** the reader already renders
for every highlighted level. The underline is what carries "this is a word you are learning" to a
reader who cannot read the hue; a different shape per level would need a custom `TextDecoration`
renderer Compose does not offer. A **background wash** behind the word was prototyped
(`traitements.png`) and rejected: it turns a page into an annotated workbook after three hundred
pages. It remains the documented fallback if the four levels ever prove too close in hue — both
ramps live in one file, so switching is a local change.

### 2.3 The selected word

The word the reader just touched carries a wash of the theme's **ink at 14 %**, not a colour of
its own: nothing new enters the screen, and the level's colour still reads through. 24 % is the
ceiling at which the level stops reading through.

### 2.4 The two exceptions

- **Language hues** — the Library's vignette is filled with a muted, earthy hue standing for the
  book's **Reading language**: `#4C6B8A` (en), `#8A5A44` (fr), `#9A7B33` (es), `#5F7A5A` (de),
  `#7A5F7A` (it). On Dark they are lightened by a third. They are **decoration**: the two-letter
  code printed inside the vignette is the signal, so a reader who cannot tell the hues apart
  loses nothing.
- **Red** — `#B3261E` on Light and Sepia, lightened to `#F2B8B5` on Dark, and only for
  irreversible actions (delete an entry, disconnect a server, remove a key) and for failures. It
  never appears as decoration, and never as a warning about something reversible: that is what
  muted ink and clear words are for.

## 3. Typography

Two voices, and they never swap roles.

- **Reading** — the book's text. The platform's serif by default, or its sans, chosen by the
  reader, at a size, leading and margin the reader controls. This is a **Reading preference**
  (ADR-0035) and the app never overrides it.
- **Interface** — everything the app says. The platform's sans-serif. No bundled font: a shipped
  face is a new artefact with its own licence notice, and it buys what the platform already gives.

One deliberate borrowing: a **screen title** is set in the reading serif — "Library",
"Vocabulary", "Settings", and the word or phrase at the top of the lookup panel. The app's own headings
sound like a book's headings. Every other piece of interface text is sans.

The scale actually used, and nothing else:

| Role | Face | Size | Weight |
| ---- | ---- | ---- | ------ |
| Screen title | serif | 26 sp | bold |
| Section or panel title | serif | 19–24 sp | bold |
| A vocabulary word | serif | 18 sp | bold |
| Row title | sans | 15–16 sp | medium |
| Body, panels | sans | 13–15 sp | normal |
| Value under a row title | sans | 12 sp | normal |
| Field label, group label | sans | 11 sp | normal, muted |
| Navigation label | sans | 11 sp | medium when current |

## 4. Shape, elevation and spacing

- **Radii**: 8 dp for controls (buttons, chips, fields, vignettes), 12 dp for surfaces (cards,
  panels, sections), 16 dp for anything that is a dialogue, and the app mark's tile at 22 % of
  its size. A panel anchored to a screen edge is square: it joins the edge rather than floating
  above it, and the lookup sheet is square like the **Aa** panel (§7.3).
- **No shadows.** A card is separated by a surface tone and a hairline border; a panel by a
  surface tone and a 1 dp rule. Material's elevation, and the tonal lift that comes with it, is
  what makes an app look like a template.
- **Spacing** is 4 dp based: 4 inside a label pair, 6–8 between related lines, 12–14 between rows,
  16–20 inside a panel, 20–24 at a screen's edges, 24–48 between a screen's blocks.
- **Touch targets** are at least 48 × 48 dp on Android, which is why a list row is never shorter
  than 48 dp even when its content is two lines of small text.

## 5. Icons

- The set is **Lucide geometry**: a 24-unit grid, a 2-unit stroke, round caps and joins. It is
  generated once, committed as `ImageVector` Kotlin, and carries no runtime dependency — the
  reasoning and the alternatives are in `docs/research/icones-compose.md`.
- **The set is fourteen icons**: `book-open`, `bookmark`, `settings`, `search`, `import`,
  `arrow-left`, `volume-2`, `a-large-small`, `x`, `trash`, `chevron-left`, `chevron-right`,
  `info`, `check`.
  `tools/icons/fetch.sh` fetches them at the pinned Lucide tag and regenerates the Kotlin; the
  generated files are never edited by hand. Their ISC and Feather/MIT notices live in
  `THIRD-PARTY-NOTICES.md` at the root, and the app names them in **Attribution**, reached from
  Languages — the same screen that carries the mark's font licence.
- **A navigation icon must be recognisable at 24 dp**, which is not the same as being handsome in
  a catalogue. `library` reads as a barcode and is not used; `book-open` is. `notebook-pen` reads
  as a scribble; `bookmark` is. An icon that only works large belongs in a sheet, not in the bar.
- `arrow-left` must mirror in right-to-left layouts; the generator must set `autoMirror` and the
  regeneration script must keep it.
- **The launcher mark is the letter** (issue #177): the `L` of the reading serif, and it is the
  whole mark. There is no page beside it and no tile behind it — the ground is the theme's paper —
  so the icon, the launch screen and the guide's tab all show the same drawing.
- **The letter carries the four Mastery level colours that have one**, and the letter's ink for
  level 4. "Known" is the absence of colour (§2.2), so the mark shows what is still being learned
  and never invents a fifth hue: the cap is level 0, the shaft carries levels 1 to 3, and the foot
  is the letter's ink. The three shaft bands cover **the same area of the shaft**, measured on the
  outline rather than estimated — which is why the thirds differ by a few tenths, the shaft is not a
  rectangle. The colour stops at the foot's shoulder, `y = 63.38`: no cut crosses the arm, because a
  boundary there would leave a stray wedge of one colour at its end.
- **The letter wears a rim of its own paper.** The same outline, stroked 2 units and drawn under the
  glyph, so 1 unit shows outside. On the Light paper — the launcher's ground — the rim is the paper
  itself and draws nothing. On the Dark paper it is the letter's edge: without it the foot, which is
  the letter's ink, would be ink on ink at 1.07:1. That is what lets the launch screen keep the
  foreground alone (§7.7).
- **The mark is fixed, not themed.** A launcher knows nothing about the App theme, so the letter
  sits on the Light paper with the Light ramp (ADR-0036). This is the one place where the
  **Mastery level** colours are decoration rather than meaning, and they are what makes the mark
  Lekto's rather than any app's letter.
- **Below 32 px the letter is one ink.** Four bands in a 16 px taskbar frame are a smear; the colour
  is the identity where the size can carry it, and the letter alone where it cannot.
- **The letter is an outline, not a font.** It is the `L` of **Noto Serif Bold 2.015** — the platform
  serif Android renders for `FontFamily.Serif`, so the "reading serif" is what the mark carries —
  taken once from the glyph and mapped to a 50-unit cap height, its corners inside the 66 dp safe
  circle. A rendered `Text` would follow the machine's fonts and could not be pinned by a golden; an
  outline can. The licence is SIL OFL 1.1, with its notice in `THIRD-PARTY-NOTICES.md`.
- **One source, generated outputs.** `tools/icons/mark/` holds two SVGs — `mark.svg`, the letter with
  its paper rim and its four bands, and `letter.svg`, the same letter in one ink. `tools/icons/mark.sh`
  writes the adaptive icon's layers, the launch mark (the foreground itself) and the guide's favicon,
  and `:app:renderAppIcon` renders the desktop PNG, ICO and ICNS from them. Everything is committed,
  so a change to the mark is a reviewable diff and no build step depends on the script having run. A
  generated file is never edited by hand.

## 6. Shell and navigation

Three sections, and no fourth:

| Section | What it is for | Icon |
| ------- | -------------- | ---- |
| **Library** | the books, progress, search, filter, import | `book-open` |
| **Vocabulary** | the words the reader saved | `bookmark` |
| **Settings** | everything else: appearance, languages, provider, vault | `settings` |

- **Import is not a section.** It is the Library's action, written as a word beside the title, and
  as a filled button in the empty state. A tab that opens a file picker is not a place.
- **The reader leaves the shell** and is full-screen. Its chrome is two bands, 48 dp and 64 dp,
  and a tap on the middle third hides both.
- **Languages and Appearance are entries inside Settings**, not sections (ADR-0034, ADR-0036).
- **The back stack is real**, and Android's back button walks it (issue #185). Opening a section or
  a screen pushes it, and Back drops it: back from Settings returns to the section it came from, and
  a bar item already behind the current place unwinds to it rather than stacking a second copy.
  Tapping the section the reader is already in — marked current while one of its own screens is open
  — returns to that section's first screen. Back from the Library is the platform's, and leaves the
  app. A screen with something open over it — the reader's lookup and **Aa** panels — closes that
  first, as the chapter list does on Back before the shell sees it (ADR-0037). The reader's settings
  link leaves the book rather than keeping it behind, so Back from Settings lands on the shelf: a
  book kept behind the link would reopen where it was opened, not where the reader had got to.
- **Desktop**: the same three sections become a fixed left column. In the reader, the right side is
  a **context column** — the chapter list when nothing is touched, the word when something is. It
  is reserved rather than overlaid, so the page never moves. It is about **320 dp** wide, a surface
  parted from the page by a 1 dp rule; a book with no chapter list says so in it rather than
  leaving it blank. Below roughly **1100 dp** the column has no room and the reader falls back to
  the phone's bottom sheet.

## 7. Screen anatomy

Each screen below is one row of what it shows, and one note on what it must not do.

### 7.1 Library

`Library` (serif) · `Import` (word) · `Search` · filter chips · rows · bottom navigation.

A row is a **language vignette** (44 × 60), the title, a line of `FORMAT · 28 %`, and a **3 dp
progress rule**. Rows are parted by **rhythm alone** — no rule between two books: the vignettes are
the shelf's columns, and a divider saws through them. There are **no covers**: a `Book` carries a title, a language, a format and a
file name, and only the EPUB format has a cover image at all — a shelf with covers for some books
and none for others reads as broken. The vignette is where a cover would go if one ever exists.
The file name is not on the row: it survives in import errors and the delete confirmation, where
it identifies a file rather than a book.

A book is deleted **from its row**, and never from a red word that sits on every book. On a touch
screen a swipe from the trailing edge uncovers `Delete`; with a mouse, the row's wash and a trash
appear when it is hovered, and a drag does nothing — the gesture is the thumb's idiom, not the
pointer's. Neither path deletes anything by itself: both open the confirmation (§8), because there
is no undo. The row exposes the same action to a screen reader as an accessibility action.

**Reading progress** is where the reader is now, not the furthest point reached: the share of the
book's rendered text before the **Reading position**, rounded down, so a book on its last page
reads just under 100 %. The number and the rule carry the same whole percentage. An **Unread**
book — no Reading position, and not **Finished** — reads `FORMAT · Not started`, with no rule; an
**In progress** book whose text has not been measured yet reads `FORMAT · …`, also without a rule.
A **Finished** book reads `FORMAT · Finished` with a check and carries **no progress rule**: the
declaration is not a percentage, and a book read to the end may have been reread or skipped
(ADR-0038).

The `Search` field sits under the title, always visible, and hides while the vault holds no book.
It matches the title alone — a case- and accent-insensitive substring of the trimmed query —
because a book found by a string its row does not show, its file name or its language, reads as a
mistake (#173). An empty query leaves the list exactly as it is. A non-empty query flattens it: no
`Continue reading` heading, every match by title. The query is **view state** too: it is cleared on
leaving the Library, survives backgrounding, and never reaches the **Vault**.

Under `Search` sits one row of filter chips once the vault has a book: `Unread`, `In progress`,
`Finished`. They are **multi-select**, and the Library opens with **Unread and In progress**
selected: the shelf shows what is still to read, not the books already finished. Tapping
`Finished` adds finished books; all three selected is the whole library. The last selected chip
cannot be turned off. The row scrolls horizontally when a translation overflows it, and it carries
no counts. The filter is **view state**: it survives leaving the Library and coming back, and
resets to Unread and In progress when the app restarts. It is never written to the **Vault**.

The three most recently read unfinished books lead under the `Continue reading` heading, most
recent first; then, under `Other books`, every other listed book by title. Both headings are the
serif section title, and the gap between the groups is wider than between two rows, so the shelf
says where "read recently" ends. A shelf with no reading history is one list by title, and carries
neither heading. A finished book is never in the leading group,
even if it was the last book read, because that group is for books the reader can continue
(ADR-0038). The leading group is hidden when it is empty. A query narrows the shelf in one more
step, not instead of the filter: the two apply together, so a book the filter hides stays hidden by
a query that matches it, and `Show all books` clears both (#173, #200).

**States**: an empty vault says what to do and carries the action. A Library whose filter matches
nothing says why — "All your books are finished." with `Show all books`, or "No books in this
state." with the same action — and a search with no match names the query back and offers to clear
it or show all books. Loading shows **skeleton rows**, never the empty message; a failed import is
a bordered block in the list, with a way to dismiss it.

### 7.2 Reader

`arrow-left` · book title · `Aa` | the page | `‹` · position · `›`.

The chevrons mark the three tap zones (previous third, chrome third, next third) and turn the
page when tapped; a chevron disabled at a bound stays visible but faded. The position reads the current chapter's
title above the page — `XI` over `Page 285 of 405` — so it uses the same words as the chapter
list (ADR-0039). The two lines are centred in the band; the title takes one line and ellipsises.
Before the first chapter, and in a book with one chapter, the page line stands alone and no
chapter is current.

Tapping the position opens the chapter list, which is why the position is a control rather than a
label. A book with one chapter has no list, so its position stays a label (ADR-0037).

The chapter list is a bottom sheet over the page on a phone, up to about 60 % of the height, and
the reserved context column on desktop (§6). It keeps the book's hierarchy, highlights the current
chapter and scrolls it into view. It closes on a choice, a tap outside, its Close action, or Back.
The lookup panel and the chapter list are never open together. In the column the list is always
there, so the position is a label — except while a word or phrase is shown, when tapping it returns
the column to the list rather than making the reader close the word first. A column choice jumps
exactly as a sheet choice does; a book with no list reads `This book has no chapter list.` there.

Chapters start on a new page. A jump moves the **Reading position** to the chapter's first block
at once, and the page number and total update as pagination counts them.

The **`Aa` panel** sits under the bottom band, in the column: opening it lifts the band and
clips the visible part of the page, so the text is never covered. The page keeps its
pagination — the page number and the page count do not move — and is whole again as soon as the
panel closes. It is a square panel of printed-form rows (`Text face`, `Text size`, `Margins`:
`Serif`/`Sans-serif`, `Narrow`/`Comfortable`/`Wide`) whose values are chips. An unselected chip carries the paper behind it, so it reads as a field on the panel
rather than a hole in it. The panel sets the face, the size and the margins, and previews live:
there is no button to confirm a reading preference. The App theme is **not** here — it recolours
the app, not the book, and it lives in Settings ▸ Appearance (ADR-0035, ADR-0036).

**The end of the book** is a full-screen reader state, not a page. Tapping the next zone on the
last page raises it; reaching the last page does not. Reaching the panel never declares the book
finished by itself. `THE END` is set in the interface's small caps, the book's title in the
reading serif, then `Mark as finished` as the filled action and `Back to Library` as the text
action. Declaring finished keeps the panel open: the check and the word `Finished` appear, the
primary action becomes `Back to Library`, and `Mark as unfinished` takes the text action. Marking
never moves the reading position. The state hides the reader's chrome; system back closes it and
returns to the last page, and `Back to Library` leaves for the Library. Reopening a finished book
opens at the saved position, not on this state.

### 7.3 The lookup panel

Raised from the bottom over the reader, never above ~45 % of the height, the page still visible
and the touched word still washed above it. The page **lifts** under the sheet by just enough to
clear the word: it is not repaginated, so the page number and the page count do not move, and the
top of the page is clipped for as long as the sheet is open — a transient state, not a new page.
A panel, not an outlined card: square where it joins the bottom edge like the **Aa** panel, the
surface tone and a hairline inset to the content, and a drag handle marks where it was raised
from. On desktop it is the context column instead, and closing the word returns the column to the
chapter list. There the level's name stands above its chips rather than beside them: five chips
leave no room for a name in the column.

A **word**: the word in the serif, the **Lemma** it resolved to when the touched form is an
inflection, its parts of speech and pronunciation, the speaker and the close action; then the
senses with their translation; then the level; then the external references as a muted line. The
header and the senses share one scroll, so a short screen never pushes the pinned footer past the
sheet's edge.

**There is no Save button.** The level (`Mastery`) *is* the save: one tap on a level chip stores the word at
that level, which is one gesture for one intention. Every chip is filled with its level's colour and
carries its number; the current one adds an ink ring, and its **name** stands beside the row, so the
numbers never need a legend.

A **phrase** is the word panel's sibling on the same shell (issue #211): the phrase in the serif with
the close icon, then the answer in the body size. **With a provider**, the translation stands first
and the disclosure beneath it — `What's sent?` — names the provider and says the selection and its
sentence leave the device. **Without a provider**, the sheet says that a phrase needs an AI while a
word has its offline dictionary, offers the settings link, and keeps the **Translation shortcut**,
which needs nothing configured. The body scrolls under the sheet's cap, so a long phrase or a long
translation never clips or pushes the shortcut past the edge. A failure is a sentence in the panel;
the reading session is never interrupted.

There are **no dictionary tabs**. The app has one offline dictionary and four external references
that leave the app; presenting them as tabs would promise a symmetry that does not exist.

**States**: a missing dictionary still allows the save, and says so
(`The offline dictionary isn't installed yet.`); a phrase with no provider
explains that a phrase needs an AI while a word does not, and offers the settings link; a failure
is a sentence inside the panel, never a dialogue over the reader.

### 7.4 Vocabulary

`Vocabulary` (serif) · `128 words` · `Search` · level filters · rows · bottom navigation.

The **count** sits beside the title in the muted ink and counts the vocabulary, not the rows a
search or a level narrowed the list to.

A row is the **word in the serif** — it is the word as the book printed it — then its colour-dotted
level and name, its translation, and the sentence it came from in the muted ink; a hairline parts
two rows. The row's one tap is reserved for the word panel (#178).

**Delete never sits on the row as a red word.** A row deletes the way a Library row does (§7.1): a
swipe from the trailing edge on a touch screen, and a muted-ink trash on the row's first line when a
mouse hovers it. Neither path deletes anything: both open the one confirmation, and red belongs to
the act, not to the row — the plate a swipe uncovers and the dialogue's confirm (§2.4). A list with
no undo does not get to delete on a mis-tap (§8).

**Empty and no-results are different states.** One says how words arrive (`No words saved yet` and
`Tap a word while reading, and it lands here.`); the other names the search (`No words match
"princesse".`), says what it covered and offers `Clear search`.

The filters are the **Aa** panel's own chips (§7.2) — `All levels`, then the five levels by name,
each carrying its level's dot, and the current filter is the theme's ink behind its name. On a touch
screen the row is one line that scrolls, and a partly cut chip is what says so. With a pointer the
chips wrap instead: a mouse cannot drag a row of buttons, and a small chip set reads better whole
than behind a scrollbar.

### 7.5 Settings

An **index**, not a form: a row per area, its name in the title style and its current value under
it in the muted ink — `Appearance · Sepia`, `App language · French`, `Reading languages · English,
Spanish`, `LLM provider · Anthropic, configured`, `Vault and sync · Synced 2 minutes ago`, then
`Attribution` and `About · version`. A `chevron-right` closes each row, and a hairline parts two
rows of one group, never one after the last.

The rows are grouped in the sentence-case labels *Reading*, *Data*, *Application*, in the small
muted style. **Reading** holds what the reader reads with, **Data** what leaves the device, and
**Application** the licences and the build itself.

The rows carry no control: a value that needs changing is a screen away, which is the whole point
of an index. A row whose screen is not wired in a build says `Not available` rather than naming a
value it does not have, and a row whose state is empty says so — `Reading languages · None yet`
before the first book.

The provider's API key and the WebDAV server are **screens of their own**, not two forms buried
mid-page. Their fields read like a printed form — the label in small muted type, the value under
it, a hairline — rather than a wall of outlined boxes.

The rules for a form: **one filled button per screen** — `Connection` on the provider, `Sync now`
on the vault; everything reversible applies on change, with no confirmation, and something that
costs a network call or writes a secret gets an explicit action; anything irreversible is written
in red. `Connection` is the explicit action for a secret: it tests first and writes only when the
endpoint answered.

### 7.6 Appearance, App language, Languages, LLM provider, Vault and sync, About

- **Appearance** — the four theme entries, each *shown on its own paper*: a 48 × 34 preview with two
  lines of ink, System split down the middle. A theme is chosen by looking at it, and the current one
  carries a check rather than a word.
- **App language** — the languages the app ships, by name, the current one checked; the choice
  re-renders the whole interface at once.
- **Languages** — one row per **Reading language**, its state (word-difficulty data, offline
  dictionary) and the download action when one is missing.
- **LLM provider** — provider, key, model. The key is masked with a reveal, and the screen says
  where it is stored: the device's keychain, never the vault. `Connection` tests it and stores it
  only when the provider answered; a stored key shows masked dots rather than an empty field.
- **Vault and sync** — export and import; then the server, the user, the application
  password, the last run ("Synced 2 minutes ago.") and the actions. `Connection` tests the
  endpoint, stores the password and turns sync on; `Sync now` runs one. The folder-sync
  caution lives behind the `info` icon beside **Server address**, not as a paragraph in the
  flow. The vault is **app-private** (ADR-0010): there is no folder to choose, and a
  "change location" control would describe an app that does not exist.
- **About** — the version an installed copy reports, the licence it is released under and the
  way to its source. The index's row carries the version, so the reader can read it without
  opening the screen.

### 7.7 Launch

Android 12 and later paints a launch screen before the app runs: the system builds a **starting
window** from the theme the manifest declares, draws the app's icon in it, and only then lets the
first frame through. There is no launch screen to design, only a paper and a mark to get right.

The paper is the **platform's**, not the chosen one. The starting window is built before the process
starts, so it can read the device's light-or-dark setting (`values-v31`, `values-night-v31`) and
nothing else: an app set to Sepia on a light device launches on the Light paper, and Sepia never
appears at launch. From the first frame on, the window and every screen follow the **App theme**
(ADR-0036). That is the honest limit of a window the system owns.

The mark in that window is **the launcher's own layer**. The system masks the outer third of the
canvas on both surfaces, so the launch screen and the icon are the same drawing at the same
proportions — there is nothing to keep in step. The layer's paper rim (§5) is what carries the mark
off the Light paper: on the Dark paper the foot is ink on ink, and the rim is the edge that separates
them. A dark theme that flashes white has a bug, not a style.

The first frame is the shell with the Library's rows **in skeleton**, never a bare logo: the wait
is a local vault read, and a screen shown for 40 ms is a flicker, which reads as slowness. No
animation, no tagline, no artificial delay.

## 8. States and feedback

| Situation | What the reader sees |
| --------- | -------------------- |
| Saving a word | the word's colour changes in the page. No toast |
| Finishing a book | the end panel confirms it. No toast |
| Importing | an inline progress bar in the Library |
| Loading a list | skeleton rows, never the empty message |
| Empty | a sentence saying what to do, and the action |
| All books finished | "All your books are finished." and a way to show them |
| No books in this state | "No books in this state." and a way to show all books |
| No search results | the query named back, with `Clear search` and `Show all books` |
| Offline dictionary | a sentence inside the lookup panel |
| Failure | a block where the action was, with a way to dismiss it |
| Deleting anything | a confirmation, because there is no undo |

Every clickable surface shows the pointing hand on desktop; a disabled control keeps the
arrow. A list row also carries a wash of the theme's ink at **5 %** while the pointer rests on it —
the surface under the mouse is the one it is about — and its row action appears there (§7.1).
The wash is a **rounded band inset from the row's edges** — 8 dp corners, reaching 10 dp past the
content — so it reads as the target under the pointer rather than a highlighted table cell, and the
text keeps air on every side. Nothing moves and nothing else changes: hover is a hint, not an
animation. `HandCursor.kt` (§10) is the one modifier that asks for the hand.

Nothing blocks the reading: an error never becomes a full-screen alert, and no state ever hides
the page behind it.

## 9. Accessibility

- **4.5:1** for any coloured word (§2.2), measured per theme.
- A **non-colour indicator** for every highlighted level: the underline.
- **48 × 48 dp** minimum for anything tappable. A filter chip's visible shape may be shorter, but
  its touch target is not, and its selected state is exposed to accessibility.
- The reader's page also carries the two page turns as accessibility actions, beside the two
  chevrons: a screen-reader or switch user keeps a way through the book.
- The language vignette's colour is decoration; its two letters are the information.
- Every icon either carries a `contentDescription` or sits beside a label that names it.
- The reading text honours the platform's font scale, and the **Reading preference**'s size stays
  in `sp` so the two multiply.
- On desktop, the page is **never covered**: the context column is reserved, so opening the panel
  does not reflow the text and does not move the reader's eye.

## 10. Where this lives in code

- Tokens and `LektoTheme` — `app/src/commonMain/kotlin/app/lekto/theme/`: the three
  `ColorScheme`s, `LektoTypography`, `LektoRadii`, `LektoShapes` and `LektoSpacing`, and the
  composable every screen is wrapped in.
- The mastery ramps — one file, three ramps (§2.2).
- The language hues — `theme/LanguageHues.kt`: one hue per **Reading language**,
  lifted by a third on night's paper, never information on their own (§2.4).
- The generated icons — `app/src/commonMain/kotlin/app/lekto/ui/icons/`, regenerated by
  `tools/icons/fetch.sh`, which pins the Lucide tag and the generator; `THIRD-PARTY-NOTICES.md`
  carries the licences.
- The launcher mark — `tools/icons/mark/mark.svg` (the letter, its paper rim and its four bands) and
  `letter.svg` (the same letter in one ink) are the source. `tools/icons/mark.sh` writes the adaptive
  icon's layers, the launch mark and the guide's favicon; `app.lekto.tools.RenderAppIcon`
  (`./gradlew :app:renderAppIcon`) renders the desktop rasters into
  `app/src/desktopMain/resources/app-icon/`. `AppIconTest`'s golden pins the geometry and
  `LauncherIconTest` the wiring.
- The screens — one file per screen, as today, with each list's row beside it: the Library's in
  `library/LibraryRow.kt` (issue #208) and the Vocabulary list's in `vocabulary/VocabularyRow.kt`
  (issue #212). A new screen follows §7's shape and §4's spacing.

- The hand cursor — `app/src/commonMain/kotlin/app/lekto/ui/HandCursor.kt`: the one modifier
  (`Modifier.handCursor(enabled)`) every clickable surface applies, and the architecture rule that
  makes a missing one fail `./gradlew check` (§8). The reader page overrides descendants with
  `pointerHoverIcon` directly, because its `SelectionContainer` would otherwise keep the text cursor.
- The list row's delete — `app/src/commonMain/kotlin/app/lekto/ui/RowDelete.kt`: `DeletingRow` is
  the one gesture the Library's rows and the Vocabulary list's share (§7.1, §7.4) — a trailing
  swipe on touch, a trash in the row's wash on hover with a pointer, the slot always reserved and
  the confirmation the caller's. The seam that picks the idiom is `ui/TouchClient.kt`.
- The search field — `app/src/commonMain/kotlin/app/lekto/ui/SearchField.kt`: the surface, hairline
  and icon the Library and the Vocabulary list both search through (§7.1, §7.4).
- The choice chip — `app/src/commonMain/kotlin/app/lekto/ui/Chip.kt`: one chip, used by the **Aa**
  panel's values and by the Vocabulary list's level filters, so a chip looks the same wherever it
  chooses something (§7.2, §7.4).
- The shell, its three sections and the back stack — `app/src/commonMain/kotlin/app/lekto/navigation/`:
  `AppShell` (a bar on a phone, a column beside the screen from 600 dp), `Destination` (§6's places,
  each naming the section it belongs to) and `BackStack` (the history as a value). The platform's own
  back is a seam there too: Android walks the stack, and a platform with no system back does nothing.
- Where the desktop opens files — `app/src/desktopMain/kotlin/app/lekto/DesktopFilePicker.kt`: the
  system dialog (AWT `FileDialog`) owned by the Compose window — GTK's chooser on Linux, the native
  panel on macOS and the common dialog on Windows — with the Swing chooser only as a fallback.
  Importing a book, importing a **Vault** and exporting one all pass through it, and its title is the
  one part of its chrome that follows the **app language**. On Linux the app loads the system
  FreeType before Compose starts, so GTK's chooser does not bind its fonts to Skiko's bundled copy.

When a screen changes, this document changes with it, in the same pull request. When the change
reverses something an ADR decided, the ADR wins and this document is wrong until it is fixed.
