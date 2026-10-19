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
- **Red** — `#B3261E` on Light and Sepia, and only for irreversible actions (delete an entry,
  disconnect a server, remove a key) and for failures. It never appears as decoration, and never
  as a warning about something reversible: that is what muted ink and clear words are for.

## 3. Typography

Two voices, and they never swap roles.

- **Reading** — the book's text. The platform's serif by default, or its sans, chosen by the
  reader, at a size, leading and margin the reader controls. This is a **Reading preference**
  (ADR-0035) and the app never overrides it.
- **Interface** — everything the app says. The platform's sans-serif. No bundled font: a shipped
  face is a new artefact with its own licence notice, and it buys what the platform already gives.

One deliberate borrowing: a **screen title** is set in the reading serif — "Library",
"Vocabulary", "Settings", and the word at the top of the lookup panel. The app's own headings
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
  panels, sections), 16 dp for anything that is a sheet or a dialogue, and the app mark's tile at
  22 % of its size.
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
- **A navigation icon must be recognisable at 24 dp**, which is not the same as being handsome in
  a catalogue. `library` reads as a barcode and is not used; `book-open` is. `notebook-pen` reads
  as a scribble; `bookmark` is. An icon that only works large belongs in a sheet, not in the bar.
- `arrow-left` must mirror in right-to-left layouts; the generator must set `autoMirror` and the
  regeneration script must keep it.
- The launcher mark is layered (issue #177): the **page with the coloured spine** for the icon
  itself, and the **letter in the reading serif alone** for the monochrome layer Android tints
  from the wallpaper, and for the system launch screen. A combined glyph reduced to one colour at
  48 dp is a smudge; a single letter is not.

## 6. Shell and navigation

Three sections, and no fourth:

| Section | What it is for | Icon |
| ------- | -------------- | ---- |
| **Library** | the books, progress, search, import | `book-open` |
| **Vocabulary** | the words the reader saved | `bookmark` |
| **Settings** | everything else: appearance, languages, provider, vault | `settings` |

- **Import is not a section.** It is the Library's action, written as a word beside the title, and
  as a filled button in the empty state. A tab that opens a file picker is not a place.
- **The reader leaves the shell** and is full-screen. Its chrome is two bands, 48 dp and 64 dp,
  and a tap on the middle third hides both.
- **Languages and Appearance are entries inside Settings**, not sections (ADR-0034, ADR-0036).
- **The back stack is real**, and Android's back button walks it. Today it is a single `when` over
  a sealed destination with no history, and back leaves the app from anywhere: that is a defect,
  not a choice. The reader's chapter list closes on Back before the shell sees it (ADR-0037); the
  shell's history is #185.
- **Desktop**: the same three sections become a fixed left column. In the reader, the right side is
  a **context column** — the chapter list when nothing is touched, the word when something is. It
  is reserved rather than overlaid, so the page never moves. Below roughly **1100 dp** the column
  has no room and the reader falls back to the phone's bottom sheet.

## 7. Screen anatomy

Each screen below is one row of what it shows, and one note on what it must not do.

### 7.1 Library

`Library` (serif) · `Import` (word) · `Search` · rows · bottom navigation.

A row is a **language vignette** (44 × 60), the title, a line of `FORMAT · 28 %`, and a **3 dp
progress rule**. There are **no covers**: a `Book` carries a title, a language, a format and a
file name, and only the EPUB format has a cover image at all — a shelf with covers for some books
and none for others reads as broken. The vignette is where a cover would go if one ever exists.
The file name is not on the row: it survives in import errors and the delete confirmation, where
it identifies a file rather than a book.

**Reading progress** is where the reader is now, not the furthest point reached: the share of the
book's rendered text before the **Reading position**, rounded down, so a book on its last page
reads just under 100 %. The number and the rule carry the same whole percentage. A book with no
Reading position reads `FORMAT · Not started` and has no rule; one whose text has not been
measured yet reads `FORMAT · …`, also without a rule. The Library never claims a book is finished;
that distinction is #175's.

Ordering leads with the three books read last (#174, #188); search is still missing (#173).

**States**: empty says what to do and carries the action; loading shows **skeleton rows**, never
the empty message; a failed import is a bordered block in the list, with a way to dismiss it.

### 7.2 Reader

`arrow-left` · book title · `Aa` | the page | `‹` · position · `›`.

The chevrons are not buttons: they *show* the three tap zones (previous third, chrome third, next
third). The position reads `Chapter 4 · Page 99 of 357`. Before the first chapter, and in a book
with one chapter, it reads `Page 99 of 357` and no chapter is current.

Tapping the position opens the chapter list, which is why the position is a control rather than a
label. A book with one chapter has no list, so its position stays a label (ADR-0037).

The chapter list is a bottom sheet over the page on a phone, up to about 60 % of the height, and
the reserved context column on desktop (§6). It keeps the book's hierarchy, highlights the current
chapter and scrolls it into view. It closes on a choice, a tap outside, its Close action, or Back.
The lookup panel and the chapter list are never open together.

Chapters start on a new page. A jump moves the **Reading position** to the chapter's first block
at once, and the page number and total update as pagination counts them.

The **`Aa` panel** (`Text face`, `Text size`, `Margins`: `Serif`/`Sans-serif`,
`Narrow`/`Comfortable`/`Wide`) sets the face, the size and the margins, and previews live: there
is no button to confirm a reading preference. The App theme is **not** here — it recolours the
app, not the book, and it lives in Settings ▸ Appearance (ADR-0035, ADR-0036).

### 7.3 The lookup panel

Raised from the bottom over the reader, never above ~45 % of the height, the page still visible
and the touched word still washed above it. On desktop it is the context column instead, and
closing the word returns the column to the chapter list.

A **word**: the word in the serif, its part of speech and pronunciation, the speaker and the close
action; then the senses with their translation; then the level; then the external references as a
muted line.

**There is no Save button.** The level (`Mastery`) *is* the save: one tap on a level chip stores the word at
that level, which is one gesture for one intention. The chips show the number and the colour, and
the current level's **name** stands beside them, so the numbers never need a legend.

There are **no dictionary tabs**. The app has one offline dictionary and four external references
that leave the app; presenting them as tabs would promise a symmetry that does not exist.

**States**: a missing dictionary still allows the save, and says so
(`The offline dictionary isn't installed yet.`); a phrase with no provider
explains that a phrase needs an AI while a word does not, and offers the settings link; a failure
is a sentence inside the panel, never a dialogue over the reader.

### 7.4 Vocabulary

`Vocabulary` (serif) · `128 words` · `Search` · level filters · rows · bottom navigation.

A row is the **word in the serif** — it is the word as the book printed it — then its colour-dotted
level and name, its translation, and the sentence it came from. The delete is a muted **trash
icon**, and it **confirms**: a list with no undo does not get to delete on a mis-tap.

**Empty and no-results are different states.** One says how words arrive (`No words saved yet —
tap a word while reading to save it`); the other names the search (`No words match "princesse".`)
and offers to clear it. The filter row scrolls — `All levels`, then the five levels by name — and
a partially cut chip is what says so.

Tapping a row does nothing today; it should open the word panel so a level can be revised outside
the reader (#178).

### 7.5 Settings

An **index**, not a form: a row per area, with its current value underneath, grouped in the
sentence-case labels *Reading*, *Data*, *Application*.

The provider's API key and the WebDAV server are **screens of their own**, not two forms buried
mid-page. Their fields read like a printed form — the label in small muted type, the value under
it, a hairline — rather than a wall of outlined boxes.

The rules for a form: **one filled button per screen**; everything reversible applies on change,
with no confirmation, and something that costs a network call or writes a secret gets an explicit
action; anything irreversible is written in red.

### 7.6 Appearance, Languages, LLM provider, Vault and sync

- **Appearance** — the four theme entries, each *shown on its own paper*: a 48 × 34 preview with two
  lines of ink, System split down the middle. A theme is chosen by looking at it.
- **Languages** — one row per **Reading language**, its state (word-difficulty data, offline
  dictionary) and the download action when one is missing.
- **LLM provider** — provider, key, model. The key is masked with a reveal, and the screen says
  where it is stored: the device's keychain, never the vault.
- **Vault and sync** — export and import; then the server, the user, the application
  password, the state ("Sync is on.") and the actions. The vault is
  **app-private** (ADR-0010): there is no folder to choose, and a "change location" control would
  describe an app that does not exist.

### 7.7 Launch

Android 12 and later paints a launch screen from the window background and the app's icon, so there
is no launch screen to design, only a background to get right: **the active theme's paper**
(ADR-0036). A dark theme that flashes white has a bug, not a style.

The first frame is the shell with the Library's rows **in skeleton**, never a bare logo: the wait
is a local vault read, and a screen shown for 40 ms is a flicker, which reads as slowness. No
animation, no tagline, no artificial delay.

## 8. States and feedback

| Situation | What the reader sees |
| --------- | -------------------- |
| Saving a word | the word's colour changes in the page. No toast |
| Importing | an inline progress bar in the Library |
| Loading a list | skeleton rows, never the empty message |
| Empty | a sentence saying what to do, and the action |
| No search results | the query named back, and a way to clear it |
| Offline dictionary | a sentence inside the lookup panel |
| Failure | a block where the action was, with a way to dismiss it |
| Deleting anything | a confirmation, because there is no undo |

Nothing blocks the reading: an error never becomes a full-screen alert, and no state ever hides
the page behind it.

## 9. Accessibility

- **4.5:1** for any coloured word (§2.2), measured per theme.
- A **non-colour indicator** for every highlighted level: the underline.
- **48 × 48 dp** minimum for anything tappable.
- The language vignette's colour is decoration; its two letters are the information.
- Every icon either carries a `contentDescription` or sits beside a label that names it.
- The reading text honours the platform's font scale, and the **Reading preference**'s size stays
  in `sp` so the two multiply.
- On desktop, the page is **never covered**: the context column is reserved, so opening the panel
  does not reflow the text and does not move the reader's eye.

## 10. Where this lives in code

- Tokens and `LektoTheme` — `app/src/commonMain/kotlin/app/lekto/theme/`. Until that package
  exists, this document is the only reference, and the prototype's hard-coded values are the
  source to copy from.
- The master ramps — one file, three ramps (§2.2).
- The generated icons — `app/src/commonMain/kotlin/app/lekto/ui/icons/`, regenerated by a script
  that pins the Lucide tag.
- The screens — one file per screen, as today. A new screen follows §7's shape and §4's spacing.

When a screen changes, this document changes with it, in the same pull request. When the change
reverses something an ADR decided, the ADR wins and this document is wrong until it is fixed.
