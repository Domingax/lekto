# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

People learning a language by reading books they already want to read, at any point on the
learner curve:

- **A1–A2 beginners** building a first vocabulary, who need the page to tell them which words
  are worth the effort.
- **B1–B2 intermediate readers** whose problem is density: a native novel is readable, but
  every unfamiliar word breaks the flow.
- **C1+ advanced readers** who read fluently and want to notice and keep the rare words.

The **Reader level** is declared per **Reading language**, so one reader can be a beginner in
Spanish and advanced in English, and the app follows the language of the book in front of them.

They are self-directed: no teacher, no class, no account. They own the files (EPUB, TXT, an
occasional PDF) and they read in short sessions, phone first, with the desktop as the longer
sitting.

## Product Purpose

Lekto turns a book the reader already owns into a text they can get through. Import it, read it
with every word coloured by how well the reader knows it, tap a word to look it up offline and
save it, and let the page slowly turn from unfamiliar to readable.

Success has two co-equal outcomes, and the product is judged on both:

1. **The reader finishes the book** without stopping every few lines to look something up.
2. **The reader keeps the words** they met on the way, in a vocabulary list they can return to.

Neither is a side effect of the other. A session that ends in a saved word but an abandoned book
has failed, and so has a book finished with nothing learned.

## Positioning

The page of a real, self-imported book whose words are coloured by *this reader's* mastery, with
the whole loop — dictionary, lookup, save, review — running offline on the device, against a
per-language **Reader level** that shapes the default mastery of words not met yet.

A neighbouring reader app can copy coloured words. It cannot truthfully copy the rest at the same
time: no account, no server, no telemetry, a **Vault** the reader exports, and sync that goes to
a **Sync target** the reader owns. Local-first is the mechanism, not a marketing line — it is why
the dictionary has to be a derived, versioned **Derived asset** rather than a service.

Phrase translation is the one place the product reaches the network, and it does so with the
reader's own **API key** straight to their own **LLM provider**. There is no Lekto server in that
path either.

## Operating Context

- The books come from the reader's own library, not a catalogue. Nothing is bought, lent or
  recommended inside Lekto.
- Reading happens offline: on a commute, a plane, a sofa. Every core action works with no network.
- The offline reference is a **Dictionary pack** (EN→FR today) derived from Wiktionary and
  published as a separate CC BY-SA 4.0 artifact; the **Difficulty table** comes from an open
  frequency list. Both are versioned, download once, and are never synced.
- Sync, when it is on, is WebDAV to a server the reader runs or rents. Configuring it means a base
  URL, a username and an **Application password**, and it is off until the reader turns it on.
- The reader may read several languages, and expects the app itself in their own **App language**,
  separate from the language they translate into.
- Android is the first-class client; desktop (Linux, macOS, Windows) is the second, for longer
  reading at a desk.

## Capabilities and Constraints

Confirmed and shipped or in the tree:

- Import EPUB and TXT as first-class formats, PDF as best-effort text extraction.
- A paginated reader that colours word tokens by **Mastery level** (0–4, level 4 rendered as
  normal text), with a tap to look up a word, a long press to select a phrase, and platform
  **Pronunciation**.
- A five-point mastery selector that is itself the save: there is no separate Save button.
- A vocabulary list with search, level filters, and the sentence each word came from.
- Chapter lists, either declared by the file or inferred from its text.
- Offline dictionary lookup, external **Dictionary shortcuts**, and optional phrase translation
  through the reader's own **LLM provider** (OpenAI, Anthropic, Gemini, OpenCode Zen, OpenCode Go,
  Ollama on desktop, or any OpenAI-compatible **Custom** endpoint). Keys live in the **Secret
  store**, never in the **Vault**.
- Vault export and import; WebDAV sync with tombstones so a deletion does not resurrect.
- A user guide published as a VitePress site on GitHub Pages.

Constraints that shape every future decision:

- **AGPL-3.0**, and every dependency, test scope included, must be AGPL-compatible. The dictionary
  pack is a separate CC BY-SA 4.0 artifact and is never committed to this repository.
- **No backend, ever** (ADR-0002). There is no Lekto account, no telemetry, no server.
- **No web client** (ADR-0008). Android and desktop are Kotlin Multiplatform; a browser target is
  out of scope.
- The **Vault** holds only what the user authored or imported (ADR-0005). Derived assets are
  device-local and re-derivable.
- Reading preferences, app theme, app language and translation language are device-local and never
  travel with the Vault.
- The domain glossary in `CONTEXT.md` is the naming authority. A new concept without a glossary
  entry is a decision not yet made.

## Brand Commitments

- The name is **Lekto**. The mark is the letter L of the reading serif, carrying the mastery
  colours; the reasoning is in `docs/design.md` §5.
- The visual identity is committed and recorded in `docs/design.md`: ink on paper, no accent
  colour, mastery ramps as the only meaning-bearing colour, and a red reserved for irreversible
  actions and failures. Changing that world is a redesign, not a refinement.
- The voice is plain and quiet. The app names what happened and what to do next; it does not
  praise, gamify, or nudge. No streaks, no badges, no scores, no congratulation.
- The licence is part of the identity: free, open source, and the reader's data stays theirs.

## Evidence on Hand

- `CONTEXT.md` — the domain vocabulary, authoritative.
- `docs/adr/` — 42 architecture decisions, authoritative over code and over any plan.
- `docs/design.md` — the incumbent visual system, screen anatomy and accessibility commitments.
- `guide/` — the end-user guide, and the product's current public-facing copy.
- Real assets: the dictionary pipeline in `tools/dictionaries/`, the generated icon set and
  launcher mark, the desktop packaging scripts.

Absent, and not to be fabricated: no user research, no usability studies, no testimonials, no
download counts, no usage analytics, no press coverage, no named customers or partners, and no
pricing. Any future surface that needs proof has to earn it with real evidence first.

## Product Principles

1. **Nothing blocks the reading.** No state hides the page, no error becomes a full-screen alert,
   no dialogue interrupts a session. The book is the interface.
2. **Local-first is the product.** Every core action works offline; the reader owns the data, the
   credentials and the sync target. A feature that requires a Lekto server does not ship.
3. **Finishing and learning are one loop.** A design that improves retention at the cost of
   fluency, or fluency at the cost of retention, has picked the wrong trade.
4. **One gesture, one intention.** The level chip is the save; the swipe uncovers the delete; the
   theme preview is the choice. No action needs a second confirmation step it did not earn.
5. **Say the limit out loud.** A missing dictionary, a provider that is not configured, a desktop
   without a screen reader: the app states it plainly and offers the next move, rather than
   hiding it or pretending.

## Accessibility & Inclusion

- **4.5:1 minimum** for any coloured word, measured per theme, and a **non-colour indicator** (the
  underline) for every highlighted level, so mastery never depends on hue alone.
- **48 × 48 dp** minimum for anything tappable, and the reading text honours the platform font
  scale multiplied by the reader's own size choice.
- The reader's page exposes the page turns as accessibility actions, beside the chevrons and the
  swipe, so a screen-reader or switch user keeps a way through the book.
- Android carries the complete screen-reader path. The desktop client supports one on macOS, on
  Windows with the Java Access Bridge enabled, and **not on Linux**; the guide says so before a
  reader relies on it.
- No WCAG conformance level is claimed anywhere in the repository; the commitments above are the
  ones the project holds itself to.
