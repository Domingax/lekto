# Lekto Developer Reference

Lekto is a local-first, offline-capable language learning reader for Android and Desktop (Linux/Windows). Built with React 19, TypeScript ~5.9.3 (strict mode, `noUncheckedIndexedAccess`), Vite 7, Capacitor 8 (Android), Tauri v2 (Desktop), SQLite (Drizzle ORM), Zustand, shadcn/ui, Tailwind CSS v4, and neverthrow.

This file is the single authoritative reference for all project conventions, architecture rules, and workflow protocols.

---

## FSD Architecture

Lekto uses [Feature-Sliced Design](https://feature-sliced.design/) (FSD).

### Layer Hierarchy

```
app → pages → widgets → features → entities → shared
```

**Unidirectional import rule:** a layer may only import from layers to its right. Never import upward.

```typescript
// Correct
import { importBook } from '@/features/importBook'

// Wrong — never import from internal paths
import { parseEpub } from '@/features/importBook/lib/parseEpub'
```

### Barrel File Convention

Every slice exposes `index.ts` as its public API. Consumers import from the slice root, never from internal files.

### Segment Convention

Every slice uses folder-based segments, even for single files:

| Segment | Purpose | When present |
|---|---|---|
| `index.ts` | Public API — only exports visible outside the slice | Always |
| `ui/` | React components | When slice has UI |
| `model/` | Business logic, hooks, types | Always |
| `api/` | External calls (platform adapters, LLM, parsers) | When slice calls external services |
| `lib/` | Slice-local utilities | When needed |
| `config/` | Slice-local constants | When needed |

### Current Directory Structure

```
src/
├── app/          ← routing, providers, global CSS
├── pages/        ← Library, Reader, Vocabulary, Settings, VaultSetup
├── widgets/      ← TranslationPanel, ReaderView, ContinueReadingCard,
│                    BookListItem, MasterySelector
├── features/     ← importBook, saveWord, lookupWord, syncVault,
│                    playTTS, configureAI, deleteBook, manageVocabulary,
│                    translatePhrase
├── entities/     ← book, token, vocabularyEntry, readingProgress
└── shared/
    ├── ui/       ← shared UI components
    ├── db/       ← Drizzle schema + migrations + migrate runner
    ├── platform/ ← adapters: filesystem, TTS, secure storage,
    │                file picker, in-app-browser
    ├── stores/   ← Zustand stores (useReaderStore, useVaultStore, useAppStore)
    └── lib/      ← utils, types, constants, tokenizer/, ai/
```

**Note:** shadcn components live in `src/components/ui/` (not `src/shared/ui/`).

---

## Naming Conventions

### File Naming

- **React components:** PascalCase — `BookListItem.tsx`, `TranslationPanel.tsx`
- **Everything else:** kebab-case — `import-book.ts`, `use-vault.ts`, `drizzle.config.ts`
- **Test files:** same name + `.test` suffix, co-located — `import-book.test.ts` next to `import-book.ts`

### Code Naming

- **Functions/variables:** camelCase — `importBook`, `wordKey`
- **Types/interfaces:** PascalCase — `BookEntity`, `VocabEntry`
- **Zustand stores:** `use[Domain]Store` — `useReaderStore`, `useVaultStore`, `useAppStore`
- **Platform adapters:** `[capability]Adapter` — `filesystemAdapter`, `ttsAdapter`, `secureStorageAdapter`
- **Constants:** SCREAMING_SNAKE_CASE — `MAX_VOCAB_ENTRIES`, `DEFAULT_FONT_SIZE`

### Database Naming

- **SQLite columns:** snake_case — `word_key`, `book_id`, `created_at`
- **TypeScript types:** camelCase — `wordKey`, `bookId`, `createdAt`
- Drizzle handles the mapping automatically — never manually alias column names

---

## Error Handling

### Result<T, string> Pattern (neverthrow)

All operations that can fail return `Result<T, string>`. No throwing across module boundaries.

```typescript
import { ok, err, Result } from 'neverthrow'

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

### Rules

- Use `throw` only for unrecoverable programmer errors (violated invariants)
- Never `throw` for expected failures (network offline, file not found, invalid key)
- Never use `.then()/.catch()` chains — always `async/await`
  - Exception: wrapping third-party APIs inside platform adapters
- Errors display inline where the action was triggered, never toasts overlaying reading content

---

## State Management

### SQLite → Zustand Hydration

- **SQLite** is the source of truth (persisted data)
- **Zustand** is the UI/runtime state
- **React local state** (`useState`) for form inputs and transient UI states

On app startup, features hydrate their relevant Zustand slice from SQLite. Never read from SQLite inside React render — always read from Zustand.

```typescript
// Startup hydration (once, in app/index.ts)
const books = await db.select().from(booksTable)
useVaultStore.setState({ books })

// In component — correct
const books = useVaultStore(state => state.books)

// Never query SQLite inside a component
```

### Optimistic Updates

Update Zustand immediately on user action, persist to SQLite in the background. Never await the SQLite write before updating the UI.

### One Store Per Domain

```typescript
// Correct — separate stores
useReaderStore    // open book, position, panel, word selection
useVaultStore     // vault path, books list, connection status
useAppStore       // aiProvider config, global app state

// Wrong — everything mixed together
useGlobalStore
```

### Loading States

Use React local state for loading, not Zustand — loading is UI-local, not shared state.

```typescript
// Correct
const [isImporting, setIsImporting] = useState(false)

// Wrong (unless loading state is needed across multiple pages)
useAppStore(state => state.isImporting)
```

---

## Platform Adapters

### Adapter Pattern

Every platform capability has an interface + two implementations (Android + Desktop) in `shared/platform/`.

```
shared/platform/filesystem/
├── index.ts                   ← exports the active adapter
├── filesystem.interface.ts
├── filesystem.android.ts
└── filesystem.desktop.ts
```

The `index.ts` selects the implementation at runtime using the `isTauri()` build-time constant:

```typescript
import { isTauri } from '@/shared/platform'

export const filesystemAdapter =
  isTauri()
    ? desktopFilesystem
    : androidFilesystem
```

### Architectural Boundary

- No feature or widget imports from `@capacitor/*` or `@tauri-apps/*` directly — all platform calls route through `shared/platform/`
- Business logic only imports the interface, never the platform-specific implementation

### Six Boundaries

| Boundary | Location | Rule |
|---|---|---|
| **Platform** | `shared/platform/` | Only location for Capacitor API calls |
| **Database** | `shared/db/index.ts` | Single db instance; no feature creates its own connection |
| **Secure storage** | `shared/platform/secure-storage/` | Only location for API key read/write; keys never pass through other layers |
| **AI** | `shared/lib/ai/` | Only location for LLM API calls; features call `translate()` via interface |
| **Vault** | `shared/platform/filesystem/` | Only location for vault folder file I/O |
| **InAppBrowser** | `shared/platform/in-app-browser/` | Only location for opening external URLs; no feature calls `window.open()` or `@capacitor/browser` directly |

---

## TDD Protocol

### Red → Green → Refactor

1. **Red:** Write a failing test first
2. **Green:** Write the minimum code to make it pass
3. **Refactor:** Improve structure while keeping tests green

**No production code is written without a prior failing test.**

### Co-location Convention

Test files live next to the source file they test. Never use a separate `__tests__/` directory.

```
features/importBook/
├── index.ts
├── import-book.ts
├── import-book.test.ts        ← co-located
└── parse-epub.ts
```

### Testing Tools

- **Unit tests:** Vitest + React Testing Library (runs in jsdom)
- **E2E web:** Playwright (`npm run test:e2e`) — runs in CI
- **E2E Android:** Maestro (run locally before release; CI integration post-MVP)
- **Desktop validation:** `npm run tauri:dev` locally — no automated desktop E2E in CI (deferred until Epic 2+ user flows exist)
- Platform-specific code (Capacitor, Tauri plugins) must be mocked in Vitest/jsdom

### Desktop adapters — local validation only

Desktop adapters (`filesystemAdapter`, `filePickerAdapter`, `secureStorageAdapter`, `preferencesAdapter`) call Tauri IPC and cannot run in jsdom. They are not covered by unit tests. Validate them locally with `npm run tauri:dev`.

Desktop E2E (WebDriverIO + `tauri-driver`) is not wired in CI. It will be reintroduced when Epic 2+ produces real user flows worth automating.

---

## Drizzle ORM Convention

- Always use Drizzle's typed query builder (e.g., `db.insert(table).values(...).onConflictDoNothing()`) instead of raw `sql` template literals when the ORM provides an equivalent method
- Unix timestamps as integers — never Date objects in SQLite

```typescript
// In Drizzle schema
updatedAt: integer('updated_at').notNull()

// In UI only
new Date(entry.updatedAt * 1000).toLocaleDateString()
```

- Schema location: `src/shared/db/schema.ts`
- Custom migration runner in `src/shared/db/migrate.ts` — uses `import.meta.glob` to bundle SQL at build time (Drizzle's built-in migrator requires Node.js `fs`, incompatible with the Capacitor/Tauri runtime environment)

---

## Clean Code Principles

- **Single responsibility:** each function does one thing at one level of abstraction
- **Expressive naming:** names reveal intent — `parseEpubIntoTokens()` not `process()`
- **No boolean arguments:** a boolean parameter means a function does two things — split it
- **Command Query Separation:** a function either does something (command) or returns something (query), never both
- **DRY — Rule of Three:** first time write it, second time note it, third time abstract it. Premature abstraction is worse than duplication.
- **No magic numbers:** all constants named in `shared/lib/constants.ts`

---

## Commit Conventions

### Conventional Commits

- `feat:` → minor bump
- `fix:` → patch bump
- `BREAKING CHANGE` → major bump
- Local enforcement: commitlint + husky (pre-commit git hook)
- Automated versioning: release-please generates CHANGELOG.md + release PR on push to main

### Git Branching

**Every new piece of work MUST start on a dedicated branch from `main`.** Never commit directly to `main`.

```bash
git checkout main
git pull
git checkout -b story/<story-key>   # e.g. story/1-2-database-infrastructure
# or
git checkout -b fix/<short-description>
```

- Branch name mirrors the story key from `sprint-status.yaml`
- Merge (or PR) back to `main` after code review passes
- Delete the branch after merge

**Once implementation is complete and all tests pass, commit the work:**

```bash
git add <changed files>
git commit -m "feat|fix|chore(<scope>): <short description>"
```

---

## Beads Workflow

This project uses **bd (beads)** for ALL issue tracking. Do NOT use markdown TODOs, task lists, or other tracking methods.

### Session Start

1. `bd ready --json` — get the prioritized work queue (unblocked issues)
2. `bd update <id> --claim` — claim the task before starting

### Mid-Session Discovery

When you discover new work during implementation:

```bash
bd create "Issue title" --description="Detailed context" -t bug|feature|task -p 0-4 --json
bd create "Found bug" --description="Details" -p 1 --deps discovered-from:<parent-id> --json
```

### Issue Types & Priorities

| Type | Use for |
|---|---|
| `bug` | Something broken |
| `feature` | New functionality |
| `task` | Work item (tests, docs, refactoring) |
| `epic` | Large feature with subtasks |
| `chore` | Maintenance (dependencies, tooling) |

| Priority | Meaning |
|---|---|
| `0` | Critical (security, data loss, broken builds) |
| `1` | High (major features, important bugs) |
| `2` | Medium (default) |
| `3` | Low (polish, optimization) |
| `4` | Backlog (future ideas) |

### Landing the Plane (Session End)

**When ending a work session**, complete ALL steps. Work is NOT complete until `git push` succeeds.

1. **Close completed issues** — `bd close <id> --reason "Done"`
2. **File remaining work** — create issues for anything that needs follow-up
3. **Run quality gates** (if code changed) — tests, linters, builds
4. **Sync and push:**
   ```bash
   git pull --rebase
   bd sync
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Verify** — all changes committed AND pushed

**Critical:** unpushed work blocks other agents. Never stop before pushing.

### Auto-Sync

bd automatically syncs with git:
- Exports to `.beads/issues.jsonl` after changes (5s debounce)
- Imports from JSONL when newer (e.g., after `git pull`)

### Rules

- Always use `--json` flag for programmatic use
- Link discovered work with `discovered-from` dependencies
- Check `bd ready` before asking "what should I work on?"
- Do NOT create markdown TODO lists or use external issue trackers

---

## Dev Setup

### Rust Toolchain (required for Tauri Desktop builds)

The Tauri v2 CLI requires a Rust toolchain to compile the desktop host process. Install via [rustup](https://rustup.rs/):

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup toolchain install stable
rustup default stable
```

Verify with `rustc --version` and `cargo --version`. The stable toolchain is sufficient — no nightly required.

---

## Enforcement Checklist

Anti-patterns to reject — quick-scan list:

- [ ] Direct Capacitor or Tauri API calls outside `shared/platform/`
- [ ] `throw` statements for expected failure modes outside adapter wrappers
- [ ] `.then()/.catch()` chains in feature code
- [ ] Imports crossing FSD layer boundaries upward
- [ ] Importing from internal slice paths instead of barrel `index.ts`
- [ ] Global loading state in Zustand for UI-local operations
- [ ] Date objects stored in SQLite (use integer timestamps)
- [ ] Boolean function arguments
- [ ] Magic numbers inline in code
- [ ] `useGlobalStore` or single monolithic Zustand store
- [ ] SQLite queries inside React components
- [ ] Raw `sql` template literals when Drizzle provides a typed equivalent
- [ ] Toasts or modal alerts overlaying reading content for error display
- [ ] Test files in `__tests__/` directories instead of co-located
- [ ] Manual column name aliasing in Drizzle (it auto-maps snake_case → camelCase)
