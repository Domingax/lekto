---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-02c-executive-summary', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish', 'step-12-complete', 'step-e-01-discovery', 'step-e-02-review', 'step-e-03-edit']
status: 'complete'
classification:
  projectType: 'native_desktop+mobile_app'
  domain: 'edtech'
  complexity: 'low-medium'
  projectContext: 'greenfield'
inputDocuments:
  - '_bmad-output/planning-artifacts/product-brief-2026-03-02.md'
  - '_bmad-output/planning-artifacts/research/technical-stack-research-2026-03-03.md'
  - '_bmad-output/brainstorming/brainstorming-session-2026-03-02-T2100.md'
workflowType: 'prd'
lastEdited: '2026-04-05'
editHistory:
  - date: '2026-04-05'
    changes: 'Platform pivot: web removed, Android (Capacitor) + Desktop native (Tauri) adopted. Vault architecture updated (lekto.db in vault folder). All web references replaced throughout.'
---

# Product Requirements Document - Lekto

**Author:** Damien
**Date:** 2026-03-07

## Executive Summary

**Lekto** is an open-source immersive language learning application for Android and native Desktop (Linux, Windows, macOS). It replicates the reading experience of subscription-based language learning readers — content import, vocabulary highlighting by mastery level, integrated contextual lookup, and vocabulary management — on a fully local architecture: no server required, no subscription, user data stays on the device.

The product targets two user profiles: **technical users** (developers, power users) who want to read in their target language, own their data, and optionally connect their own AI services; and **general learners** who want an "open and read" experience that is free, simple, and works out of the box.

The problem being solved is concrete friction: mandatory internet connection to read, data stored on a third-party server without simple export, and a monthly subscription required for a complete experience. Lekto removes these frictions one by one without sacrificing reading experience quality.

### What Makes This Special

**End-to-end fluency is the product.** Not a single feature — the coherence of the entire journey: import an EPUB or PDF → text displayed with words color-coded by mastery level → click a word or phrase → dictionary or AI translation without leaving the page → word saved with context. Every step is immediate, local, and uninterrupted.

**Open source as contribution, not strategy.** No business model to defend. The codebase is readable, forkable, and contributor-friendly. Community adoption is a secondary success; the primary success is personal.

**BYOK AI as product philosophy.** AI is not an opaque service provided by the app — it is a service the user connects from their own subscriptions (OpenAI, Anthropic, Copilot, Ollama...). Applications should consume the user's AI, not the reverse. The app is fully functional without AI; AI is a progressive enhancement.

## Project Classification

| Dimension | Value |
|-----------|-------|
| **Project Type** | Native Android (Capacitor) + Native Desktop (Tauri) — React/Vite/TypeScript shared frontend |
| **Domain** | EdTech / Consumer Language Learning |
| **Complexity** | Low-medium — no regulatory constraints, personal open-source project |
| **Project Context** | Greenfield — new application, no existing codebase |

## Success Criteria

### User Success

Lekto is successful when the author uses it as their primary daily reading tool without feeling limited compared to existing immersive language learning readers. The bar is functional parity for the core reading loop, not feature parity across the board.

Key success moments:
- **Reading flow**: A full chapter completed without the lookup panel breaking concentration — word and phrase translation feels instant and contextual.
- **Cross-device continuity**: A session started on Android (pages read, words saved) is seamlessly resumable on the desktop app — same reading position, same updated vocabulary list, via shared vault folder.
- **Habit formation**: The app is opened for reading sessions as consistently as a premium tool would be, without friction creating a reason to switch back.

### Business Success

No commercial objectives. Success is personal: the author completes reading sessions in Lekto without reverting to existing tools.

Secondary success (not a requirement): the project is useful to others. 10 satisfied users would already be a positive outcome.

### Technical Success

- **Documentation as a first-class deliverable**: README, architecture documentation, and how-it-works guides complete, accurate, and maintained throughout development. A new contributor can understand the project and run it locally from the README alone.
- **Clean, testable codebase**: Unit and E2E tests implemented; no merge without a green CI pipeline (lint → test → build).
- **BMAD validation**: The project validates that BMAD agents can develop independent features concurrently without conflicts, following defined architecture and conventions.

### Measurable Outcomes

| Outcome | Definition of Done |
|---------|-------------------|
| Full reading session | Import → read → save words end-to-end without switching to another tool |
| Cross-device sync | Read on Android, resume on Desktop at same position with same vocabulary (via shared vault folder) |
| New device restore | Point app to existing vault → full reading history and vocabulary restored |
| Contributor-ready | New contributor runs the project locally following README in under 10 minutes |

## User Journeys

### Journey 1 — First Use

A user hears about Lekto on a forum, downloads the desktop app, and installs it. No account prompt, no paywall — the app opens directly into an empty library. A vault folder is automatically created in a standard local location so the user can start immediately.

A non-intrusive prompt appears: *"Your reading data is stored in a local vault. Want to sync across devices? Point your vault to a cloud-synced folder."* The user can dismiss it and read now, or immediately redirect the vault to their Google Drive or Syncthing folder — their choice, no pressure.

They tap "Import" and pick an EPUB from their device. A brief import animation, and the book appears in the library. They open it — the text is there, words color-coded by mastery level.

They tap on an unfamiliar word. A panel slides up instantly, showing dictionary entries from WordReference and Google Translate — still inside the app, no tab switch. They read the translation, understand the word in context, and tap "Save". The word is now in their vocabulary list with the sentence it came from.

They close the app. *This felt fast. This felt right.*

**Capabilities revealed:** vault auto-creation with optional relocation, file import flow, EPUB parsing and tokenization, mastery-level word coloring, translation panel (dictionary), in-app dictionary display, vocabulary save with context.

---

### Journey 2 — Daily Reading Session

Three days later, the same user opens the app on their Android phone during their commute. The library shows the book they started, with a progress bar and "Resume at Chapter 3". They tap it — the reader opens exactly where they left off.

They read for 15 minutes, clicking on a handful of words, saving two of them. They reach their stop and close the app.

That evening, they open the Lekto desktop app on their laptop. The library reflects the same progress — the vault folder is the same cloud-synced directory on both devices. The two words saved on the phone are in their vocabulary list. They continue reading from Chapter 3, page 12 — exactly where the phone left off.

**Capabilities revealed:** reading position persistence, cross-device sync via shared vault folder, vocabulary sync, library resume indicator, platform consistency (Desktop + Android).

---

### Journey 3 — AI Discovery and Setup

A few sessions in, the user selects a multi-word phrase they don't understand. The translation panel opens, but instead of a full phrase translation, it shows: *"Connect your AI to translate phrases and sentences."* A small "Set up" link sits below the message — unobtrusive, never blocking.

Curious, they tap it. A settings screen explains in plain language: *"Lekto can use your own AI subscription to translate phrases. Your key is stored securely on this device only and never shared."* Three providers are listed: OpenAI, Anthropic, Ollama. They pick OpenAI, paste their API key into a masked field, and tap Save.

They return to their book, select the same phrase. This time, a fluid translation appears — contextual, accurate, in their native language. They save the phrase with the translation.

**Capabilities revealed:** progressive disclosure of AI features, BYOK API key setup flow, secure key storage, provider selection, phrase-level AI translation, graceful fallback when AI is not configured.

---

### Journey 4 — OSS Contributor

A developer learning Japanese discovers Lekto on GitHub. The README is clear: what the project does, why it exists, how to run it locally. They clone the repo, run `npm install && npm run dev`, and the app opens in a Tauri development window in under 5 minutes.

They browse the architecture documentation and understand the import pipeline: EPUB → tokenizer → SQLite. They want to add support for a new dictionary provider for Japanese (Jisho). The contributing guide points them to the relevant module. The code is structured, typed, and tested — they can follow the pattern of an existing provider.

They open a PR. The CI pipeline runs automatically — lint, tests, build all pass. A maintainer reviews and merges within the week.

**Capabilities revealed:** contributor documentation (README, architecture, contributing guide), local dev setup, modular dictionary provider system by language pair, CI pipeline on PRs.

---

### Journey Requirements Summary

| Capability Area | Revealed By |
|----------------|-------------|
| Vault auto-creation with optional relocation | Journey 1 |
| File import + EPUB/PDF parsing and tokenization | Journey 1 |
| Word tokenization + mastery-level coloring | Journey 1 |
| In-app translation panel (dictionary) | Journey 1, 2 |
| Vocabulary save with context | Journey 1, 2 |
| Reading position persistence | Journey 2 |
| Vault-based cross-device sync (Android ↔ Desktop via shared folder) | Journey 2 |
| Progressive AI feature disclosure | Journey 3 |
| BYOK API key setup + secure storage | Journey 3 |
| AI phrase translation with fallback | Journey 3 |
| Developer documentation + CI | Journey 4 |
| Modular dictionary provider system by language pair | Journey 4 |
| Reading customization (font, theme, text size, page swipe) | FR18, FR40 |
| Platform consistency (Desktop + Android) | Journey 2 |

## Innovation & Novel Patterns

### Detected Innovation Areas

**BYOK AI in Language Learning**

The BYOK (Bring Your Own Key) movement — users connecting their own AI subscriptions to third-party applications — is an emerging pattern with an active community, but it has no significant presence in the language learning space. Lekto does not invent this approach; it applies it to a domain where it is currently absent.

Proprietary language learning tools are moving in the opposite direction, bundling AI as a premium paid feature within their subscription tiers. Lekto's BYOK model is structurally incompatible with a subscription business, making it unlikely that commercial competitors will adopt it — creating durable differentiation.

**Local-First Vault Architecture in an Immersive Reader**

The vault model — user-owned, portable, sync-agnostic data storage — is well-established in the note-taking space (Obsidian, Logseq) but absent from immersive language learning tools. The reason is architectural as much as business: community features (shared content libraries, collaborative vocabulary, user-generated lessons) are a core value driver in commercial language learning tools and inherently require cloud infrastructure, creating a virtuous cycle — community → cloud → subscription. Note-taking tools lack this community imperative, which is why local-first has succeeded there.

Lekto's deliberate exclusion of community features is not only a scope decision — it is what makes local-first architecture coherent and sustainable.

### Market Context

- Language learning tools (immersive readers, vocabulary builders) are uniformly cloud-first and subscription-based
- Community features require cloud infrastructure and reinforce the cloud-first model in this space
- Note-taking tools have validated the vault/local-first model at scale (Obsidian, Logseq) precisely because note-taking is a personal, non-community activity
- BYOK AI is an established pattern in developer and productivity tools, not yet present in language learning
- The language learning tool market is smaller than note-taking, limiting community growth potential — but also meaning less competition for a local-first alternative

### Validation Approach

- **BYOK AI value**: validated by the author's own daily use — consistent use of AI translation over dictionary links confirms its value.
- **Vault sync reliability**: validated by the cross-device sync success criterion (read on Android, resume on web).

## Android + Desktop App Specific Requirements

### Architecture Overview

Lekto targets Android (via Capacitor) and native Desktop (via Tauri — Linux, Windows, macOS). Both platforms share a React/Vite/TypeScript frontend. Platform-specific capabilities (filesystem, file picker, secure storage, TTS) are handled exclusively through adapters in `shared/platform/` — no platform-specific branches in business logic.

### Platform Support

| Platform | Target | Notes |
|----------|--------|-------|
| Android | Full support | Capacitor wrapper; Android 11+ (API 30) required |
| Desktop Linux | Full support | Tauri v2; AppImage distribution |
| Desktop Windows | Full support | Tauri v2; NSIS installer distribution |
| Desktop macOS | Full support | Tauri v2; .dmg distribution |
| iOS | Post-MVP (v5) | Architecturally compatible; deferred |
| Web browser | Not a target | No web distribution; `npm run dev` used for development only |

### Vault Architecture

The vault is a user-managed folder containing all reading data:

```
vault/
├── books/       ← imported source files (EPUB, PDF, TXT)
└── lekto.db     ← SQLite: all application data (vocabulary, progress, metadata)
```

`lekto.db` lives inside the vault folder on disk. Copying the vault folder to another device = full data migration.

**Per-platform strategy:**
- **Desktop (Tauri):** SQLite opened directly at the vault folder path via Rust backend — no sync layer needed
- **Android (Capacitor):** SQLite managed internally; synced to/from `lekto.db` in the vault folder on write (debounced) and on vault open

### Offline Mode

The application is **fully offline by design**. Network access is used only for:
- Dictionary lookups — opened in-app browser; degrade gracefully if offline
- BYOK AI translation — silently unavailable if offline
- Vault sync — handled entirely by the user's chosen third-party sync service (Syncthing, cloud folder, etc.)

### Native Features by Platform

| Feature | Android (Capacitor) | Desktop (Tauri) |
|---------|--------------------|--------------------|
| File system access | `@capacitor/filesystem` (Documents dir) | Tauri `fs` plugin (any OS path) |
| Vault folder picker | `@capawesome/capacitor-file-picker` (SAF) | Tauri `dialog` plugin (native OS picker) |
| Secure storage (API keys) | `@aparajita/capacitor-secure-storage` (Android Keystore) | Tauri `stronghold` plugin |
| TTS audio | `@capacitor-community/text-to-speech` | Tauri TTS plugin (OS speech engine) |
| File import (EPUB/PDF) | `@capawesome/capacitor-file-picker` | Tauri `dialog` plugin |

### Distribution

| Channel | Timeline |
|---------|----------|
| Android — GitHub Releases (APK) | MVP |
| Android — F-Droid | MVP |
| Desktop — GitHub Releases (Linux AppImage, Windows NSIS) | MVP |
| Desktop — macOS (.dmg) | MVP |
| Android — Google Play Store | Post-MVP |
| iOS — App Store | v5 |

### Implementation Constraints

- **Shared frontend**: React/Vite/TypeScript codebase shared across both platforms — all differences in `shared/platform/` adapters only
- **No web distribution**: no GitHub Pages, no PWA, no OPFS — web browser used for development only
- **No SSR / SEO**: SPA only
- **No real-time requirements**: no WebSockets, no server push — all data is local
- **Vault boundary**: all file I/O routes through `shared/platform/filesystem/` — no direct Capacitor or Tauri API calls in business logic

## Product Scope & Roadmap

### MVP Strategy

**Approach:** Personal Utility MVP — the product is done when it replaces existing immersive language learning tools as the author's daily reading tool, without friction or compromise on the core experience. No market validation objective, no investor milestone, no revenue target.

**Resource profile:** Solo developer with AI agent assistance (BMAD workflow). Scope must remain lean enough for one person to ship.

**MVP gate criterion:** Every feature must pass — "without this, does the core reading loop fail?" If no, it is post-MVP.

### MVP Feature Set

| # | Feature | Notes |
|---|---------|-------|
| 1 | **Book import** | EPUB, PDF (word-by-word overlay), plain text |
| 2 | **Library with resume** | Shows imported books with progress; opens at last reading position |
| 3 | **Immersive reader** | Text color-coded by word mastery level; reading customization (font, size, theme, swipe) |
| 4 | **Translation panel** | Word/phrase selection → dictionary services (WordReference, Reverso, Google Translate, Linguee) in-app |
| 5 | **BYOK AI translation** | Phrase translation via user's own LLM; gracefully absent if not configured |
| 6 | **TTS audio** | Pronunciation playback — OS TTS engine (Desktop), Android TTS (Android) |
| 7 | **Vocabulary management** | Save word/phrase with translation, notes, confidence level (1→4 + known) |
| 8 | **Vault-based sync** | Portable folder structure; multi-device sync via user's chosen third-party service |
| 9 | **Platforms** | Android + Desktop (Linux, Windows, macOS) |
| 10 | **Accessibility** | WCAG 2.1 AA (web), TalkBack (Android) — first-class, not post-MVP |

### Out of Scope for MVP

- Phrase/expression highlighting in reader (multi-word vocabulary coloring)
- Spaced repetition review
- Learning statistics / progress dashboard
- iOS
- Audio/podcast support
- Google Play Store publication
- Community features

### Post-MVP Roadmap

| Phase | Focus |
|-------|-------|
| **Post-MVP** | Google Play Store publication |
| **v2** | Phrase/expression highlighting in reader — saved multi-word vocabulary entries appear as colored spans in the reader, matching their mastery level (deferred: N-gram matching complexity, cross-layer render impact, performance at scale) |
| **v3** | Spaced repetition review (Anki-like) + learning progress statistics |
| **v4** | Audio — podcast/audio with synchronized transcript (user-provided, BYOK STT via Whisper API, or RSS-embedded) |
| **v5** | iOS support |

### Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **PDF word extraction complexity** | High | PDF.js text content layer + transparent word-level span overlay using extracted character positions. CJK PDF support deferred to post-MVP. |
| **Android file system restrictions (Android 11+)** | Medium | System file picker for vault folder selection; persist access via content:// URIs. Default vault to app Documents directory with optional relocation. |
| **Solo developer scope creep** | High | Strict MVP gate criterion applied to every feature decision. |
| **Vault sync conflicts** | Medium | Out of scope — delegated to user's sync service. Lekto must not corrupt vault data; document this limitation clearly. |

## Functional Requirements

### Content Import

- **FR1**: User can import an EPUB file from their device into the library
- **FR2**: User can import a PDF file from their device into the library
- **FR3**: User can import a plain text file from their device into the library
- **FR4**: The system tokenizes imported content into words, punctuation, and whitespace at import time (not at read time)
- **FR5**: The system assigns a normalized word key to each unique word for vocabulary tracking
- **FR6**: User can view feedback during import processing
- **FR7**: User can assign a language to the imported content

### Library & Navigation

- **FR8**: User can view all imported books in a library with title, cover, and reading progress
- **FR9**: User can open a book and resume reading from the last saved position
- **FR10**: User can navigate between chapters or sections within a book
- **FR11**: User can delete a book from the library

### Immersive Reader

- **FR12**: User can read content with words visually differentiated by mastery level
- **FR13**: User can update the mastery level of a word directly from the reader
- **FR14**: User can select a single word to open the translation panel
- **FR15**: User can select a multi-word phrase to open the translation panel
- **FR16**: User can navigate between pages via swipe gesture or navigation controls
- **FR17**: Reading position is automatically saved continuously as the user reads
- **FR18**: User can customize reading display (font family, text size, light/dark theme)

### Translation & Lookup

- **FR19**: User can view dictionary lookups for a selected word via integrated dictionary services (WordReference, Reverso, Google Translate, Linguee) without leaving the reading context
- **FR20**: User can request AI translation for a selected phrase when an AI provider is configured
- **FR21**: The translation panel displays a non-blocking prompt to configure AI when no provider is set and a phrase is selected
- **FR22**: User can hear TTS audio pronunciation for a selected word or phrase
- **FR23**: The translation panel degrades gracefully when offline

### Vocabulary Management

- **FR24**: User can save a word or phrase from the translation panel to their vocabulary list
- **FR25**: User can attach a translation to a saved vocabulary entry
- **FR26**: User can add personal notes to a saved vocabulary entry
- **FR27**: User can set a confidence level (1–4 + known) on a saved vocabulary entry
- **FR28**: User can view the sentence context in which a word was originally saved
- **FR29**: User can view, update, and delete entries in their vocabulary list

### Vault & Data Sync

- **FR30**: The system automatically creates a vault folder in a default local location on first launch, containing `books/` and `lekto.db`
- **FR31**: User can relocate the vault to a different folder (e.g., a cloud-synced directory)
- **FR32**: All reading data (books, vocabulary, progress) is stored in the vault folder as portable files — copying the vault to another device restores the full experience
- **FR33**: User can point the app to an existing vault folder containing a valid `lekto.db` and restore all reading history and vocabulary
- **FR34**: The system detects and reflects vault contents on app launch

### AI Configuration

- **FR35**: User can configure a BYOK AI provider (OpenAI, Anthropic, Ollama, or equivalent)
- **FR36**: User can enter, save, and update an API key for their chosen AI provider
- **FR37**: API keys are stored using OS-level secure storage and never written to the vault
- **FR38**: The app is fully functional without any AI provider configured

### Settings & Accessibility

- **FR39**: User can configure their target language and native language
- **FR40**: User can access and manage all app settings from a dedicated settings area (vault location, AI provider, reading preferences, language settings)
- **FR41**: User can navigate all app features using keyboard only (Desktop)
- **FR42**: All interactive elements expose accessible labels for screen readers (web: ARIA, Android: TalkBack)
- **FR43**: Word mastery coloring is supplemented with non-color visual indicators for colorblind users
- **FR44**: The reader respects the system font size setting on Android

## Non-Functional Requirements

### Performance

- Translation panel opens within 200ms of word or phrase selection
- Page turn and scroll animations complete within 100ms
- EPUB import for a 300-page book completes in under 15 seconds on modern hardware
- App initial load completes in under 3 seconds
- Vault state detected and reflected within 1 second of app launch
- Reading performance does not degrade as vocabulary list grows (tested up to 10,000 entries)

### Security

- API keys stored exclusively using OS-level secure storage (Android Keystore on Android; Tauri Stronghold / system keychain on Desktop) — never written to vault or disk
- API keys never appear in log output, error messages, debug panels, or crash reports
- All calls to external services use HTTPS exclusively
- API key input fields mask the value by default
- Vault files contain no secrets — any vault can be shared or inspected without exposing credentials

### Privacy

- No telemetry, analytics, or usage data collected or transmitted
- No data sent to any remote server except user-initiated calls: dictionary lookups (in-app browser), BYOK AI translation, TTS playback
- All user data remains on the user's device inside the vault
- All core reading features function with no network connection

### Accessibility

- Desktop and Android interfaces conform to WCAG 2.1 Level AA
- All interactive elements keyboard-navigable on Desktop
- All interactive elements have accessible labels for screen readers (ARIA on Desktop, content descriptions on Android / TalkBack)
- Word mastery colors supplemented with non-color visual indicators (pattern, underline, or icon) for colorblind users
- Minimum touch target size of 48×48dp on Android
- Reader respects system font size setting on Android

### Integration Reliability

- Dictionary service failures handled gracefully — panel remains open with a clear non-blocking message; app does not crash
- BYOK AI failures (invalid key, timeout, no network) display a clear inline error without disrupting the reading session
- TTS unavailability handled gracefully — audio button hidden or disabled with no error thrown
- Lekto must not corrupt vault data under any circumstance; sync conflict resolution is delegated to the user's sync service

### Maintainability

- Core business logic (import pipeline, tokenization, vocabulary management, vault operations) has unit test coverage
- E2E tests cover the primary user journey (import → read → lookup → save) on Android and Desktop
- All commits must pass CI pipeline (lint → unit tests → build) before merge
- Public module APIs documented; architectural decisions recorded in project documentation
