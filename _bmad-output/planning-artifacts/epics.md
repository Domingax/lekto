---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
status: 'complete'
inputDocuments:
  - '_bmad-output/planning-artifacts/prd.md'
  - '_bmad-output/planning-artifacts/architecture.md'
  - '_bmad-output/planning-artifacts/ux-design-specification.md'
---

# letko - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for letko, decomposing the requirements from the PRD, UX Design, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: User can import an EPUB file from their device into the library
FR2: User can import a PDF file from their device into the library
FR3: User can import a plain text file from their device into the library
FR4: The system tokenizes imported content into words, punctuation, and whitespace at import time (not at read time)
FR5: The system assigns a normalized word key to each unique word for vocabulary tracking
FR6: User can view feedback during import processing
FR7: User can assign a language to the imported content
FR8: User can view all imported books in a library with title, cover, and reading progress
FR9: User can open a book and resume reading from the last saved position
FR10: User can navigate between chapters or sections within a book
FR11: User can delete a book from the library
FR12: User can read content with words visually differentiated by mastery level
FR13: User can update the mastery level of a word directly from the reader
FR14: User can select a single word to open the translation panel
FR15: User can select a multi-word phrase to open the translation panel
FR16: User can navigate between pages via swipe gesture or navigation controls
FR17: Reading position is automatically saved continuously as the user reads
FR18: User can customize reading display (font family, text size, light/dark/sepia theme)
FR19: User can view dictionary lookups for a selected word via integrated dictionary services (WordReference, Reverso, Google Translate, Linguee) without leaving the reading context
FR20: User can request AI translation for a selected phrase when an AI provider is configured
FR21: The translation panel displays a non-blocking prompt to configure AI when no provider is set and a phrase is selected
FR22: User can hear TTS audio pronunciation for a selected word or phrase
FR23: The translation panel degrades gracefully when offline
FR24: User can save a word or phrase from the translation panel to their vocabulary list
FR25: User can attach a translation to a saved vocabulary entry
FR26: User can add personal notes to a saved vocabulary entry
FR27: User can set a confidence level (1–4 + known) on a saved vocabulary entry
FR28: User can view the sentence context in which a word was originally saved
FR29: User can view, update, and delete entries in their vocabulary list
FR30: The system automatically creates a vault in a default local location on first launch
FR31: User can relocate the vault to a different folder (e.g., a cloud-synced directory)
FR32: All reading data (books, vocabulary, progress) is stored in the vault as portable files
FR33: User can point the app to an existing vault and restore all reading history and vocabulary
FR34: The system detects and reflects vault contents on app launch
FR35: User can configure a BYOK AI provider (OpenAI, Anthropic, Ollama, or equivalent)
FR36: User can enter, save, and update an API key for their chosen AI provider
FR37: API keys are stored using OS-level secure storage and never written to the vault
FR38: The app is fully functional without any AI provider configured
FR39: User can configure their target language and native language
FR40: User can access and manage all app settings from a dedicated settings area (vault location, AI provider, reading preferences, language settings)
FR41: User can navigate all app features using keyboard only (desktop)
FR42: All interactive elements expose accessible labels for screen readers (desktop: ARIA-compatible, Android: TalkBack)
FR43: Word mastery coloring is supplemented with non-color visual indicators for colorblind users
FR44: The reader respects the system font size setting on Android

### NonFunctional Requirements

NFR1: Translation panel opens within 200ms of word or phrase selection
NFR2: Page turn and scroll animations complete within 100ms
NFR3: EPUB import for a 300-page book completes in under 15 seconds on modern hardware
NFR4: App initial load completes in under 3 seconds
NFR5: Vault state detected and reflected within 1 second of app launch
NFR6: Reading performance does not degrade as vocabulary list grows (tested up to 10,000 entries)
NFR7: API keys stored exclusively using OS-level secure storage (Android Keystore on Android; Tauri Stronghold on desktop) — never written to vault or disk
NFR8: API keys never appear in log output, error messages, debug panels, or crash reports
NFR9: All calls to external services use HTTPS exclusively
NFR10: API key input fields mask the value by default
NFR11: Vault files contain no secrets — any vault can be shared or inspected without exposing credentials
NFR12: No telemetry, analytics, or usage data collected or transmitted
NFR13: No data sent to any remote server except user-initiated calls (dictionary lookups, BYOK AI translation, TTS playback)
NFR14: All user data remains on the user's device inside the vault
NFR15: All core reading features function with no network connection (fully offline by design)
NFR16: Desktop and Android app conforms to WCAG 2.1 Level AA
NFR17: All interactive elements keyboard-navigable on desktop (Tab, Enter, Space, Escape)
NFR18: All interactive elements have accessible labels for screen readers (ARIA on desktop, content descriptions on Android/TalkBack)
NFR19: Word mastery colors supplemented with non-color visual indicators (underline style variation per level) for colorblind users
NFR20: Minimum touch target size of 48×48dp on Android throughout
NFR21: Reader respects system font size setting on Android
NFR22: Dictionary service failures handled gracefully — panel remains open with clear non-blocking message; app does not crash
NFR23: BYOK AI failures (invalid key, timeout, no network) display a clear inline error without disrupting the reading session
NFR24: TTS unavailability handled gracefully — audio button hidden or disabled with no error thrown
NFR25: Lekto must not corrupt vault data under any circumstance; sync conflict resolution is delegated to the user's sync service
NFR26: Core business logic (import pipeline, tokenization, vocabulary management, vault operations) has unit test coverage
NFR27: E2E tests cover the primary user journey (import → read → lookup → save) on Android and Desktop
NFR28: All commits must pass CI pipeline (lint → unit tests → build) before merge
NFR29: Public module APIs documented; architectural decisions recorded in project documentation

### Additional Requirements

**From Architecture:**
- **Starter Template (Epic 1 Story 1):** Vite react-ts + Capacitor manual integration is the selected starter. Initialization: `npm create vite@latest letko -- --template react-ts && npx cap init letko com.letko.app --web-dir dist && npx cap add android && npx shadcn@latest init`. This is the first implementation story.
- **Code Architecture:** Feature-Sliced Design (FSD) with layers: app / pages / widgets / features / entities / shared. Unidirectional imports only (never upward).
- **Data persistence:** Single SQLite file `lekto.db` in the vault, managed via Drizzle ORM. Tables: books, sections, tokens, vocabulary, reading_progress. Drizzle migrate() runs automatically on startup.
- **State management:** SQLite (source of truth) + Zustand (UI/runtime state). One Zustand store per domain. Never query SQLite inside React components — always read from Zustand.
- **Platform adapters:** All platform-specific APIs (filesystem, TTS, secure storage, file picker) routed through `shared/platform/` adapters. No direct platform calls in business logic. Pattern: interface + `*.android.ts` + `*.desktop.ts` + `index.ts` — platform selected via `isTauri()` at runtime.
- **Error handling:** Result<T> type via `neverthrow` for all fallible async operations. No throw across module boundaries for expected failures.
- **Async patterns:** Always async/await; no .then()/.catch() chains in feature code.
- **CI/CD:** 3 GitHub Actions pipelines (PR gate, push to main + deploy, release tag + Android build). Conventional Commits + commitlint + husky + commitizen + release-please.
- **Testing stack:** Vitest + React Testing Library (unit), Playwright (E2E desktop via Tauri WebDriver), Maestro (E2E Android — local only for MVP).
- **Deployment:** Tauri AppImage (Linux) + NSIS installer (Windows), GitHub Releases + F-Droid (Android APK).

**From UX Design:**
- **Responsive breakpoints:** Mobile (<768px), Tablet (768–1024px), Desktop (>1024px). Mobile-first Tailwind approach.
- **Navigation structure:** Bottom nav (4 items: Library, Import, Vocabulary, Settings) on mobile/tablet; persistent sidebar on desktop.
- **Translation panel layout:** Bottom sheet (mobile), fixed side panel (tablet/desktop). Slide-up animation 200ms ease-out, hardware accelerated.
- **Library layout:** List with "Continue Reading" hero block on mobile; 2-column grid on tablet; wide list in sidebar layout on desktop.
- **VaultSetupScreen:** One-time first-launch screen with two options — "Create new vault" (primary) and "Open existing vault" (secondary). Default path shown with modify option before confirming.
- **Design system:** shadcn/ui + Tailwind CSS v4 + Lucide Icons. No external component libraries.
- **Reading themes:** Light (default), Sepia, Dark — switchable from reading settings. Immediate live preview (no Save button).
- **Mastery level color system:** 5 levels — Unknown (blue), Familiar (yellow), Recognized (light orange), Mastered (light green), Known (no highlight). Each level also has a distinct underline style for colorblind users.
- **Reader typography:** User-configurable font family (Georgia default), font size (14–26px slider), line height (1.4–2.0 slider), margins (Narrow/Medium/Wide). Controlled via CSS custom properties.
- **Feedback patterns:** Word color updates immediately in reader on save (no toast). Inline errors inside panel. Inline progress bar for import.

### FR Coverage Map

FR1: Epic 3 — Import EPUB file into library
FR2: Epic 3 — Import PDF file into library
FR3: Epic 3 — Import plain text file into library
FR4: Epic 3 — Tokenize content at import time
FR5: Epic 3 — Assign normalized word key per unique word
FR6: Epic 3 — Import progress feedback
FR7: Epic 3 — Assign language to imported content
FR8: Epic 3 — Library view with title, cover, and reading progress
FR9: Epic 3 — Open book and resume from last position
FR10: Epic 3 — Navigate between chapters/sections
FR11: Epic 3 — Delete a book from library
FR12: Epic 4 — Read with words colored by mastery level
FR13: Epic 4 — Update mastery level of a word from reader
FR14: Epic 4 — Select single word to open translation panel
FR15: Epic 4 — Select multi-word phrase to open translation panel
FR16: Epic 3 — Navigate between pages via swipe or navigation controls
FR17: Epic 4 — Reading position auto-saved continuously
FR18: Epic 4 — Customize reading display (font, size, theme)
FR19: Epic 5 — Dictionary lookup in-app (WordReference, Reverso, Google Translate, Linguee)
FR20: Epic 6 — AI translation for selected phrase when provider configured
FR21: Epic 5 — Non-blocking AI prompt when no provider set and phrase selected
FR22: Epic 5 — TTS audio pronunciation for selected word or phrase
FR23: Epic 5 — Translation panel degrades gracefully when offline
FR24: Epic 5 — Save word/phrase from translation panel to vocabulary list
FR25: Epic 5 — Attach translation to saved vocabulary entry
FR26: Epic 5 — Add personal notes to saved vocabulary entry
FR27: Epic 5 — Set confidence level (1–4 + known) on saved vocabulary entry
FR28: Epic 5 — View sentence context in which a word was originally saved
FR29: Epic 5 — View, update, and delete entries in vocabulary list
FR30: Epic 2 — System auto-creates vault on first launch
FR31: Epic 2 — User can relocate vault to different folder
FR32: Epic 2 — All reading data stored in vault as portable files
FR33: Epic 2 — User can point app to existing vault and restore data
FR34: Epic 2 — System detects and reflects vault contents on app launch
FR35: Epic 6 — Configure BYOK AI provider (OpenAI, Anthropic, Ollama)
FR36: Epic 6 — Enter, save, and update API key for chosen AI provider
FR37: Epic 6 — API keys stored in OS-level secure storage, never in vault
FR38: Epic 6 — App fully functional without any AI provider configured
FR39: Epic 7 — Configure target language and native language
FR40: Epic 7 — Access and manage all app settings from dedicated settings area
FR41: Epic 7 — Navigate all app features using keyboard only (desktop)
FR42: Epic 7 — All interactive elements expose accessible labels (ARIA / TalkBack)
FR43: Epic 7 — Word mastery coloring supplemented with non-color visual indicators
FR44: Epic 7 — Reader respects system font size setting on Android

## Epic List

### Epic 1: Project Foundation & Developer Setup
Establish the complete project scaffolding, architecture, tooling, and CI/CD pipelines so that every subsequent epic can be developed and shipped with confidence. After this epic, a developer can clone the repo, run the app locally in under 10 minutes, and all CI pipelines are active.
**FRs covered:** None (greenfield infrastructure — enables all subsequent epics)

### Epic 2: Vault & First Launch
Users have a persistent, portable local data store from day one. After this epic, a user launching the app for the first time is guided through vault creation (or opening an existing vault), and all subsequent reading data is stored reliably in that vault.
**FRs covered:** FR30, FR31, FR32, FR33, FR34

### Epic 3: Book Import & Library
Users can import their books and manage their reading library. After this epic, a user can import an EPUB, PDF, or plain text file, see it in their library with progress, open it, navigate by chapter and by page swipe, and delete books they no longer want.
**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR8, FR9, FR10, FR11, FR16

### Epic 4: Immersive Reading Experience
Users can read with the full immersive experience: words colored by mastery level, word/phrase selection to trigger the translation panel, automatic reading position persistence, and reading customization (font, size, theme).
**FRs covered:** FR12, FR13, FR14, FR15, FR17, FR18

### Epic 5: Translation & Vocabulary
Users can look up words while reading and build their vocabulary. After this epic, tapping a word opens a translation panel with dictionary services, TTS, and graceful offline degradation; users can save words with translation, notes, and confidence levels, and manage their full vocabulary list.
**FRs covered:** FR19, FR21, FR22, FR23, FR24, FR25, FR26, FR27, FR28, FR29

### Epic 6: BYOK AI Translation
Users can connect their own AI subscription to unlock phrase-level translation. After this epic, users can configure an AI provider (OpenAI, Anthropic, Ollama), store their API key securely, and get contextual AI translations for multi-word selections — with graceful fallback if no provider is configured.
**FRs covered:** FR20, FR35, FR36, FR37, FR38

### Epic 7: Settings & Accessibility
All app settings are accessible and all accessibility requirements are met. After this epic, users can configure language settings and manage all preferences from a dedicated settings area; the app is fully keyboard-navigable, screen-reader-compatible, colorblind-accessible, and respects Android system font size.
**FRs covered:** FR39, FR40, FR41, FR42, FR43, FR44

### Epic 8: Tauri Desktop Integration
The app runs natively on Linux and Windows as a Tauri application sharing the same React/Capacitor codebase. After this epic, a developer can build and run the desktop app locally, the DB layer uses the Tauri SQL plugin with direct vault path access (no sync needed), all platform adapters have a `*.desktop.ts` implementation, and CI/CD produces AppImage and NSIS installers on release.
**Priority:** Immediate — must precede any feature story claiming Desktop support.
**FRs covered:** FR30 (desktop vault creation), FR31 (desktop vault relocation), FR37 (Tauri Stronghold), FR41 (desktop keyboard navigation)

---

## Known Gaps (V2 Backlog)

Features identified during epic creation that are intentionally deferred. See PRD § Post-MVP Roadmap for full rationale.

| Feature | Why deferred | V2 priority |
|---------|-------------|-------------|
| **Phrase/expression highlighting in reader** — saved multi-word vocabulary entries appear as colored spans in the reader, matching their mastery level | N-gram matching at render-time, cross-layer impact (tokenization model + reader render engine + vocabulary store), performance risk at 10k entries (NFR6) | #1 |

---

## Epic 1: Project Foundation & Developer Setup

Establish the complete project scaffolding, architecture, tooling, and CI/CD pipelines so that every subsequent epic can be developed and shipped with confidence. After this epic, a developer can clone the repo, run the app locally in under 10 minutes, and all CI pipelines are active.

### Story 1.1: Project Scaffold & Core Tooling

As a developer,
I want a fully initialized project with the correct stack and FSD structure,
So that I can start building features immediately without any setup friction.

**Acceptance Criteria:**

**Given** the repository is cloned on a fresh machine
**When** the developer runs `npm install && npm run dev`
**Then** the Vite dev server starts and the app loads in the browser without errors

**Given** the project is initialized
**When** a developer inspects the source structure
**Then** the FSD directory tree exists (`src/app`, `src/pages`, `src/widgets`, `src/features`, `src/entities`, `src/shared/ui`, `src/shared/db`, `src/shared/platform`, `src/shared/lib`) each with an `index.ts` barrel file

**Given** the project is initialized
**When** TypeScript compilation runs (`npm run typecheck`)
**Then** it completes with zero errors in strict mode

**Given** the project is initialized
**When** the linter runs (`npm run lint`)
**Then** it completes with zero errors or warnings

**Given** the project is initialized
**When** Capacitor CLI runs `npx cap sync`
**Then** the Android platform is present and syncs without errors

**Given** shadcn/ui is initialized
**When** a developer imports a shadcn component (e.g. `Button`)
**Then** it renders correctly in the browser with Tailwind styles applied

---

### Story 1.2: Database Infrastructure

As a developer,
I want a SQLite database layer with Drizzle ORM and automatic migrations,
So that all subsequent features can persist data reliably on both Android and Desktop without manual setup.

**Acceptance Criteria:**

**Given** the app starts on Desktop (Tauri)
**When** `main.tsx` runs
**Then** Drizzle's `migrate()` executes via the Tauri SQL plugin and `lekto.db` is created directly in the vault folder path without errors

**Given** the app starts on Android
**When** `main.tsx` runs
**Then** the same migration runner executes via the Capacitor SQLite adapter and `lekto.db` is created without errors

**Given** a migration file exists in `src/shared/db/migrations/`
**When** the app restarts
**Then** the migration is applied exactly once and idempotent re-runs produce no errors

**Given** the Drizzle schema defines a column in snake_case (e.g. `word_key`)
**When** the TypeScript type is generated via `drizzle-kit generate`
**Then** the resulting TypeScript type uses camelCase (`wordKey`) automatically

**Given** the database infrastructure is in place
**When** a developer runs `npm run db:generate`
**Then** a new migration file is produced in `src/shared/db/migrations/`

---

### Story 1.3: AGENTS.md & Developer Reference

As an AI agent or new contributor,
I want a single authoritative reference file at the root of the repository,
So that I can understand all project conventions, architecture rules, and workflow protocols without reading multiple documents.

**Acceptance Criteria:**

**Given** `AGENTS.md` exists at the repository root
**When** an AI agent starts a new session on this project
**Then** reading `AGENTS.md` alone is sufficient to understand: FSD layer rules, naming conventions, error handling pattern, state management pattern, platform adapter pattern, commit conventions, and the Beads workflow

**Given** the FSD section of `AGENTS.md`
**When** an agent reads it
**Then** it finds: the layer hierarchy (app → pages → widgets → features → entities → shared), the unidirectional import rule with a correct and an incorrect example, and the barrel file convention

**Given** the Beads workflow section of `AGENTS.md`
**When** an agent starts a new session
**Then** the documented protocol instructs: (1) run `bd ready --json` to get the prioritized work queue, (2) run `bd update <id> --claim` before starting a task, (3) run `bd create "title"` for any work discovered mid-session

**Given** the Beads workflow section of `AGENTS.md`
**When** an agent ends a session ("Land the Plane")
**Then** the documented protocol instructs: (1) close completed issues, (2) file any remaining discovered work, (3) run `bd sync`, (4) `git push` — and notes that unpushed work blocks other agents

**Given** the TDD section of `AGENTS.md`
**When** an agent begins implementing any feature code
**Then** the documented protocol instructs: (1) write a failing test first, (2) write the minimum code to make it pass, (3) refactor — and explicitly prohibits writing implementation code before a failing test exists

**Given** the TDD section of `AGENTS.md`
**When** an agent reads it
**Then** it finds: the Red→Green→Refactor cycle, the rule that no production code is written without a prior failing test, and the co-location convention for test files (`*.test.ts` next to the source file)

**Given** the error handling section of `AGENTS.md`
**When** an agent reads it
**Then** it finds: the `Result<T, string>` pattern with a correct example, the rule against `throw` for expected failures, and the rule against `.then()/.catch()` chains in feature code

**Given** the state management section of `AGENTS.md`
**When** an agent reads it
**Then** it finds: the SQLite → Zustand hydration pattern, the optimistic update rule, the one-store-per-domain rule, and the prohibition against querying SQLite inside React components

---

### Story 1.4: Platform Adapters & Error Handling Foundation

As a developer,
I want a platform abstraction layer and a unified error handling pattern,
So that all features can be written once and run correctly on both Android and Desktop without platform-specific branching in business logic.

**Acceptance Criteria:**

**Given** the platform adapters are implemented
**When** business logic calls `filesystemAdapter.readFile(path)`
**Then** the correct Android or Desktop implementation is invoked based on `isTauri()` / `Capacitor.isNativePlatform()` — never a direct Capacitor or Tauri API call from feature code

**Given** the four adapter interfaces exist (`filesystem`, `tts`, `secureStorage`, `filePicker`)
**When** a developer adds a new platform capability
**Then** they follow the established pattern: interface + `*.android.ts` + `*.desktop.ts` + `index.ts` runtime selector (via `isTauri()`) in `src/shared/platform/<capability>/`

**Given** `neverthrow` is installed
**When** a fallible async operation is written in feature code
**Then** it returns `Promise<Result<T, string>>` — never throws for expected failure modes

**Given** a platform adapter call fails (e.g. file not found)
**When** the result is consumed in feature code
**Then** the TypeScript compiler enforces handling both `ok` and `err` branches — the error case cannot be silently ignored

**Given** the adapters are in place
**When** unit tests run for a feature that uses a platform adapter
**Then** the adapter can be swapped with a mock via dependency injection without modifying feature code

---

### Story 1.5: CI/CD & Quality Pipelines

As a developer,
I want automated CI/CD pipelines enforcing quality gates on every PR and deploying automatically on release,
So that no broken code reaches main and releases are fully automated.

**Acceptance Criteria:**

**Given** a pull request is opened
**When** the PR pipeline runs (`.github/workflows/pr.yml`)
**Then** it executes in sequence: lint + typecheck → unit tests (Vitest) with coverage → build → E2E desktop (Playwright via Tauri WebDriver) → CodeQL → SonarCloud — and blocks merge if any step fails

**Given** a commit is pushed to `main`
**When** the main pipeline runs (`.github/workflows/main.yml`)
**Then** it creates or updates a release PR via release-please

**Given** a release tag `v*` is pushed (by merging the release-please PR)
**When** the release pipeline runs (`.github/workflows/release.yml`)
**Then** it builds the Tauri desktop app (AppImage for Linux, NSIS installer for Windows), builds and signs the Android APK using GitHub Secrets, attaches both to a GitHub Release, and triggers F-Droid pickup

**Given** a developer writes a commit message that does not follow Conventional Commits format
**When** they run `git commit`
**Then** the commitlint husky hook rejects the commit with a clear error message before it is created

**Given** Vitest is configured
**When** a developer runs `npm run test`
**Then** the test suite runs and exits with a non-zero code if any test fails or coverage falls below threshold

**Given** Playwright is configured
**When** a developer runs `npm run test:e2e`
**Then** the E2E suite runs against the built desktop app via Tauri WebDriver and reports pass/fail per spec file

---

## Epic 2: Vault & First Launch

Users have a persistent, portable local data store from day one. After this epic, a user launching the app for the first time is guided through vault creation (or opening an existing vault), and all subsequent reading data is stored reliably in that vault.

### Story 2.1: Vault Setup Screen & Create New Vault

As a first-time user,
I want to be guided through creating a vault on first launch,
So that my reading data has a persistent local home without any manual configuration required.

**Acceptance Criteria:**

**Given** the app has never been launched before (no vault path stored)
**When** the app loads
**Then** the VaultSetupScreen is displayed — not the library — with two options: "Create new vault" (primary button) and "Open existing vault" (secondary button)

**Given** the app has a vault path already configured
**When** the app loads
**Then** the VaultSetupScreen is skipped and the library is displayed directly

**Given** the user is on the VaultSetupScreen and selects "Create new vault"
**When** the create flow opens
**Then** the default vault path is displayed with a "Modify" option before confirmation — no file picker is opened automatically

**Given** the user confirms the default vault path
**When** vault creation runs
**Then** the vault folder is created with a `books/` subdirectory, `lekto.db` is initialized via Drizzle migrations, and the vault path is persisted for future sessions

**Given** the user taps "Modify" before confirming
**When** the file picker opens
**Then** the user can select any folder as the vault location, after which the selected path replaces the default in the confirmation view

**Given** vault creation completes successfully
**When** the app transitions
**Then** the VaultSetupScreen is dismissed and the empty library is displayed — the setup screen is never shown again on subsequent launches

**Given** the app runs on Desktop (Tauri)
**When** vault creation runs
**Then** the vault folder is created with a `books/` subdirectory, `lekto.db` is initialized at the chosen OS path via Drizzle + Tauri SQL plugin, and the vault path is persisted for future sessions

**Given** the app runs on Android
**When** the user taps "Modify" to choose a vault location
**Then** the SAF file picker opens and the selected folder path is stored as a persistent `content://` URI

---

### Story 2.2: Open Existing Vault

As a returning user or multi-device user,
I want to point the app to an existing vault folder,
So that all my previous reading history and vocabulary are immediately restored.

**Acceptance Criteria:**

**Given** the user is on the VaultSetupScreen and selects "Open existing vault"
**When** the file picker opens
**Then** the user can select any folder from their device or cloud-synced directory

**Given** the user selects a folder that contains a valid `lekto.db`
**When** vault detection runs
**Then** the vault is accepted, the path is persisted, and the app transitions to the library displaying all previously imported books and vocabulary

**Given** the user selects a folder that does not contain a `lekto.db`
**When** vault detection runs
**Then** an inline error message is shown explaining the folder is not a valid vault — the VaultSetupScreen remains open and no data is modified

**Given** the vault is loaded from an existing `lekto.db`
**When** Drizzle migrations run on startup
**Then** any pending migrations are applied without data loss and the app proceeds normally

**Given** the user opens an existing vault on a second device
**When** the library loads
**Then** all books, vocabulary entries, and reading progress from the vault are reflected accurately

---

### Story 2.3: Vault Relocation from Settings

As a user,
I want to change my vault location from the settings at any time,
So that I can move my data to a cloud-synced folder or a new path without losing anything.

**Acceptance Criteria:**

**Given** the user navigates to Settings → Vault
**When** the vault settings screen loads
**Then** the current vault path is displayed with a "Change location" button

**Given** the user taps "Change location"
**When** the file picker opens and a new folder is selected
**Then** the app detects whether the new folder contains an existing `lekto.db` and presents the appropriate option: "Use existing vault data" or "Migrate current data to this location"

**Given** the user confirms migration to a new empty folder
**When** migration runs
**Then** a progress indicator is shown, all vault contents (`books/` and `lekto.db`) are copied to the new location, and the original vault remains untouched until the copy is confirmed successful

**Given** migration completes successfully
**When** the app switches to the new vault
**Then** the library reflects the new vault contents, the new path is persisted, and the original vault folder is left intact (not deleted)

**Given** migration fails at any point
**When** the error occurs
**Then** an inline error message is shown, the original vault remains active and unmodified, and no data is lost

**Given** the user selects a folder containing an existing `lekto.db` and confirms "Use existing vault data"
**When** the vault switches
**Then** the app loads the selected vault directly without copying, and the library reflects its contents

---

## Epic 3: Book Import & Library

Users can import their books and manage their reading library. After this epic, a user can import an EPUB, PDF, or plain text file, see it in their library with progress, open it, navigate by chapter and by page swipe, and delete books they no longer want.

### Story 3.1: EPUB Import & Tokenization

As a reader,
I want to import an EPUB file from my device,
So that I can read it in Lekto with my vocabulary tracked word by word.

**Acceptance Criteria:**

**Given** the user taps the Import action in the bottom navigation
**When** the file picker opens
**Then** only EPUB files are selectable (filtered by MIME type / extension)

**Given** the user selects a valid EPUB file
**When** import processing begins
**Then** an inline progress indicator is shown in the library — the user is not blocked from using the rest of the app

**Given** import is running
**When** the EPUB is parsed via epubjs
**Then** all chapters and sections are extracted, tokenized into words, punctuation, and whitespace, and stored in the `sections` and `tokens` SQLite tables — tokenization happens entirely at import time, never at read time

**Given** the tokenization pipeline runs
**When** a unique word is encountered
**Then** a normalized word key is assigned (lowercased, diacritics preserved) and stored in the `tokens` table — the same word appearing in multiple chapters shares one `wordKey`

**Given** import completes
**When** the language assignment step runs
**Then** the user is prompted to confirm or change the detected language for the imported book before it appears in the library

**Given** the user confirms the language
**When** the book is saved
**Then** it appears in the library with its title, extracted cover image (or a placeholder if none), and 0% reading progress

**Given** an invalid or corrupted EPUB is selected
**When** the parser fails
**Then** an inline error message is shown in the library — no partial data is written to SQLite and the import progress indicator disappears cleanly

---

### Story 3.2: PDF & Plain Text Import

As a reader,
I want to import a PDF or plain text file from my device,
So that I can read any of my documents in Lekto with vocabulary tracking.

**Acceptance Criteria:**

**Given** the user opens the file picker
**When** the picker is displayed
**Then** PDF and plain text files (.txt) are selectable in addition to EPUB

**Given** the user selects a valid PDF file
**When** the PDF is processed via PDF.js
**Then** the text content layer is extracted, a transparent word-level span overlay is generated using character positions, and the content is tokenized and stored identically to EPUB (same `sections` and `tokens` table structure)

**Given** a PDF with embedded text is imported
**When** tokenization runs
**Then** word boundaries are correctly identified and each word receives a normalized `wordKey`

**Given** the user selects a plain text file
**When** import runs
**Then** the raw text is split into a single section, tokenized with the same Latin regex pipeline as EPUB, and stored in SQLite

**Given** a PDF that contains only scanned images (no embedded text layer)
**When** PDF.js finds no text content
**Then** an inline error message informs the user that the PDF has no selectable text — the import is aborted cleanly with no data written

**Given** a PDF or TXT import completes
**When** the language prompt appears
**Then** the user confirms or changes the language, after which the book appears in the library with a placeholder cover and 0% progress

**Given** CJK content is detected in a PDF during import
**When** tokenization runs
**Then** the content is imported without crashing — CJK word boundaries may be approximate at MVP (kuromoji deferred), but the file is not rejected

---

### Story 3.3: Library View & Book Management

As a reader,
I want to see all my imported books in a library and manage them,
So that I can easily find what I'm reading and keep my library organised.

**Acceptance Criteria:**

**Given** the user has imported at least one book
**When** the library screen loads
**Then** each book is displayed as a list item showing: cover image (or placeholder), title, author, language badge, and reading progress percentage

**Given** the user has previously opened a book
**When** the library screen loads
**Then** a "Continue Reading" hero block appears at the top of the library showing the last opened book with its title, chapter, and progress percentage

**Given** the library is empty (no books imported)
**When** the library screen loads
**Then** the empty state message "No books yet — tap Import to add your first book" is displayed and the "Continue Reading" block is hidden

**Given** the user taps a book in the library
**When** the book opens
**Then** the reader navigates directly to the last saved reading position for that book — not the beginning

**Given** the user opens a book for the first time (no saved position)
**When** the reader opens
**Then** the first page of the first chapter is displayed

**Given** the user long-presses or swipes a book item (or uses a context menu)
**When** the delete action is triggered
**Then** a confirmation dialog appears: "Delete [title]? This cannot be undone."

**Given** the user confirms deletion
**When** the book is removed
**Then** its record is deleted from the `books`, `sections`, `tokens`, and `reading_progress` SQLite tables, the source file is removed from `vault/books/`, and the library updates immediately

**Given** the library has more than one language of books
**When** language filter chips are displayed
**Then** tapping a chip filters the list to show only books in that language; tapping again removes the filter

---

### Story 3.4: Basic Reader & In-Book Navigation

As a reader,
I want to read my imported books and navigate between pages and chapters,
So that I can read comfortably before vocabulary features are available.

**Acceptance Criteria:**

**Given** a book is opened in the reader
**When** the reader renders the current section
**Then** the tokenized words are displayed as plain text (no mastery coloring yet) with a readable default layout: Georgia 18px, 1.7 line height, medium margins, light theme

**Given** the reader is open
**When** the user swipes left
**Then** the next page is displayed with an animation completing within 100ms

**Given** the reader is open
**When** the user swipes right
**Then** the previous page is displayed with an animation completing within 100ms

**Given** the reader is open
**When** the user taps the navigation controls (arrows or page indicator)
**Then** the same next/previous page behaviour is triggered as with swipe

**Given** the reader is on the last page of a chapter
**When** the user swipes left
**Then** the first page of the next chapter is displayed seamlessly

**Given** the reader is on the first page of a chapter
**When** the user swipes right
**Then** the last page of the previous chapter is displayed seamlessly

**Given** the reader is open
**When** the user taps the chapter navigation control
**Then** a chapter list is displayed and tapping any chapter jumps directly to its first page

**Given** the user is reading and closes the app
**When** the app is reopened and the book is opened again
**Then** the reader resumes at the exact page and token position where the user left off

**Given** the reader chrome is visible
**When** the user taps outside the text area
**Then** the top bar (back arrow + book title) and bottom bar (chapter + page indicator) toggle visibility — the text remains readable in both states

---

## Epic 4: Immersive Reading Experience

Users can read with the full immersive experience: words colored by mastery level, word/phrase selection to trigger the translation panel, automatic reading position persistence, and reading customization (font, size, theme).

### Story 4.1: Word Mastery Coloring

As a reader,
I want words colored by their mastery level as I read,
So that I can instantly see which words I know and which I still need to learn.

**Acceptance Criteria:**

**Given** a book is open in the reader
**When** the page renders
**Then** every `WordToken` is colored according to its mastery level: Unknown (level 0) → blue highlight, Familiar (level 1) → yellow, Recognized (level 2) → light orange, Mastered (level 3) → light green, Known (level 4) → no highlight (plain text)

**Given** a word has no vocabulary entry in SQLite
**When** it renders in the reader
**Then** it is displayed as Unknown (level 0, blue) by default

**Given** a word's mastery level is updated
**When** the Zustand store is updated
**Then** the word's color updates immediately in the reader without a page reload or re-navigation

**Given** a colorblind user reads the text
**When** words are rendered at each mastery level
**Then** each level has a distinct underline style (e.g. none, solid, dashed, double, wavy) in addition to the color — the mastery level is identifiable without relying on color alone

**Given** the reader renders a page with 500+ word tokens
**When** the page renders
**Then** it completes within 100ms with no visible jank — mastery colors do not degrade rendering performance

**Given** the vocabulary list contains 10,000 entries
**When** the reader renders a page
**Then** mastery level lookup per token is performed from the Zustand store (not SQLite) and remains imperceptible to the user

---

### Story 4.2: Word & Phrase Selection + Mastery Update

As a reader,
I want to tap a word or select a phrase and update its mastery level,
So that I can track my vocabulary progress directly from the reading flow.

**Acceptance Criteria:**

**Given** the reader is displaying a page
**When** the user taps a single `WordToken`
**Then** the word is highlighted with a "selected" visual state and a compact panel slides up from the bottom within 200ms showing the word and a `MasterySelector` (5 buttons: 1, 2, 3, 4, ✓)

**Given** the compact panel is open
**When** the user taps a mastery level button
**Then** the vocabulary entry is created or updated in SQLite (optimistic Zustand update first), the word's color in the reader text updates immediately to reflect the new level, and the panel closes

**Given** the compact panel is open
**When** the user swipes it down or taps outside it
**Then** the panel closes and the reader returns to its previous scroll position with no repositioning

**Given** the user taps a word that is already at level 4 (Known)
**When** the panel opens
**Then** the ✓ button is shown as selected — the user can still change the level to any other value

**Given** the user wants to select a multi-word phrase
**When** they long-press a word and drag to select additional words
**Then** the selected phrase is highlighted and the compact panel opens in phrase mode, showing the phrase text and a `MasterySelector`

**Given** the user navigates to the next page
**When** 5 seconds elapse on the new page without the user navigating back
**Then** all `WordToken`s at level 0 (Unknown) on the page just left are batch-upserted to level 4 (Known) in Zustand and SQLite, and their color in the reader updates to reflect the new level

**Given** the 5-second auto-known timer is running after a forward page turn
**When** the user swipes back to the previous page before the timer expires
**Then** the timer is cancelled and no words are modified — the Unknown words remain at level 0

**Given** the user navigates backward (swipes right to a previous page)
**When** the page changes
**Then** no auto-known timer is started and no word levels are modified

**Given** a word has been explicitly set to level 1, 2, or 3 by the user
**When** the auto-known timer fires on the page it appears on
**Then** that word is not modified — only level 0 (Unknown/unseen) words are auto-promoted to Known

**Given** the compact panel is open on desktop
**When** the user presses Escape
**Then** the panel closes and focus returns to the triggering `WordToken`

**Given** the `WordToken` component renders
**When** inspected by a screen reader
**Then** it exposes `role="button"` and `aria-label="[word], level [n]"` where n is the current mastery level (1–4 or "known")

---

### Story 4.3: Reading Position Auto-Save

As a reader,
I want my reading position saved automatically on every page turn,
So that I never lose my place even if the app closes unexpectedly.

**Acceptance Criteria:**

**Given** the user turns a page in the reader
**When** the new page is displayed
**Then** the current position (book ID, section index, token index) is updated immediately in the Zustand `useReaderStore`

**Given** the Zustand position has been updated after a page turn
**When** 300–500ms elapse without another page turn (debounce)
**Then** the position is written to the `reading_progress` SQLite table via a single upsert

**Given** the user rapidly flips through multiple pages
**When** page turns occur in quick succession
**Then** only one SQLite write is triggered after the last page turn — intermediate positions are not written individually

**Given** the app is sent to the background (Android) or loses window focus (Desktop)
**When** the `appStateChange` or `blur` event fires
**Then** the current Zustand position is flushed to SQLite immediately, bypassing the debounce

**Given** the app is killed without warning (e.g. Android low memory)
**When** the user reopens the app and opens the same book
**Then** the reader resumes at most one page behind the actual last position

**Given** reading progress is written to SQLite
**When** the write completes
**Then** the `reading_progress` row for the book is upserted (not duplicated) with the updated `sectionIndex`, `tokenIndex`, and `updatedAt` timestamp

---

### Story 4.4: Reading Customization

As a reader,
I want to customize how the text is displayed,
So that I can read comfortably for extended sessions in any environment.

**Acceptance Criteria:**

**Given** the user opens reading settings (via reader chrome)
**When** the settings panel is displayed
**Then** it shows: font family selector, font size slider, line height slider, margins selector, and theme switcher

**Given** the user changes the font family
**When** a new font is selected (Georgia, Lora, Open Sans, or system-ui)
**Then** the reader text updates immediately via CSS custom property — no page reload or re-navigation required

**Given** the user moves the font size slider
**When** the value changes (range: 14px–26px)
**Then** the reader text size updates live as the slider moves — the change is visible as a preview behind the settings panel

**Given** the user moves the line height slider
**When** the value changes (range: 1.4–2.0)
**Then** the reader line height updates live

**Given** the user selects a margin option (Narrow, Medium, Wide)
**When** the selection changes
**Then** the reader column width adjusts immediately

**Given** the user switches theme (Light, Sepia, Dark)
**When** the new theme is selected
**Then** the reader background, text color, and all UI elements update immediately — WCAG 2.1 AA contrast ratios are maintained on all three themes

**Given** reading preferences are changed
**When** the user closes and reopens the app
**Then** all customization settings (font, size, line height, margins, theme) are restored exactly as left — they are persisted locally and never reset to defaults

---

## Epic 5: Translation & Vocabulary

Users can look up words while reading and build their vocabulary. After this epic, tapping a word opens a translation panel with dictionary services, TTS, and graceful offline degradation; users can save words with translation, notes, and confidence levels, and manage their full vocabulary list.

### Story 5.1: Full Translation Panel

As a reader,
I want a full translation panel that opens instantly when I tap a word or select a phrase,
So that I can look up definitions and hear pronunciation without leaving my reading context.

**Acceptance Criteria:**

**Given** the user taps a single word in the reader
**When** the `TranslationPanel` opens
**Then** it slides up from the bottom (mobile) or appears as a fixed side panel (tablet/desktop) within 200ms, showing the selected word in the header, a TTS button, a Save button, and dictionary tabs (WordReference, Reverso, Google Translate, Linguee)

**Given** the panel is open in word mode
**When** the user taps a dictionary tab
**Then** the corresponding service loads in an in-app browser (Android: InAppBrowser plugin, Desktop: Tauri shell open or embedded webview) without navigating away from the reader

**Given** the user selects a multi-word phrase in the reader
**When** the `TranslationPanel` opens
**Then** it displays in phrase mode: the selected phrase in the header and a non-blocking inline message — "Connect your AI to translate phrases" with a settings link — no dictionary tabs are shown

**Given** the panel is open
**When** the user taps the TTS button
**Then** the selected word or phrase is read aloud using Android TTS engine (Android) or the system TTS via Tauri shell (Desktop) in the book's language

**Given** TTS is unavailable on the current platform or language
**When** the user would normally see the TTS button
**Then** the button is hidden or disabled — no error is thrown and the panel remains fully functional

**Given** the device is offline
**When** the panel opens
**Then** an inline message is displayed inside the panel ("Dictionary unavailable offline") — the panel remains open, the reader session is not interrupted, and no crash or modal alert occurs

**Given** a dictionary service fails to load (network error, service unavailable)
**When** the tab content fails
**Then** an inline non-blocking message is shown within the tab — the other tabs remain accessible and the reading session continues uninterrupted

**Given** the panel is open
**When** the user swipes it down, taps outside it, or presses Escape
**Then** the panel closes and the reader returns to the exact scroll position with the word deselected

**Given** the panel is open
**When** inspected by a screen reader
**Then** focus is trapped inside the panel, all interactive elements have accessible labels, and closing the panel returns focus to the triggering `WordToken`

---

### Story 5.2: Vocabulary Save

As a reader,
I want to save a word or phrase from the translation panel with context and my own notes,
So that I can build a personal vocabulary list tied to my actual reading.

**Acceptance Criteria:**

**Given** the translation panel is open
**When** the user taps the Save button
**Then** the word or phrase is saved to the `vocabulary` SQLite table with: the `wordKey`, the book's language, the sentence context (the full sentence containing the selected word), and the current timestamp — in a single tap with no form or confirmation dialog

**Given** a word is saved
**When** the save completes
**Then** the word's color in the reader text updates immediately to reflect its current mastery level (optimistic Zustand update) — no toast or modal is shown

**Given** the panel is open after a word has been saved
**When** the user types in the translation field
**Then** the translation is attached to the vocabulary entry and persisted to SQLite

**Given** the panel is open after a word has been saved
**When** the user types in the notes field
**Then** personal notes are attached to the vocabulary entry and persisted to SQLite

**Given** the panel is open after a word has been saved
**When** the user taps a level in the `MasterySelector` (1, 2, 3, 4, or ✓ Known)
**Then** the confidence level is updated in the `vocabulary` table and the word's color in the reader updates immediately

**Given** the user saves a word that already exists in their vocabulary
**When** the save runs
**Then** the existing entry is updated (upsert) rather than duplicated — translation, notes, and confidence level are preserved unless explicitly changed

**Given** a word is saved
**When** the user later views that entry in the vocabulary list
**Then** the sentence context in which the word was originally saved is displayed exactly as it appeared in the source text

---

### Story 5.3: Vocabulary List

As a reader,
I want to view, manage, and edit my saved vocabulary entries,
So that I can review what I've learned and keep my vocabulary list accurate.

**Acceptance Criteria:**

**Given** the user taps the Vocabulary item in the bottom navigation
**When** the vocabulary screen loads
**Then** all saved entries are displayed as a list showing: the word or phrase, its translation (if set), its confidence level indicator, and its language

**Given** the vocabulary list is empty
**When** the screen loads
**Then** the empty state message "No words saved yet — tap a word while reading to save it" is displayed

**Given** the user taps a vocabulary entry
**When** the entry detail view opens
**Then** it shows: the word/phrase, translation, personal notes, confidence level, and the original sentence context in which it was saved

**Given** the user edits the translation field in an entry
**When** the change is saved
**Then** the updated translation is persisted to SQLite and reflected immediately in the list view

**Given** the user edits the notes field in an entry
**When** the change is saved
**Then** the updated notes are persisted to SQLite

**Given** the user changes the confidence level of an entry
**When** the new level is selected
**Then** the `vocabulary` table is updated and the word's mastery color updates the next time it is rendered in the reader

**Given** the user deletes a vocabulary entry
**When** they confirm the deletion
**Then** the entry is removed from SQLite, the list updates immediately, and the word reverts to Unknown (level 0) the next time it appears in the reader

**Given** the vocabulary list contains many entries
**When** the user types in the search bar
**Then** the list filters in real time to show only entries whose word, phrase, or translation matches the search input

---

## Epic 6: BYOK AI Translation

Users can connect their own AI subscription to unlock phrase-level translation. After this epic, users can configure an AI provider (OpenAI, Anthropic, Ollama), store their API key securely, and get contextual AI translations for multi-word selections — with graceful fallback if no provider is configured.

### Story 6.1: AI Provider Configuration

As a power user,
I want to connect my own AI subscription to Lekto,
So that I can get contextual phrase translations using my existing API access without Lekto storing or monetising my credentials.

**Acceptance Criteria:**

**Given** the user navigates to Settings → AI Provider
**When** the screen loads
**Then** it displays: a provider selector (OpenAI, Anthropic, Ollama), an API key input field (masked by default), a reveal toggle for the key field, and a Save button — with a clear explanation that the key is stored on-device only and never shared

**Given** the user selects a provider and enters an API key
**When** they tap Save
**Then** the key is stored exclusively via the platform secure storage adapter (Android Keystore on Android, Tauri Stronghold on Desktop) — it is never written to the vault, never written to SQLite, and never appears in any log or error output

**Given** an API key is saved
**When** the user reopens the Settings → AI Provider screen
**Then** the previously selected provider is shown and the key field displays a masked placeholder (e.g. `••••••••`) — the key is retrievable from secure storage for use but never displayed in plain text

**Given** the user wants to update their API key
**When** they clear the field, enter a new key, and tap Save
**Then** the new key replaces the old one in secure storage

**Given** the user saves a new API key
**When** the app validates the key (lightweight test call to the provider)
**Then** an inline success or error message is shown — on error, the previous key (if any) remains active and the new key is not stored

**Given** no AI provider is configured
**When** the user reads, looks up words, saves vocabulary, and manages their library
**Then** all features work exactly as in Epics 1–5 — no degraded mode, no prompts to configure AI beyond the non-blocking phrase panel message

**Given** an API key is stored in secure storage
**When** the app generates any log entry, error report, or crash report
**Then** the key does not appear in any output — it is never interpolated into strings outside the secure storage adapter

---

### Story 6.2: AI Phrase Translation

As a reader,
I want phrase-level AI translation when I select multiple words,
So that I can understand expressions and sentences in context without switching to an external tool.

**Acceptance Criteria:**

**Given** an AI provider is configured and the user selects a multi-word phrase in the reader
**When** the `TranslationPanel` opens in phrase mode
**Then** an AI translation is requested using the selected phrase and its surrounding sentence as context — the full sentence is sent to improve translation accuracy

**Given** the AI request is in progress
**When** the panel is open
**Then** a loading skeleton is displayed inside the panel — the panel does not block the reader and the user can close it at any time

**Given** the AI response is received
**When** the translation is displayed
**Then** it appears in the panel body, clearly attributed to the configured provider, with the Save button available to save the phrase and translation to vocabulary

**Given** the AI request fails due to an invalid or expired API key
**When** the error is received
**Then** an inline error message is shown inside the panel ("Invalid API key — update it in Settings") — the reading session is not interrupted and no crash or modal alert occurs

**Given** the AI request fails due to a network timeout or offline state
**When** the error is received
**Then** an inline non-blocking message is shown inside the panel — the user can close the panel and continue reading without any disruption

**Given** the AI request fails for any reason
**When** the error is displayed
**Then** the API key value is never included in the error message shown to the user or written to any log

---

## Epic 7: Settings & Accessibility

All app settings are accessible and all accessibility requirements are met. After this epic, users can configure language settings and manage all preferences from a dedicated settings area; the app is fully keyboard-navigable, screen-reader-compatible, colorblind-accessible, and respects Android system font size.

### Story 7.1: Settings Area & Language Configuration

As a user,
I want a single settings area where I can manage all app preferences,
So that I can find and update any configuration without hunting through the app.

**Acceptance Criteria:**

**Given** the user taps Settings in the bottom navigation (mobile/tablet) or sidebar (desktop)
**When** the settings screen loads
**Then** it displays four clearly labelled sections: Languages, Vault, AI Provider, and Reading Preferences

**Given** the user opens the Languages section
**When** it renders
**Then** two selectors are displayed: "Native language" (their reference language for translations) and "Default target language" (pre-fills the language selector at book import — does not override the book's assigned language)

**Given** the user changes their native language
**When** the change is saved
**Then** the new native language is persisted locally and used as the reference language in AI translation requests

**Given** the user changes their default target language
**When** a new book is imported
**Then** the language selector at import is pre-filled with this value — the user can still change it per book and the book's assigned language always takes precedence for lookups

**Given** the user opens the Vault section
**When** it renders
**Then** the current vault path is displayed with a "Change location" button that navigates to the vault relocation flow from Story 2.3

**Given** the user opens the AI Provider section
**When** it renders
**Then** it navigates to the same standalone AI provider configuration screen created in Story 6.1 — no duplication, same screen

**Given** the user opens the Reading Preferences section
**When** it renders
**Then** it navigates to the reading customization panel from Story 4.4 (font, size, line height, margins, theme)

**Given** the settings screen is open on desktop
**When** the layout renders at >1024px
**Then** all four sections are visible in the sidebar layout without requiring navigation

---

### Story 7.2: Accessibility Polish

As a user with accessibility needs,
I want the entire app to be navigable by keyboard, screen reader, and without relying on color,
So that Lekto is usable regardless of how I interact with my device.

**Acceptance Criteria:**

**Given** the user navigates the desktop app using only the keyboard
**When** they Tab through any screen (library, reader, vocabulary, settings)
**Then** all interactive elements receive focus in a logical order and are activatable with Enter or Space

**Given** the translation panel is open on desktop
**When** the user presses Escape
**Then** the panel closes and focus returns to the `WordToken` that triggered it

**Given** any modal or dialog is open (e.g. book deletion confirmation)
**When** it is displayed
**Then** focus is trapped inside the modal — Tab does not reach elements behind it — and closing it returns focus to the triggering element

**Given** the desktop app is used with a screen reader (NVDA/Windows or VoiceOver/macOS)
**When** any interactive element receives focus
**Then** it announces a meaningful label — no element is announced as "button" or "link" without a descriptive label

**Given** the Android app is used with TalkBack enabled
**When** the user swipes through interactive elements
**Then** every button, icon, and interactive element has a content description that describes its action

**Given** the reader displays words at all 5 mastery levels
**When** viewed through a colorblindness simulation (Deuteranopia, Protanopia)
**Then** each mastery level is distinguishable by its underline style alone — no two levels share the same underline style

**Given** all three reading themes (Light, Sepia, Dark) are active
**When** text and UI elements are checked against WCAG 2.1 AA contrast requirements
**Then** all text/background combinations meet a minimum contrast ratio of 4.5:1 for normal text and 3:1 for large text

**Given** the Android app is running on a device with a non-default system font size
**When** the reader renders text
**Then** the base font size scales proportionally with the system setting using `sp` units via Capacitor

**Given** the Android app is used on any screen
**When** interactive elements are rendered
**Then** every tappable element has a minimum touch target of 48×48dp — verified across library, reader, translation panel, vocabulary list, and settings

---

## Epic 8: Tauri Desktop Integration

The app runs natively on Linux and Windows as a Tauri application sharing the same React/Capacitor codebase. After this epic, a developer can build and run the desktop app locally, the DB layer uses the Tauri SQL plugin with direct vault path access, all platform adapters have a `*.desktop.ts` implementation, and CI/CD produces AppImage and NSIS installers on release.

**Priority:** Immediate — must precede any feature story claiming Desktop support.

### Story 8.1: Tauri Initialization & Project Setup

As a developer,
I want Tauri v2 integrated into the project alongside Capacitor,
So that I can build and run the app as a native desktop application on Linux and Windows.

**Acceptance Criteria:**

**Given** the repository is cloned on a fresh machine
**When** the developer runs `npm run tauri dev`
**Then** the Tauri desktop window opens and the app renders correctly without errors

**Given** Tauri is configured
**When** a developer inspects the project
**Then** `src-tauri/` exists with `tauri.conf.json`, `Cargo.toml`, and a minimal Rust backend — Capacitor's `android/` directory coexists without conflict

**Given** the project has both Tauri and Capacitor targets
**When** `npm run build` runs
**Then** it produces a web-compatible bundle usable by both platforms — no duplicate build configurations

**Given** Tauri is configured
**When** the developer runs `npm run tauri build`
**Then** an AppImage is produced on Linux and an NSIS installer on Windows without errors

**Given** `isTauri()` is defined as a build-time Vite constant
**When** feature code checks platform
**Then** `isTauri()` returns `true` in a Tauri build and `false` in a Capacitor/Android build — no runtime import of Tauri APIs in non-Tauri builds

---

### Story 8.2: Desktop DB Layer (Tauri SQL Plugin)

As a developer,
I want the SQLite database accessed via the Tauri SQL plugin on desktop,
So that `lekto.db` lives directly in the vault folder at any OS path — no sync needed.

**Acceptance Criteria:**

**Given** the app starts on Desktop
**When** `main.tsx` runs
**Then** the Tauri SQL plugin opens `lekto.db` at the configured vault path, Drizzle's `migrate()` executes, and the database is ready for queries — no intermediate copy or OPFS layer involved

**Given** a vault path is set to a cloud-synced directory (e.g. `~/Dropbox/lekto-vault/`)
**When** the app starts on Desktop
**Then** `lekto.db` is opened directly at that path — the cloud sync tool handles replication transparently

**Given** the database layer abstraction in `src/shared/db/`
**When** `isTauri()` is true
**Then** the Tauri SQL adapter is selected at runtime; when false, the Capacitor SQLite adapter is selected — no platform branching in feature code

**Given** a Drizzle migration is pending
**When** the desktop app starts
**Then** the migration runs exactly once against the file at the vault path and is idempotent on subsequent restarts

---

### Story 8.3: Desktop Platform Adapters

As a developer,
I want `*.desktop.ts` implementations for all platform adapters,
So that filesystem, secure storage, and file picker features work natively on Linux and Windows.

**Acceptance Criteria:**

**Given** `filesystem.desktop.ts` is implemented
**When** feature code calls `filesystemAdapter.readFile(path)` on desktop
**Then** the Tauri FS plugin reads the file at the given absolute OS path without error

**Given** `filePicker.desktop.ts` is implemented
**When** feature code calls `filePickerAdapter.pickDirectory()` on desktop
**Then** the Tauri dialog plugin opens a native OS folder picker and returns the selected path

**Given** `secureStorage.desktop.ts` is implemented using Tauri Stronghold
**When** an API key is stored on desktop
**Then** it is persisted in Tauri Stronghold — never written to the vault or any plain file — and survives app restarts

**Given** all four adapters have `*.desktop.ts` implementations
**When** `isTauri()` is true
**Then** the `index.ts` runtime selector routes all adapter calls to the desktop implementation — `*.android.ts` is never loaded in a Tauri build

**Given** unit tests run for a feature using a platform adapter on desktop
**When** the adapter is mocked via dependency injection
**Then** tests pass without importing any Tauri-specific modules

---

### Story 8.4: Desktop CI/CD Pipelines

As a developer,
I want CI/CD pipelines producing desktop builds on release,
So that AppImage and NSIS installers are automatically published to GitHub Releases on every version tag.

**Acceptance Criteria:**

**Given** a pull request is opened
**When** the PR pipeline runs
**Then** a Tauri desktop build compiles without errors as part of the gate — blocking merge if it fails

**Given** a release tag `v*` is pushed
**When** the release pipeline runs
**Then** the Tauri build action produces an AppImage (Linux) and an NSIS installer (Windows), both attached to the GitHub Release alongside the Android APK

**Given** the Tauri build uses code signing secrets
**When** secrets are not configured (e.g. a fork PR)
**Then** the build completes without signing but does not fail the pipeline — signed artifacts are only required for release tags

**Given** Playwright E2E tests are configured for desktop
**When** the PR pipeline runs E2E
**Then** the test suite launches the Tauri app via WebDriver and executes the primary user journey (vault creation → book import stub) — pass/fail reported per spec file
