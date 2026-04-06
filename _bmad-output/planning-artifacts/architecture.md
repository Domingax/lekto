---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
inputDocuments:
  - '_bmad-output/planning-artifacts/prd.md'
  - '_bmad-output/planning-artifacts/ux-design-specification.md'
  - '_bmad-output/planning-artifacts/ux-design-directions.html'
  - '_bmad-output/planning-artifacts/research/technical-stack-research-2026-03-03.md'
  - '_bmad-output/planning-artifacts/product-brief-2026-03-02.md'
workflowType: 'architecture'
project_name: 'lekto'
user_name: 'Damien'
date: '2026-03-09'
status: 'complete'
completedAt: '2026-03-12'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
44 FRs across 8 categories: Content Import (FR1–7), Library & Navigation (FR8–11), Immersive Reader (FR12–18), Translation & Lookup (FR19–23), Vocabulary Management (FR24–29), Vault & Data Sync (FR30–34), AI Configuration (FR35–38), Settings & Accessibility (FR39–44).

The architectural heart is the Import → Tokenize → Store → Render pipeline: content is parsed and tokenized once at import; the reader is a pure data renderer with no runtime parsing.

**Non-Functional Requirements:**
- Performance: TranslationPanel <200ms, page turn <100ms, vocabulary lookup stable at 10,000 entries
- Security: API keys stored exclusively in OS secure storage, never written to vault, never logged
- Privacy: zero telemetry, fully offline by design
- Accessibility: WCAG 2.1 AA (first-class, not post-MVP)
- Maintainability: unit + E2E test coverage on all core flows, CI pipeline required

**Scale & Complexity:**
- Primary domain: local-first native app (Android + Desktop), shared React frontend
- Complexity level: low-medium
- Estimated architectural modules: 8 (Import Pipeline, Token Renderer, Vocabulary Store, Vault Manager, Translation Service, AI Adapter, TTS Adapter, Secure Storage)

### Technical Constraints & Dependencies

- React/TypeScript/Vite frontend shared across Android (Capacitor) and Desktop (Tauri) — no platform-specific business logic branches; only platform adapter implementations differ
- Vault is a user-managed folder; all data must be portable files (SQLite) — no proprietary binary formats
- Dictionary services block iframe embedding — deep-link URLs opened via InAppBrowser (Android) or new tab (web); no API keys required, compatible with any web dictionary service
- No web distribution — desktop users get a native Tauri app; OPFS and web adapters are transitional artifacts to be removed as Tauri integration progresses
- Android 11+ (API 30) minimum — scoped storage restrictions require SAF file picker + content:// URI approach for vault folder access
- API keys must never enter the vault file system — requires a separate secure storage layer (Android Keystore / encrypted localStorage)

### Cross-Cutting Concerns Identified

- **Platform abstraction**: every platform-specific API (filesystem, secure storage, TTS, file picker) must route through an adapter — no direct platform calls in business logic
- **Offline state management**: all core features must degrade gracefully without network; only dictionary lookups, BYOK AI translation, and TTS use network
- **Error boundaries**: dictionary/AI/TTS failures must be inline, non-blocking — never disrupt reading session
- **Responsive layout**: TranslationPanel and ReaderPage change structure at breakpoints — bottom sheet on mobile, side panel on tablet/desktop (md: 768px, lg: 1024px)
- **Performance at scale**: WordToken rendering for 300+ page books requires virtualized rendering strategy
- **Accessibility**: ARIA roles, keyboard navigation, and non-color mastery indicators are cross-cutting requirements affecting every interactive component

## Starter Template Evaluation

### Primary Technology Domain

Native cross-platform app — React/TypeScript/Vite frontend shared across Android (Capacitor) and Desktop (Tauri). No SSR, no full-stack framework; all data is local.

### Starter Options Considered

- **Ionic Capacitor React starter**: rejected — imposes Ionic UI components, incompatible with shadcn/ui
- **Community starters**: rejected — poorly maintained, pinned versions, immediate technical debt risk
- **Vite react-ts + manual Capacitor setup**: selected — official Capacitor approach for non-Ionic React projects

### Selected Starter: Vite react-ts + Capacitor integration

**Rationale:** Stack fully validated by technical research (2026-03-03). No existing starter bundles exactly Vite + Capacitor + shadcn/ui without conflicts. Manual initialization is the recommended path per Capacitor documentation.

**Initialization Command:**

```bash
npm create vite@latest lekto -- --template react-ts
cd lekto && npm install
npx cap init lekto com.lekto.app --web-dir dist
npx cap add android
npx shadcn@latest init
```

**Architectural Decisions Provided by Starter:**

**Language & Runtime:** TypeScript 5 strict mode, native ESM

**Styling Solution:** Tailwind CSS v4 + shadcn/ui (Radix UI primitives) — design tokens via CSS custom properties

**Build Tooling:** Vite 6 — instant HMR, optimized ESM build, Capacitor CLI compatible

**Testing Framework:** To configure: Vitest + React Testing Library (unit), Playwright (E2E web), Maestro (E2E Android)

**Code Organization:** FSD structure defined in Core Architectural Decisions

**Development Experience:** Web hot reload, Capacitor live reload on Android via `npx cap run android -l`

**Note:** Project initialization is the first implementation story.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical (block implementation):**
- Vault: single SQLite file `lekto.db`
- Source architecture: Feature-Sliced Design (FSD)
- Source of truth: SQLite + Zustand for UI/runtime state

**Important (shape architecture):**
- State strategy: SQLite → Zustand hydration on startup, flush every 30s + appStateChange/visibilitychange
- Platform detection: Capacitor.isNativePlatform() via adapters in shared/platform/
- Tokenization: Latin regex for MVP, extensible per language in shared/lib/tokenizer/

**Deferred (post-MVP):**
- CJK tokenization (kuromoji)
- Maestro Android E2E in CI (emulator too slow for MVP)
- Google Play Store

---

### Data Architecture

**Vault — single file:**

```
vault/
├── books/     ← source files (EPUB, PDF, TXT)
└── lekto.db   ← SQLite: all application data
```

**Drizzle tables:**
- `books` — metadata (id, title, fileName, language, createdAt)
- `sections` — chapters/pages (id, bookId, index, title)
- `tokens` — pre-tokenized content (id, sectionId, index, type, text, wordKey)
- `vocabulary` — entries (wordKey, language, status 0–5, translation, notes, updatedAt)
- `reading_progress` — position (bookId, sectionIndex, tokenIndex, updatedAt)

**Migrations:** Custom migration runner at app startup (see GAP-06 resolution below). Drizzle's built-in `sqlite-proxy/migrator` cannot be used in browser/WASM — migrations are bundled at build time via Vite `import.meta.glob` and applied through a manual runner that replicates Drizzle's journal-based tracking.

---

### Frontend Architecture

**FSD structure:**

```
src/
├── app/          ← routing, providers, global CSS
├── pages/        ← Library, Reader, Vocabulary, Settings
├── widgets/      ← TranslationPanel, ReaderPage,
│                    ContinueReadingCard, BookListItem
├── features/     ← importBook, saveWord, lookupWord,
│                    syncVault, playTTS, configureAI
├── entities/     ← book, token, vocabularyEntry,
│                    readingProgress
└── shared/
    ├── ui/       ← shadcn + custom components
    ├── db/       ← Drizzle schema + migrations
    ├── platform/ ← adapters: filesystem, TTS,
    │                secure storage, file picker
    └── lib/      ← utils, types, tokenizer/
```

**Import rule:** unidirectional — pages → widgets → features → entities → shared. Never upward.

**State management:**
- SQLite: source of truth (persisted data)
- Zustand: UI/runtime state (open book, selected word, panel open/closed, readerSettings, vaultState, aiProvider)
- React local state: form inputs, transient UI states

**Reading position:** Zustand updated on every page turn, flushed to SQLite every 30s + on appStateChange/visibilitychange.

---

### Security

- No authentication (local-first, no accounts)
- API keys: @aparajita/capacitor-secure-storage (Android Keystore / encrypted localStorage) — never in vault, never in logs
- All external requests use HTTPS exclusively
- Content Security Policy configured on web build

---

### Infrastructure & Deployment

**Android:** GitHub Actions → signed APK → GitHub Releases + F-Droid (automatic pull on tag)
**Desktop:** GitHub Actions → Tauri build → Linux AppImage + Windows NSIS installer → GitHub Releases
**Env vars:** no build-time variables — API keys in secure storage at runtime only
**Platform detection:** Capacitor.isNativePlatform() in shared/platform/ — no platform branching in business logic

**CI/CD — 3 pipelines:**

Pipeline 1 — PR (required to merge):
```
lint + typecheck → unit tests (Vitest + coverage)
  → web build → E2E web (Playwright)
  → CodeQL → SonarCloud
```

Pipeline 2 — Push to main:
```
lint → unit tests → web build → E2E web
  → deploy GitHub Pages
  → release-please (creates/updates release PR)
```

Pipeline 3 — Release tag v* (triggered by merging the release-please PR):
```
Android build → sign APK → GitHub Release (APK attached) → F-Droid
Desktop build → Tauri (Linux AppImage + Windows NSIS) → GitHub Release
```

**Android E2E (Maestro):** run locally before release tag; CI integration post-MVP.

**Quality & Security:**
- Dependabot: CVE alerts on dependencies (continuous, outside pipelines)
- CodeQL: SAST JavaScript/TypeScript (on PR)
- SonarCloud: code quality, coverage, duplication (on PR)

**Conventions & Versioning:**
- Commit format: Conventional Commits
- Local enforcement: commitlint + husky (pre-commit git hook)
- Commit authoring helper: commitizen (`git cz`)
- Automated versioning: release-please
  - `feat:` → minor bump, `fix:` → patch bump, `BREAKING CHANGE` → major bump
  - Generates CHANGELOG.md + release PR on every push to main
  - Merging the PR → tag vX.Y.Z → triggers Pipeline 3

**Required GitHub Secrets:**
- ANDROID_KEYSTORE_BASE64
- ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD / ANDROID_STORE_PASSWORD
- SONAR_TOKEN

## Platform Strategy

### Decision: Capacitor Android + Tauri Desktop

**Context:** The app's core feature — immersive EPUB reading with word-by-word interaction — relies on HTML `<span>` elements, CSS mastery coloring, and DOM click handlers. This is what the web rendering engine does best. A native rendering framework (Flutter, React Native) would require rebuilding this text interaction layer from scratch with less mature EPUB tooling.

**Decision:** Preserve the HTML-based reader advantage and solve vault portability per platform through a two-bridge architecture with a fully shared React frontend.

| Target | Framework | SQLite location | Vault portability |
|---|---|---|---|
| Android | Capacitor | Export/import to vault folder | Via binary DB sync |
| Desktop Linux/Windows/macOS | Tauri v2 | Directly in vault folder | Native — zero workaround |

The React/TypeScript/Vite frontend is **100% shared**. Only the `shared/platform/` adapter implementations differ per target.

### Why not a web PWA for desktop?

The browser security model requires permission re-grant on every session for folder access (File System Access API). For a vault-centric app this is an unacceptable UX regression. Native desktop via Tauri eliminates this entirely.

### Why not Flutter?

`flutter_epub_viewer` (the mature EPUB package) renders via epub.js in a WebView with a JS↔Dart bridge for word interaction. The native rendering advantage is lost precisely for the app's core feature. A full rewrite in Dart for no reader improvement is not justified.

### Platform adapter structure (target state)

The adapter pattern uses exactly two implementations per capability — no web layer:

```
shared/platform/filesystem/
├── filesystem.interface.ts
├── filesystem.android.ts      ← Capacitor Filesystem (Directory.Documents)
└── filesystem.desktop.ts      ← Tauri fs plugin (direct OS path)

shared/platform/file-picker/
├── file-picker.interface.ts
├── file-picker.android.ts     ← @capawesome/capacitor-file-picker (SAF)
└── file-picker.desktop.ts     ← Tauri dialog plugin (native OS picker)

shared/platform/secure-storage/
├── secure-storage.interface.ts
├── secure-storage.android.ts  ← @aparajita/capacitor-secure-storage
└── secure-storage.desktop.ts  ← Tauri stronghold plugin

shared/platform/preferences/
├── preferences.interface.ts
├── preferences.android.ts     ← @capacitor/preferences
└── preferences.desktop.ts     ← Tauri store plugin
```

Platform selection in each `index.ts`:

```typescript
export const filesystemAdapter =
  isTauri() ? desktopFilesystemAdapter : androidFilesystemAdapter
```

`isTauri()` is a build-time constant injected via Vite define — no runtime sniffing.

**Migration note:** The current codebase contains `.web.ts` adapter files inherited from the initial web-first implementation. These are transitional — they are replaced by `.desktop.ts` counterparts as Tauri integration stories are implemented. No new `.web.ts` files should be created.

### Vault DB sync strategy

**Goal:** `lekto.db` lives inside the vault folder on disk, making the vault a self-contained portable unit (copy folder = full backup).

**Desktop (Tauri):** The Rust backend opens SQLite directly at the vault path. `lekto.db` is always current. No sync layer needed.

```rust
// Tauri command — opens DB at any OS path
let pool = SqlitePool::connect(&format!("sqlite:{}/lekto.db", vault_path)).await?;
```

**Android (Capacitor):** `@capacitor-community/sqlite` stores the DB in app internal storage. A sync layer in `features/sync-vault/` bridges the gap:

- **On vault create / write** (debounced 5s + `appStateChange` / `visibilitychange`): export DB binary via `sqlite3_js_db_export()` → write to vault folder as `lekto.db`
- **On open existing vault**: read `lekto.db` binary from vault folder → import into internal storage → reinitialize DB connection
- **Vault validity marker**: `filesystemAdapter.exists('lekto.db')` — the DB file is the canonical marker, not `books/`

**Note:** `sqlite3_js_db_export()` requires the DB to be in journal mode DELETE (not WAL). Ensure this is set at DB initialization.

### Deployment additions for Tauri

Pipeline 3 (release tag) gains a desktop build step:

```
web build → deploy GitHub Pages
  → Android APK build → sign → GitHub Release
  → Tauri desktop build (Linux AppImage, Windows NSIS) → GitHub Release
```

Required additional GitHub Secrets:
- `TAURI_PRIVATE_KEY` / `TAURI_KEY_PASSWORD` (updater signing)
- Windows code signing certificate (optional for initial release)

---

## Implementation Patterns & Consistency Rules

### Naming Patterns

**Database ↔ TypeScript mapping:**
- SQLite columns: snake_case (`word_key`, `book_id`, `created_at`)
- TypeScript: camelCase (`wordKey`, `bookId`, `createdAt`)
- Drizzle handles the mapping automatically — never manually map column names in application code

**File naming:**
- React components: PascalCase (`BookListItem.tsx`, `TranslationPanel.tsx`)
- All other files: kebab-case (`import-book.ts`, `use-vault.ts`, `drizzle.config.ts`)
- Test files: same name as source + `.test` suffix (`import-book.test.ts` co-located with `import-book.ts`)

**Code naming:**
- Functions/variables: camelCase (`importBook`, `wordKey`)
- Types/interfaces: PascalCase (`BookEntity`, `VocabEntry`)
- Zustand stores: `use[Domain]Store` (`useReaderStore`, `useVaultStore`, `useAppStore`)
- Platform adapters: `[capability]Adapter` (`filesystemAdapter`, `ttsAdapter`, `secureStorageAdapter`)
- Constants: SCREAMING_SNAKE_CASE (`MAX_VOCAB_ENTRIES`, `DEFAULT_FONT_SIZE`)

---

### Structure Patterns

**FSD exports — barrel files:**
Each FSD slice exposes a public API via `index.ts`. Consumers import from the slice root, never from internal files.

```typescript
// ✅ Correct
import { importBook } from '@/features/importBook'
import { BookEntity } from '@/entities/book'

// ❌ Wrong — never import from internal paths
import { parseEpub } from '@/features/importBook/lib/parseEpub'
```

**Component structure:**
Simple components: single file (`BookListItem.tsx`).
Complex components with local logic: dedicated folder.

```
widgets/TranslationPanel/
├── index.ts                   ← public export only
├── TranslationPanel.tsx
├── TranslationPanel.test.tsx
├── use-translation-panel.ts
└── types.ts
```

**Test co-location:**
Tests live next to the file they test, never in a separate `__tests__/` directory.

```
features/importBook/
├── index.ts
├── import-book.ts
├── import-book.test.ts        ← co-located
└── parse-epub.ts
```

**Platform adapter structure:**
Every platform capability has an interface + two implementations.

```
shared/platform/filesystem/
├── index.ts                   ← exports the active adapter
├── filesystem.interface.ts
├── filesystem.web.ts
└── filesystem.android.ts
```

The `index.ts` selects the implementation at runtime:
```typescript
export const filesystemAdapter =
  Capacitor.isNativePlatform()
    ? androidFilesystem
    : webFilesystem
```

Business logic only imports the interface — never the platform-specific implementation directly.

---

### Format Patterns

**Error handling — Result type:**
All operations that can fail return a `Result<T>` type (via `neverthrow`).
No throwing across module boundaries.

```typescript
import { ok, err, Result } from 'neverthrow'

// ✅ Correct
async function importBook(file: File): Promise<Result<Book, string>> {
  const parsed = await parseEpub(file)
  if (!parsed) return err('Failed to parse EPUB')
  return ok(parsed)
}

// Consumer — compiler enforces handling both cases
const result = await importBook(file)
if (result.isErr()) {
  // handle inline — never propagate further
}
```

Use `throw` only for unrecoverable programmer errors (violated invariants, wrong types).
Never `throw` for expected failure modes (network offline, file not found, invalid key).

**Async patterns:**
Always `async/await`. Never `.then()/.catch()` chains, never raw callbacks except
when wrapping third-party APIs inside platform adapters.

**Date/time storage:**
Unix timestamps (integer seconds) in SQLite. Convert to `Date` objects only at the UI display layer.

```typescript
// ✅ In Drizzle schema
updatedAt: integer('updated_at').notNull()

// ✅ In UI only
new Date(entry.updatedAt * 1000).toLocaleDateString()
```

---

### State Management Patterns

**Zustand store organization:**
One store per domain, not one global store.

```typescript
// ✅ Correct — separate stores
useReaderStore    // open book, position, panel, word selection
useVaultStore     // vault path, books list, connection status
useAppStore       // aiProvider config, global app state

// ❌ Wrong
useGlobalStore    // everything mixed together
```

**SQLite → Zustand hydration:**
On app startup, features hydrate their relevant Zustand slice from SQLite.
Never read from SQLite inside React render — always read from Zustand.

```typescript
// ✅ Startup hydration (once, in app/index.ts)
const books = await db.select().from(booksTable)
useVaultStore.setState({ books })

// ✅ In component
const books = useVaultStore(state => state.books)

// ❌ Never query SQLite inside a component
```

**Optimistic updates:**
Update Zustand immediately on user action, persist to SQLite in the background.
Never await the SQLite write before updating the UI.

---

### Process Patterns

**Loading states:**
Use local React state for loading, not Zustand — loading is UI-local, not shared state.

```typescript
// ✅ Correct
const [isImporting, setIsImporting] = useState(false)

// ❌ Wrong (unless the loading state is needed across multiple pages)
useAppStore(state => state.isImporting)
```

**Error display:**
Errors appear inline where the action was triggered.
Never use modal alerts or toasts that overlay reading content.

```typescript
// ✅ Correct — inline in the panel
{result.isErr() && (
  <p className="text-sm text-destructive">{result.error}</p>
)}

// ❌ Wrong — toast over reading content
toast.error(result.error)
```

---

### Clean Code Principles

All agents apply the following Clean Code principles consistently:

- **Single responsibility**: each function does one thing at one level of abstraction.
  If a function mixes low-level and high-level logic, split it.
- **Expressive naming**: names reveal intent. `parseEpubIntoTokens()` not `process()`.
  Comments explain *why*, never *what* — the code explains what.
- **No boolean arguments**: a boolean parameter means a function does two things. Split it.
- **Command Query Separation**: a function either does something (command) or returns
  something (query), never both. `saveWord()` does not return the saved word.
- **DRY — Rule of Three**: avoid duplicating business logic. Tolerate accidental duplication
  until a pattern is clear (first time: write it; second time: note it; third time: abstract it).
  Premature abstraction is worse than duplication.
- **No magic numbers**: all constants named in `shared/lib/constants.ts`.

---

### Enforcement Guidelines

**All AI agents MUST:**
- Import from FSD slice roots only — never from internal paths
- Return `Result<T>` (neverthrow) from all fallible async operations
- Use `async/await` exclusively — no `.then()/.catch()` chains in feature code
- Store Unix timestamps as integers in SQLite
- Route all platform API calls through `shared/platform/` adapters
- Co-locate tests with source files
- Use `use[Domain]Store` naming for Zustand stores
- Update Zustand optimistically before persisting to SQLite
- Apply Clean Code principles: single responsibility, expressive naming, CQS, Rule of Three

**Anti-patterns to reject in code review:**
- Direct Capacitor or Tauri API calls outside `shared/platform/`
- `throw` statements for expected failure modes outside adapter wrappers
- `.then()/.catch()` chains in feature code
- Imports crossing FSD layer boundaries upward
- Global loading state in Zustand for UI-local operations
- Date objects stored in SQLite (use integer timestamps)
- Boolean function arguments
- Magic numbers inline in code

## Project Structure & Boundaries

### Complete Project Directory Structure

```
lekto/
├── .github/
│   ├── workflows/
│   │   ├── pr.yml              ← Pipeline 1: lint + tests + E2E + CodeQL + SonarCloud
│   │   ├── main.yml            ← Pipeline 2: deploy web + release-please
│   │   └── release.yml         ← Pipeline 3: Android build + GitHub Release
│   └── dependabot.yml
├── .husky/
│   └── commit-msg              ← commitlint hook
├── android/                    ← Capacitor Android project (generated, not edited manually)
├── dist/                       ← Vite build output (gitignored)
├── e2e/                        ← Playwright E2E tests
│   ├── fixtures/
│   ├── library.spec.ts
│   ├── reader.spec.ts
│   └── translation.spec.ts
├── maestro/                    ← Android E2E flows (run locally before release)
│   ├── import-book.yaml
│   └── read-and-save-word.yaml
├── src/
│   ├── app/
│   │   ├── main.tsx            ← Entry point: DB migration + Zustand hydration
│   │   ├── App.tsx             ← Root component, providers, vault gate
│   │   ├── router.tsx          ← React Router v7 routes
│   │   └── globals.css         ← Tailwind base + CSS custom properties
│   ├── pages/
│   │   ├── library-page/
│   │   │   ├── index.ts
│   │   │   └── ui/
│   │   │       └── LibraryPage.tsx
│   │   ├── reader-page/
│   │   │   ├── index.ts
│   │   │   └── ui/
│   │   │       └── ReaderPage.tsx
│   │   ├── vocabulary-page/
│   │   │   ├── index.ts
│   │   │   └── ui/
│   │   │       └── VocabularyPage.tsx
│   │   ├── settings-page/
│   │   │   ├── index.ts
│   │   │   └── ui/
│   │   │       └── SettingsPage.tsx
│   │   └── vault-setup-page/   ← First-launch vault initialization (FR30)
│   │       ├── index.ts
│   │       └── ui/
│   │           └── VaultSetupPage.tsx
│   ├── widgets/
│   │   ├── translation-panel/  ← Signature interaction: lookup + save (FR14, FR19–23)
│   │   │   ├── index.ts
│   │   │   ├── ui/
│   │   │   │   └── TranslationPanel.tsx
│   │   │   └── model/
│   │   │       ├── use-translation-panel.ts
│   │   │       ├── use-translation-panel.test.ts
│   │   │       └── types.ts
│   │   ├── reader-view/        ← Tokenized text renderer + gestures (FR12–17)
│   │   │   ├── index.ts
│   │   │   ├── ui/
│   │   │   │   ├── ReaderView.tsx
│   │   │   │   └── WordToken.tsx
│   │   │   └── model/
│   │   │       ├── use-reader-view.ts
│   │   │       ├── use-reader-view.test.ts
│   │   │       └── types.ts
│   │   ├── continue-reading-card/  ← Library hero block (FR9)
│   │   │   ├── index.ts
│   │   │   ├── ui/
│   │   │   │   └── ContinueReadingCard.tsx
│   │   │   └── model/
│   │   │       └── types.ts
│   │   ├── book-list-item/     ← Library list row (FR8)
│   │   │   ├── index.ts
│   │   │   └── ui/
│   │   │       └── BookListItem.tsx
│   │   └── mastery-selector/   ← Confidence level buttons 1–4 + Known (FR27)
│   │       ├── index.ts
│   │       ├── ui/
│   │       │   └── MasterySelector.tsx
│   │       └── model/
│   │           └── use-mastery-selector.test.ts
│   ├── features/
│   │   ├── import-book/        ← EPUB/PDF/TXT → tokenize → SQLite (FR1–7)
│   │   │   ├── index.ts
│   │   │   ├── model/
│   │   │   │   ├── import-book.ts
│   │   │   │   ├── import-book.test.ts
│   │   │   │   └── tokenize-content.ts
│   │   │   └── api/
│   │   │       ├── parse-epub.ts
│   │   │       ├── parse-epub.test.ts
│   │   │       ├── parse-pdf.ts
│   │   │       ├── parse-pdf.test.ts
│   │   │       ├── parse-txt.ts           ← FR3: plain text import
│   │   │       └── parse-txt.test.ts
│   │   ├── save-word/          ← Save vocab entry with context (FR24–28)
│   │   │   ├── index.ts
│   │   │   └── model/
│   │   │       ├── save-word.ts
│   │   │       └── save-word.test.ts
│   │   ├── lookup-word/        ← Dictionary deep-link URLs (FR19)
│   │   │   ├── index.ts
│   │   │   ├── model/
│   │   │   │   ├── lookup-word.ts
│   │   │   │   └── lookup-word.test.ts
│   │   │   └── lib/
│   │   │       └── dictionary-urls.ts
│   │   ├── translate-phrase/   ← BYOK AI phrase translation (FR20–21)
│   │   │   ├── index.ts
│   │   │   ├── model/
│   │   │   │   ├── translate-phrase.ts
│   │   │   │   └── translate-phrase.test.ts
│   │   │   └── api/
│   │   │       └── call-ai-provider.ts
│   │   ├── sync-vault/         ← Vault init, relocation, restore (FR30–34)
│   │   │   ├── index.ts
│   │   │   └── model/
│   │   │       ├── sync-vault.ts
│   │   │       └── sync-vault.test.ts
│   │   ├── play-tts/           ← TTS audio playback (FR22)
│   │   │   ├── index.ts
│   │   │   └── model/
│   │   │       └── play-tts.ts
│   │   ├── delete-book/        ← Remove book + tokens from vault (FR11)
│   │   │   ├── index.ts
│   │   │   └── model/
│   │   │       ├── delete-book.ts
│   │   │       └── delete-book.test.ts
│   │   ├── manage-vocabulary/  ← View, update, delete vocab entries (FR29)
│   │   │   ├── index.ts
│   │   │   └── model/
│   │   │       ├── manage-vocabulary.ts
│   │   │       └── manage-vocabulary.test.ts
│   │   └── configure-ai/       ← BYOK API key setup + validation (FR35–38)
│   │       ├── index.ts
│   │       ├── model/
│   │       │   ├── configure-ai.ts
│   │       │   └── configure-ai.test.ts
│   │       └── api/
│   │           └── validate-api-key.ts
│   ├── entities/
│   │   ├── book/
│   │   │   ├── index.ts
│   │   │   └── model/
│   │   │       └── types.ts        ← BookEntity
│   │   ├── token/
│   │   │   ├── index.ts
│   │   │   └── model/
│   │   │       └── types.ts        ← TokenEntity, MasteryLevel enum (0–4)
│   │   ├── vocabulary-entry/
│   │   │   ├── index.ts
│   │   │   └── model/
│   │   │       └── types.ts        ← VocabEntry
│   │   └── reading-progress/
│   │       ├── index.ts
│   │       └── model/
│   │           └── types.ts        ← ReadingProgress
│   └── shared/
│       ├── ui/                 ← shadcn/ui components (via shadcn CLI)
│       │   ├── button.tsx
│       │   ├── sheet.tsx
│       │   ├── tabs.tsx
│       │   ├── dialog.tsx
│       │   ├── input.tsx
│       │   ├── slider.tsx
│       │   └── ...
│       ├── db/
│       │   ├── index.ts        ← db instance: Tauri plugin-sql/sqlx (desktop) | @capacitor-community/sqlite (android)
│       │   ├── schema.ts       ← Drizzle schema: 5 tables + indexes
│       │   ├── migrate.ts      ← migration runner
│       │   └── migrations/     ← Drizzle-generated migration files
│       ├── platform/
│       │   ├── filesystem/
│       │   │   ├── index.ts
│       │   │   ├── filesystem.interface.ts
│       │   │   ├── filesystem.web.ts
│       │   │   └── filesystem.android.ts
│       │   ├── tts/
│       │   │   ├── index.ts
│       │   │   ├── tts.interface.ts
│       │   │   ├── tts.web.ts
│       │   │   └── tts.android.ts
│       │   ├── secure-storage/
│       │   │   ├── index.ts
│       │   │   ├── secure-storage.interface.ts
│       │   │   ├── secure-storage.web.ts
│       │   │   └── secure-storage.android.ts
│       │   ├── file-picker/
│       │   │   ├── index.ts
│       │   │   ├── file-picker.interface.ts
│       │   │   ├── file-picker.web.ts
│       │   │   └── file-picker.android.ts
│       │   └── in-app-browser/     ← Dictionary deep links (FR19)
│       │       ├── index.ts
│       │       ├── in-app-browser.interface.ts
│       │       ├── in-app-browser.web.ts      ← window.open
│       │       └── in-app-browser.android.ts  ← @capacitor/browser
│       ├── stores/
│       │   ├── use-reader-store.ts
│       │   ├── use-vault-store.ts
│       │   └── use-app-store.ts
│       └── lib/
│           ├── constants.ts
│           ├── types.ts                ← Result<T>, shared utility types
│           ├── utils.ts
│           ├── tokenizer/
│           │   ├── index.ts
│           │   ├── tokenizer.interface.ts
│           │   └── latin.tokenizer.ts
│           └── ai/
│               ├── index.ts
│               ├── ai.interface.ts
│               ├── openai.adapter.ts
│               ├── anthropic.adapter.ts
│               └── ollama.adapter.ts
├── public/
│   └── favicon.ico
├── docs/
│   └── contributing.md
├── .commitlintrc.js
├── .czrc
├── capacitor.config.ts
├── drizzle.config.ts
├── eslint.config.js
├── index.html
├── package.json
├── playwright.config.ts
├── sonar-project.properties
├── tailwind.config.ts
├── tsconfig.json
├── vite.config.ts
└── vitest.config.ts
```

### Segment Convention

Every slice follows this mandatory structure regardless of size:

| Segment | Purpose | When present |
|---|---|---|
| `index.ts` | Public API — only exports visible outside the slice | Always |
| `ui/` | React components | When slice has UI |
| `model/` | Business logic, hooks, types | Always |
| `api/` | External calls (platform adapters, LLM, parsers) | When slice calls external services |
| `lib/` | Slice-local utilities | When needed |
| `config/` | Slice-local constants | When needed |

Rule: if a segment has only one file, the segment folder is still created. Consistency beats brevity.

### Architectural Boundaries

**Platform boundary** — `shared/platform/` is the only location where Capacitor or Tauri APIs are called. No feature or widget imports from `@capacitor/*` or `@tauri-apps/*` directly.

**Database boundary** — `shared/db/index.ts` exports the single db instance.
Features use Drizzle via this instance only. No feature creates its own connection.

**Secure storage boundary** — `shared/platform/secure-storage/` is the only location
where API keys are read or written. Keys never pass through any other layer.

**AI boundary** — `shared/lib/ai/` is the only location for LLM API calls.
Features call `translate()` via the interface — never `fetch()` to LLM endpoints directly.

**Vault boundary** — `shared/platform/filesystem/` is the only location for vault
folder operations. All file I/O routes through this adapter.

**InAppBrowser boundary** — `shared/platform/in-app-browser/` is the only location
for opening external URLs. No feature calls `window.open()` or `@capacitor/browser` directly.

### Data Flow

**Import flow:**
```
file-picker → import-book/model
  → import-book/api (foliate-js / PDF.js)
  → tokenize-content → shared/lib/tokenizer
  → shared/db: INSERT books + sections + tokens
  → useVaultStore.setState({ books })
```

**Read flow:**
```
LibraryPage → useVaultStore (books list)
  → open book → shared/db: SELECT tokens WHERE sectionId
  → useReaderStore.setState({ tokens, currentSection })
  → reader-view/ui: WordToken[] rendered from Zustand
```

**Lookup flow:**
```
WordToken tap → useReaderStore.setState({ selectedWord, isPanelOpen: true })
  → translation-panel → lookup-word/model
  → lookup-word/lib/dictionary-urls → InAppBrowser URL
```

**AI translate flow:**
```
Phrase selection → translate-phrase/model
  → translate-phrase/api → shared/lib/ai (adapter)
  → shared/platform/secure-storage (get key)
  → fetch() to LLM API → Result<string>
  → translation-panel displays result
```

**Save flow:**
```
MasterySelector tap → save-word/model
  → shared/db: UPSERT vocabulary (optimistic: Zustand first)
  → useVaultStore: update word status
  → WordToken re-renders with new mastery color
```

### Requirements to Structure Mapping

| FR | Primary location |
|---|---|
| FR1–3 (import formats) | `features/import-book/api/` (parse-epub, parse-pdf, parse-txt) |
| FR4–5 (tokenization) | `features/import-book/model/tokenize-content.ts`, `shared/lib/tokenizer/` |
| FR6–7 (import UX) | `pages/library-page/` (progress state), `features/import-book/model/` (language field) |
| FR8–9 (library + resume) | `pages/library-page`, `widgets/continue-reading-card` |
| FR10 (chapter navigation) | `widgets/reader-view/ui/` (section nav controls) |
| FR11 (delete book) | `features/delete-book/` |
| FR12–13 (mastery coloring) | `widgets/reader-view/ui/WordToken.tsx`, `entities/token/model/types.ts` |
| FR14–16 (selection + swipe) | `widgets/reader-view/model/use-reader-view.ts` |
| FR17 (auto-save position) | Zustand flush pattern (30s + appStateChange) |
| FR18 (reading customization) | `pages/settings-page/ui/`, `shared/stores/use-app-store.ts` |
| FR19 (dictionary lookup) | `features/lookup-word/`, `shared/platform/in-app-browser/` |
| FR20–21 (AI translation) | `features/translate-phrase/`, `shared/lib/ai/` |
| FR22 (TTS) | `features/play-tts/`, `shared/platform/tts/` |
| FR23 (offline degradation) | Result<T> inline error pattern (cross-cutting) |
| FR24–28 (vocabulary save) | `features/save-word/`, `widgets/mastery-selector/` |
| FR29 (vocabulary CRUD) | `features/manage-vocabulary/`, `pages/vocabulary-page/` |
| FR30–34 (vault) | `features/sync-vault/`, `shared/platform/filesystem/` |
| FR35–38 (AI config) | `features/configure-ai/`, `shared/platform/secure-storage/` |
| FR39–40 (language + settings) | `pages/settings-page/`, `shared/stores/use-app-store.ts` |
| FR41–44 (accessibility) | Cross-cutting — enforced in component authoring |

## Architecture Validation Results

### 1. Technology Coherence

All core technology choices are mutually compatible:

| Stack pair | Compatible | Notes |
|---|---|---|
| Vite 6 + React 19 | ✅ | Official Vite React plugin supports React 19 |
| Capacitor 6 + Vite 6 | ✅ | Capacitor uses the Vite `dist/` output directly |
| shadcn/ui + Tailwind CSS v4 | ✅ | shadcn v2 targets Tailwind v4 |
| Drizzle ORM + SQLite | ✅ | First-class Drizzle driver for both web-sqlite and Capacitor SQLite |
| Zustand 5 + React 19 | ✅ | Zustand 5 ships with React 19 concurrent mode support |
| neverthrow + TypeScript 5 | ✅ | Pure TypeScript, no runtime constraints |
| React Router v7 + React 19 | ✅ | React Router v7 requires React 18+, supports 19 |
| Vitest + Vite 6 | ✅ | Vitest is the official Vite testing framework |
| release-please + GitHub Actions | ✅ | First-class GitHub Actions integration |
| commitlint + husky v9 | ✅ | husky v9 supports Node 18+ |

**Decision compatibility:** All three critical decisions (FSD, SQLite vault, Zustand+SQLite state) are orthogonal — they do not constrain each other. The platform adapter pattern is compatible with FSD (shared/ layer).

**Pattern consistency:** Result<T> + async/await + optimistic updates are coherent with each other — no pattern forces a throw where another expects a Result, no pattern forces synchronous behavior where another expects async.

---

### 2. Functional Requirements Coverage

#### Fully covered

| FR | Location | Status |
|---|---|---|
| FR1 EPUB import | `features/import-book/api/parse-epub.ts` | ✅ |
| FR2 PDF import | `features/import-book/api/parse-pdf.ts` | ✅ |
| FR4 Tokenization at import | `features/import-book/model/tokenize-content.ts` | ✅ |
| FR5 Normalized word key | `features/import-book/model/` + `entities/token/model/types.ts` | ✅ |
| FR8 Library view | `pages/library-page/` + `widgets/book-list-item/` | ✅ |
| FR9 Resume reading | `widgets/continue-reading-card/` + `entities/reading-progress/` | ✅ |
| FR12 Mastery coloring | `widgets/reader-view/ui/WordToken.tsx` | ✅ |
| FR13 Update mastery from reader | `widgets/mastery-selector/` | ✅ |
| FR14 Select word | `widgets/reader-view/model/` | ✅ |
| FR15 Select phrase | `widgets/reader-view/model/` | ✅ |
| FR17 Auto-save position | Zustand flush pattern (30s + appStateChange) | ✅ |
| FR19 Dictionary lookup | `features/lookup-word/` | ✅ |
| FR20 AI phrase translation | `features/translate-phrase/` | ✅ |
| FR21 AI setup prompt | `widgets/translation-panel/model/` (panel state) | ✅ |
| FR22 TTS | `features/play-tts/` + `shared/platform/tts/` | ✅ |
| FR23 Offline degradation | Result<T> inline error pattern (cross-cutting) | ✅ |
| FR24 Save word | `features/save-word/` | ✅ |
| FR25 Attach translation | `features/save-word/` (translation field in vocab schema) | ✅ |
| FR26 Add notes | `features/save-word/` (notes field in vocab schema) | ✅ |
| FR27 Set confidence level | `widgets/mastery-selector/` | ✅ |
| FR28 View context sentence | `entities/vocabulary-entry/model/types.ts` (context field) | ✅ |
| FR30 Auto-create vault | `features/sync-vault/` + `pages/vault-setup-page/` | ✅ |
| FR31 Relocate vault | `features/sync-vault/` | ✅ |
| FR32 Portable vault files | Single `lekto.db` + books/ folder architecture | ✅ |
| FR33 Restore from vault | `features/sync-vault/` | ✅ |
| FR34 Detect vault on launch | `app/main.tsx` + `use-vault-store.ts` | ✅ |
| FR35 Configure AI provider | `features/configure-ai/` | ✅ |
| FR36 Enter/update API key | `features/configure-ai/` | ✅ |
| FR37 Secure key storage | `shared/platform/secure-storage/` | ✅ |
| FR38 Functional without AI | Architecture design (AI is additive) | ✅ |
| FR40 Settings area | `pages/settings-page/` | ✅ |

#### Gaps requiring resolution

| FR | Gap | Severity | Resolution |
|---|---|---|---|
| FR3 TXT import | `parse-txt.ts` missing from `features/import-book/api/` | Important | Add `parse-txt.ts` + `parse-txt.test.ts` to the structure |
| FR6 Import progress feedback | No dedicated UI location for progress reporting during long imports | Minor | Local `useState` for import progress in the triggering page — no new slice needed |
| FR7 Language assignment | No location for language tagging step during import flow | Minor | Add to `features/import-book/model/` (import form state) |
| FR10 Chapter navigation | No explicit chapter/section navigation widget | Minor | Add navigation controls to `widgets/reader-view/ui/` (prev/next section) |
| FR11 Delete book | No `delete-book/` feature slice | Important | Add `features/delete-book/` or expand `sync-vault/` to cover book removal |
| FR16 Swipe navigation | Swipe gesture handler not named in structure | Minor | Part of `widgets/reader-view/model/use-reader-view.ts` — no new file needed, but must be documented |
| FR18 Reading customization | Font/size/theme controls not wired to a settings location | Minor | Add reader settings to `pages/settings-page/ui/` + persist in `use-app-store.ts` |
| FR29 Vocabulary CRUD | No feature for viewing/updating/deleting vocabulary entries | Important | Add `features/manage-vocabulary/` slice |
| FR39 Language settings | Target/native language config has no dedicated location | Minor | Add to `pages/settings-page/` + `use-app-store.ts` |
| FR41–44 Accessibility | ARIA labels, keyboard nav, colorblind indicators, system font — cross-cutting | Minor | Not a structural gap; must be enforced in component authoring guidelines |

---

### 3. NFR Coverage

| NFR | Coverage | Status |
|---|---|---|
| TranslationPanel <200ms | Zustand read (no async I/O on tap) | ✅ |
| Page turn <100ms | Zustand token read — but virtualization strategy unspecified | ⚠️ Gap |
| EPUB import <15s | foliate-js async, worker-compatible | ✅ |
| App load <3s | Vite bundle + lazy route loading | ✅ |
| Vault detection <1s | Startup hydration from SQLite | ✅ |
| 10,000 vocab entries — stable | Schema defined but DB indexes not specified | ⚠️ Gap |
| API keys: secure storage only | `shared/platform/secure-storage/` boundary | ✅ |
| API keys: never in logs | Enforcement via anti-patterns section | ✅ |
| HTTPS only | Documented in security section | ✅ |
| CSP on web build | Mentioned, not specified | Minor gap |
| Zero telemetry | Architecture design (no analytics) | ✅ |
| WCAG 2.1 AA | Listed as first-class, enforced in component authoring | ✅ |
| Unit + E2E coverage | Vitest + Playwright + Maestro | ✅ |
| Green CI on merge | 3-pipeline CI/CD | ✅ |

---

### 4. Gap Analysis

#### Critical gaps (must resolve before implementation begins)

**GAP-01 — SQLite driver for web not specified**

The architecture specifies Drizzle ORM + SQLite as the database layer but does not name the web SQLite driver. Two options exist:

- `@sqlite.org/sqlite-wasm` + OPFS: persistent vault on web, OPFS support required (Chrome/Edge: ✅, Firefox: ✅, Safari 16.4+: ✅). Requires COOP/COEP headers on the web host.
- `sql.js`: in-memory only — data is lost on refresh unless manually persisted via OPFS. Not suitable for a vault-first application.

**Resolution:** Use `@sqlite.org/sqlite-wasm` with OPFS storage. For Android: `@capacitor-community/sqlite`. The `shared/db/index.ts` platform adapter selects the driver at runtime via `Capacitor.isNativePlatform()`.

```
shared/db/
├── index.ts          ← selects driver: wasm (web) or capacitor (android)
├── schema.ts
├── migrate.ts
└── migrations/
```

This requires adding COOP/COEP headers to the GitHub Pages deployment (configurable via `_headers` file or GitHub Actions).

**GAP-02 — Token virtualization strategy not selected**

The architecture acknowledges "300+ page books require virtualized rendering" as a cross-cutting concern but names no library. Without virtualization, rendering all `WordToken[]` for a large section will block the UI thread and violate the <100ms page turn NFR.

**Resolution:** Use `@tanstack/react-virtual` (TanStack Virtual v3). It integrates with any scroll container, is framework-agnostic, and is compatible with React 19. Virtualization logic lives in `widgets/reader-view/model/use-reader-view.ts`.

#### Important gaps (resolve before story creation)

**GAP-03 — InAppBrowser not in platform adapters**

Dictionary lookup opens URLs via InAppBrowser on Android. This is a platform capability that must route through `shared/platform/`. Currently absent from the structure.

**Resolution:** Add `shared/platform/in-app-browser/` adapter:
```
shared/platform/in-app-browser/
├── index.ts
├── in-app-browser.interface.ts
├── in-app-browser.web.ts      ← window.open(..., '_blank')
└── in-app-browser.android.ts  ← @capacitor/browser plugin
```

**GAP-04 — Missing feature slices for FR3, FR11, FR29**

Three FRs have no corresponding feature slice:
- FR3: plain text import — add `parse-txt.ts` to `features/import-book/api/`
- FR11: delete book — add `features/delete-book/`
- FR29: vocabulary CRUD — add `features/manage-vocabulary/`

**GAP-05 — Database indexes not specified**

The schema defines 5 tables but no indexes. The performance NFR requires stable reads at 10,000 vocabulary entries and fast token lookups.

**Resolution:** Document required indexes in `shared/db/schema.ts`:
- `vocabulary`: index on `(word_key, language)` — lookups are always by word key + language
- `tokens`: index on `(section_id, index)` — reader loads all tokens for a section in order
- `reading_progress`: index on `book_id` — one row per book, frequent updates

**GAP-06 — Drizzle migration runner incompatible with browser/WASM**

The built-in `migrate()` from `drizzle-orm/sqlite-proxy/migrator` calls `readMigrationFiles()` internally, which uses Node.js `fs.readFileSync()` — unusable in browser/WASM contexts. Its signature `migrate(db, callback, config)` still requires `config.migrationsFolder` as a filesystem path.

**Alternatives evaluated:**

| Approach | Viable | Trade-off |
|---|---|---|
| Custom runner with `import.meta.glob` | **Yes — selected** | Manual tracking table replication, but fully self-contained |
| Adapt op-sqlite/expo-sqlite migrator pattern | Partial | These accept pre-bundled `Record<string, string>`, but are typed for their own DB classes (`OPSQLiteDatabase`, `ExpoSQLiteDatabase`), not `SqliteRemoteDatabase` — type cast required, fragile across versions |
| Vite alias to replace `readMigrationFiles` | Yes but fragile | Couples build to Drizzle internal module structure — breaks silently on drizzle-orm updates |
| Wait for upstream fix | Uncertain | No timeline for a `sqlite-proxy/migrator` that accepts `MigrationMeta[]` |

**Resolution:** Custom migration runner in `shared/db/migrate.ts`. Uses Vite `import.meta.glob` to bundle `migrations/*.sql` and `migrations/meta/_journal.json` at build time. Replicates Drizzle's internal logic: journal-ordered execution, `-->  statement-breakpoint` splitting, `__drizzle_migrations` tracking table with hash and timestamp. Returns `Result<void, string>` via neverthrow.

**Upgrade path:** If Drizzle adds a `sqlite-proxy/migrator` overload accepting `MigrationMeta[]` or `Record<string, string>` (matching the op-sqlite pattern), migrate to the native API. Monitor drizzle-orm changelogs on major version bumps.

---

#### Minor gaps (note in implementation stories, no structural change required)

- FR6 (import progress): local `useState` in `pages/library-page/` — no structural change
- FR7 (language assignment): add language field to import form in `features/import-book/model/`
- FR10 (chapter navigation): prev/next section controls in `widgets/reader-view/ui/`
- FR16 (swipe): gesture handler in `widgets/reader-view/model/use-reader-view.ts`
- FR18 (reading customization): reading settings in `pages/settings-page/` + `use-app-store.ts`
- FR39 (language settings): target/native language in `pages/settings-page/` + `use-app-store.ts`
- FR41–44 (accessibility): cross-cutting, enforced via component authoring — add to enforcement guidelines

---

### 5. Validation Checklist

#### Coherence
- [x] All technology choices are mutually compatible
- [x] FSD import rule is unidirectional and enforced
- [x] Platform adapter pattern covers all Capacitor API calls
- [x] Result<T> pattern covers all fallible operations
- [x] Zustand hydration pattern is consistent across stores
- [x] Optimistic update pattern is consistent
- [x] CI/CD pipelines cover all deployment targets

#### Requirements
- [x] 44 FRs accounted for (34 fully covered, 10 with documented gaps)
- [x] All 8 FR categories have a corresponding structural location
- [x] All NFRs have a coverage path
- [x] 2 critical NFR gaps identified (virtualization, SQLite driver) with resolutions

#### Security
- [x] API key boundary defined and enforced
- [x] Vault contains no secrets
- [x] HTTPS documented
- [ ] CSP headers not yet specified (minor, pre-deployment concern)

#### Implementation readiness
- [x] Initialization command defined
- [x] All mandatory FSD segments documented
- [x] Naming conventions fully specified
- [x] Anti-patterns documented for code review
- [ ] 3 missing feature slices (delete-book, manage-vocabulary, in-app-browser adapter) — add to structure
- [ ] SQLite driver selection — document in shared/db/index.ts description
- [ ] DB indexes — document in schema.ts description

---

### 6. Resolutions Applied to Architecture

The following additions address the critical and important gaps identified above:

**SQLite driver selection:**
- Web: `@sqlite.org/sqlite-wasm` + OPFS (persistent vault in browser)
- Android: `@capacitor-community/sqlite`
- `shared/db/index.ts` selects driver at runtime via `Capacitor.isNativePlatform()`
- GitHub Pages deployment requires COOP/COEP headers (`Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Embedder-Policy: require-corp`)

**Virtualization:**
- `@tanstack/react-virtual` v3 in `widgets/reader-view/model/use-reader-view.ts`

**Additional platform adapter:**
- `shared/platform/in-app-browser/` for dictionary deep links

**Additional feature slices:**
- `features/delete-book/` (FR11)
- `features/manage-vocabulary/` (FR29)
- `features/import-book/api/parse-txt.ts` (FR3)

**DB indexes (to document in schema.ts):**
- `vocabulary(word_key, language)`, `tokens(section_id, index)`, `reading_progress(book_id)`

**Browser-compatible migration runner (GAP-06):**
- `drizzle-orm/sqlite-proxy/migrator` requires Node.js filesystem — cannot be used in browser/WASM
- Custom runner in `shared/db/migrate.ts` bundles SQL via Vite `import.meta.glob` at build time
- Replicates Drizzle journal parsing, breakpoint splitting, and `__drizzle_migrations` tracking
- Upgrade path: adopt native `sqlite-proxy/migrator` if Drizzle adds `MigrationMeta[]` overload
