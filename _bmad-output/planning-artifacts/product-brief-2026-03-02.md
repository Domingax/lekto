---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments:
  - '_bmad-output/brainstorming/brainstorming-session-2026-03-02-T2100.md'
  - 'screenshot-lingq-reader-mode (provided by Damien)'
  - 'screenshot-lingq-multi-word-panel (provided by Damien)'
date: 2026-03-02
author: Damien
---

# Product Brief: LingQ Open

<!-- Content will be appended sequentially through collaborative workflow steps -->

## Executive Summary

**LingQ Open** is an open-source language learning application built on a local-first architecture, available on Web and Android. It replicates the immersive reading experience of LingQ — content import, vocabulary highlighting by mastery level, multi-source contextual translation, and an integrated vocabulary management system — without cloud dependency or mandatory subscription.

The project serves a dual purpose: a personal language learning tool, and an open-source foundation that the community can use, extend, or fork freely.

---

## Core Vision

### Problem Statement

Immersive language learning tools (such as LingQ) deliver an effective experience but rely on centralized cloud infrastructure, remote user data storage, and subscription-based models. No open-source, local-first alternative exists that provides an equally fluid immersive reading experience with integrated vocabulary management.

### Problem Impact

- Users have no control over their learning data (saved words, progress, notes)
- No ability to customize or extend the tool to personal needs
- No true offline operation
- No community-driven open-source equivalent exists

### Why Existing Solutions Fall Short

**LingQ**: excellent reference product, but proprietary and cloud-only — no self-hosting option, no code modification possible.
**Anki alone**: excellent for spaced repetition but no immersive reading experience or contextual translation.
**Other readers**: reading only, no vocabulary layer or integrated translation.

### Proposed Solution

A local-first web and mobile application providing:

1. **Content import**: EPUB, PDF, plain text
2. **Immersive reading**: text with words color-coded by mastery level (unknown → known), paragraph-by-paragraph navigation
3. **Contextual translation panel** on word or phrase selection:
   - **Single word** → dictionary services (WordReference, Reverso, Google Translate, Linguee) — always available, no configuration required
   - **Phrase / multi-word selection** → AI translation via user's own LLM (BYOK: OpenAI, Anthropic, Gemini, Ollama...) if configured; falls back to dictionary services if no LLM is connected
   - **AI optional**: the app is fully functional without AI — AI features are hidden or shown as "unavailable" with a link to configuration when no LLM is set up
   - **TTS audio**: pronunciation playback for selected word or phrase (Web Speech API on web, Android TTS on Android)
4. **Vocabulary management**: save words/phrases with translation, personal notes, confidence levels (1→4 + known)
5. **Synchronization**: via user-managed folder/file structure — no server to install

### Key Differentiators

- **Open source & forkable**: accessible, modifiable, community-contributable codebase — zero vendor lock-in
- **Local-first**: all user data stays on the device
- **BYOK AI (optional)**: user connects their own LLM for phrase/paragraph translation — AI is a progressive enhancement, not a requirement
- **Multi-dictionary**: integration of reference dictionary services (WordReference, Reverso, Google Translate, Linguee) for word-level lookups
- **Integrated TTS**: audio playback for words and phrases, using native platform APIs
- **Cross-platform**: Web + Android (iOS planned post-MVP)
- **Zero subscription**: no recurring cost, no server to host

---

## Target Users

### Primary Users

**Persona 1 — "The Self-Directed Language Learner"**
*Example: Damien, software developer, learning English through extensive reading*

- **Context**: Reads in their target language daily, using books and articles they genuinely enjoy. Prefers short, focused sessions (10–15 min) that fit into a busy schedule, though session length varies by individual.
- **Current workflow**: Imports EPUB/PDF books, reads with a translation panel open, saves unknown words, reviews them later with spaced repetition.
- **Frustrations with existing tools**: Cloud dependency, lack of control over personal learning data, inability to customize the experience.
- **What success looks like**: Opens the app, reads a few pages, encounters unknown words, gets instant translations, saves them — all offline, all local, synced seamlessly to their other devices via their existing cloud folder.
- **Tech comfort**: Comfortable with basic configuration (API key setup, pointing an app to a folder). Not necessarily a developer.

---

**Persona 2 — "The Privacy-Conscious Power User"**
*Example: A developer or tech-savvy professional who already uses tools like Obsidian, Syncthing, self-hosted services*

- **Context**: Values data sovereignty. Already has LLM API subscriptions (OpenAI, Anthropic, etc.) and uses them in multiple tools. Uses folder-based sync (Syncthing, rclone, cloud folder) for personal data.
- **Current workflow**: Likely already learning a language with LingQ or Anki, frustrated by the cloud-only model.
- **Frustrations**: Vendor lock-in, subscription costs, no offline mode, no way to export/own their vocabulary data.
- **What success looks like**: Configures their API key once, points the vault to their Syncthing folder, and gets a faster/richer translation experience than LingQ using their own AI.
- **Tech comfort**: High. Understands APIs, folder structures, and is comfortable reading documentation.

---

**Persona 3 — "The Everyday Language Learner"**
*Example: A student or professional learning a language, comfortable with apps but not with APIs or config files*

- **Context**: Discovers the app, wants to start reading immediately. Has never heard of Syncthing, API keys, or vault folders.
- **Current workflow**: May already use apps like Duolingo or a simple e-reader, looking for something more immersive without the complexity of a developer tool.
- **Frustrations with existing tools**: LingQ requires an account and internet connection; other tools don't offer integrated vocabulary management.
- **What success looks like**: Opens the app, imports a book, starts reading, clicks on unknown words, gets instant dictionary translations, saves vocabulary — all without touching a single setting.
- **Advanced features**: If they ever want multi-device sync or AI translation, a guided UI ("Connect your AI in 3 steps") walks them through it progressively.
- **Tech comfort**: Low to medium. Comfortable with standard app interactions (file picker, settings screens) but not with CLIs, config files, or API keys.
- **Design implication**: The app must work 100% out of the box with smart defaults — vault auto-created in a standard location, dictionary services active without configuration, advanced features discoverable but never intrusive (**progressive disclosure**).

### Secondary Users

**Persona 4 — "The Open Source Contributor / Developer"**
*Example: A developer who also learns languages and wants to extend or fork the app*

- **Context**: Discovers LingQ Open on GitHub, finds the architecture clean and well-documented, wants to contribute a feature or adapt it for a specific use case (e.g., adding a new dictionary provider, supporting a new file format, adding a language-specific feature).
- **Needs**: Clear architecture documentation, well-structured codebase, contributor guidelines, AI-friendly project context files.
- **What success looks like**: Can understand the codebase quickly, submit a PR, or fork the project confidently without needing to reverse-engineer undocumented decisions.

### User Journey

**Primary User — First Use**
1. **Discovery**: Finds the project on GitHub or a community forum (e.g., r/languagelearning, Hacker News)
2. **Access**: Opens the web app or installs the Android app — no server setup required
3. **Vault setup**: App auto-creates a vault in a standard location — user can optionally relocate it to a cloud-synced folder
4. **First import**: Opens a file picker, selects an EPUB or PDF — book appears in their library
5. **First reading session**: Opens the book, clicks an unknown word, gets an instant dictionary translation — the "aha" moment
6. **Vocabulary save**: Saves the word with its translation and context — it enters the vocabulary list
7. **Optional AI setup**: Enters an API key in settings to unlock phrase-level AI translation
8. **Daily habit**: Short daily reading sessions build vocabulary progressively over time
9. **Multi-device**: Relocates the vault folder to a cloud-synced directory — same progress on web and Android

---

## Success Metrics

### Personal Success (Primary)

The product is successful when Damien uses it as his daily language learning tool, replacing LingQ with equal or greater satisfaction. Key indicators:

- **Daily usage**: The app is opened for reading sessions as consistently as LingQ would be
- **Session continuity**: "Resume where I left off" works reliably across sessions and devices
- **Translation fluency**: Word/phrase lookup feels fast and natural — no friction breaking reading flow

### MVP Definition (Done = Shippable)

The MVP is complete when all of the following work reliably end-to-end:

1. **Book import**: EPUB, PDF, and plain text files can be imported and appear in the library
2. **Library & resume**: Main screen shows imported books; opening a book resumes at the last reading position
3. **Immersive reader**: Text displays with word coloring by mastery level; click/select a word or phrase opens the translation panel with dictionary services (and AI if configured)
4. **TTS audio**: Pronunciation playback works on both web and Android without additional setup
5. **Vocabulary management**: Words/phrases can be saved with translation, notes, and confidence level (1→4 + known)
6. **Vault-based sync**: App data is stored in a portable folder/file structure; relocating this to a cloud-synced directory enables multi-device sync

### Technical Quality Metrics (Educational Goals)

This project is also a validation of AI-assisted development with BMAD. Success on this axis means:

- **Clean, testable code**: Unit tests and E2E tests implemented where applicable (coverage adapted to the chosen tech stack)
- **Living documentation**: User-facing docs and developer docs kept up to date throughout development — not written after the fact
- **Quality CI pipeline**: Automated checks (lint, tests, build) run on every commit; no merge without green pipeline
- **Parallel agent development**: Validate that BMAD agents can develop independent features concurrently without conflicts, following defined architecture and conventions
- **Beads integration**: [`bd` (beads)](https://github.com/steveyegge/beads) is used as the primary issue and session tracking tool for all agent work — validate its effectiveness for managing multi-agent development sessions

### Community / Open Source (Secondary)

Not a primary success criterion at this stage. The product must be personally satisfying before community adoption becomes a goal. That said, the codebase should be forkable and contributable from day one (clean architecture, good docs, contributor guidelines).

### Business Objectives

N/A — this is a personal open-source project with no commercial objectives.

---

## MVP Scope

### Core Features

| # | Feature | Notes |
|---|---------|-------|
| 1 | **Book import** | EPUB, PDF, plain text |
| 2 | **Library with resume** | Main screen shows imported books; opens at last reading position |
| 3 | **Immersive reader** | Text color-coded by word mastery level (unknown → known) |
| 4 | **Translation panel** | Click/select word or phrase → dictionary services (WordReference, Reverso, Google Translate, Linguee) |
| 5 | **AI translation (optional)** | Phrase/paragraph translation via user's BYOK LLM; gracefully absent if not configured |
| 6 | **TTS audio** | Pronunciation playback for selected word or phrase (Web Speech API on web, Android TTS on Android) |
| 7 | **Vocabulary management** | Save word/phrase with translation, personal notes, confidence level (1→4 + known) |
| 8 | **Vault-based sync** | All data stored in a portable folder/file structure; multi-device sync via user's chosen third-party service |
| 9 | **Platforms** | Web (browser) + Android |

### Out of Scope for MVP

- **Spaced repetition review (Anki-like)** — deferred to v2; vocabulary is saved but review sessions come later
- **Audio content** — no podcast or audiobook support (TTS for word/phrase only is in scope)
- **Video content** — deferred
- **Import from online services** — no YouTube, Netflix, or web scraping integrations
- **iOS** — deferred to post-MVP
- **Windows / Linux / macOS desktop apps** — not targeted
- **Online user accounts / proprietary cloud** — local-first only
- **Direct cloud storage API integration** — (Google Drive API, Dropbox API, etc.) — vault folder approach is sufficient
- **LLM OAuth** — API key input sufficient for v1
- **Community / social features** — deferred
- **Advanced learning statistics** (graphs, heatmaps, streaks dashboard) — deferred to v2

### Security Constraints

- **API key storage**: Keys must be stored using OS-level secure storage (Android Keystore on Android; encrypted local storage on web)
- **Vault exclusion**: API keys are never written to the sync vault — they are device-local only and must be reconfigured on each new device
- **No plaintext secrets**: API keys must never appear in log files, error messages, or debug output
- **HTTPS only**: All calls to external services (LLM APIs, dictionary services, TTS) use HTTPS exclusively
- **Input masking**: API key input fields mask the value by default

### MVP Success Criteria

The MVP is considered successful when:
- Damien completes a full reading session (import → read → save words) without switching back to LingQ
- Dictionary translation panel works reliably on both web and Android
- Vault folder can be relocated to a cloud-synced directory and data appears correctly on a second device
- TTS playback works on both platforms without additional setup

### Future Vision

| Version | Focus |
|---------|-------|
| **v2** | Spaced repetition review (Anki-like) + learning progress statistics |
| **v3** | iOS support |
| **v4** | Audio content support (podcasts, audiobooks) with text/audio sync |
| **v5** | Video support + import from online services (YouTube, etc.) |
| **v6+** | Community features, shared vocabulary lists, collaborative content |
