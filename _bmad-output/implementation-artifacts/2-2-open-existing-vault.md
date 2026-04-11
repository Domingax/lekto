# Story 2.2: Open Existing Vault

Status: review

## Story

As a returning user or multi-device user,
I want to point the app to an existing vault folder,
So that all my previous reading history and vocabulary are immediately restored.

## Acceptance Criteria

1. **Given** the user is on the VaultSetupScreen and selects "Open existing vault"
   **When** the button is clicked
   **Then** the file picker opens (Tauri native picker on Desktop, SAF picker on Android) and the user can select any folder

2. **Given** the user selects a folder that contains a valid `lekto.db`
   **When** vault detection runs
   **Then** the vault is accepted, the path is persisted, the app transitions to the library displaying all previously imported books and vocabulary

3. **Given** the user selects a folder that does NOT contain a `lekto.db`
   **When** vault detection runs
   **Then** an inline error message is shown ("This folder does not contain a valid Lekto vault") — the VaultSetupScreen remains open and no data is modified

4. **Given** the vault is loaded from an existing `lekto.db`
   **When** Drizzle migrations run on startup
   **Then** any pending migrations are applied without data loss and the app proceeds normally

5. **Given** the user opens an existing vault on a second device
   **When** the library loads
   **Then** all books, vocabulary entries, and reading progress from the vault are reflected accurately

6. **Given** all changes
   **Then** `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build` all pass with zero errors

## Tasks / Subtasks

### Commit 1: `feat(sync-vault): add openExistingVaultDesktop`

- [x] Task 1: Add `openExistingVaultDesktop(vaultPath: string): AsyncResult<void>` to `src/features/sync-vault/model/sync-vault.ts` (AC: #2, #3, #4)
  - [x] Check `filesystemAdapter.exists(`${vaultPath}/lekto.db`)` — returns `AsyncResult<boolean>`
  - [x] If `exists` returns `err` or `false` → return `err('This folder does not contain a valid Lekto vault')`
  - [x] Call `initDbForNewVault(vaultPath)` to reset and connect the DB at the selected vault path
  - [x] If DB init returns `err` → return `err(...)` with message
  - [x] Call `runMigrations()` — applies any pending schema updates without data loss (AC: #4)
  - [x] If migrations fail → return `err(...)`
  - [x] Call `preferencesAdapter.set(VAULT_PATH_KEY, vaultPath)` to persist the path
  - [x] If set fails → return `err(...)`
  - [x] Call `useVaultStore.getState().setVaultPath(vaultPath)`
  - [x] Return `ok(undefined)`

  ```typescript
  export async function openExistingVaultDesktop(vaultPath: string): AsyncResult<void> {
    try {
      const existsResult = await filesystemAdapter.exists(`${vaultPath}/lekto.db`)
      if (existsResult.isErr()) return err(existsResult.error)
      if (!existsResult.value) return err('This folder does not contain a valid Lekto vault')

      const dbResult = await initDbForNewVault(vaultPath)
      if (dbResult.isErr()) return err(`DB init failed: ${dbResult.error}`)

      const migrationsResult = await runMigrations()
      if (migrationsResult.isErr()) return err(`DB migration failed: ${migrationsResult.error}`)

      const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, vaultPath)
      if (setResult.isErr()) return err(setResult.error)

      useVaultStore.getState().setVaultPath(vaultPath)
      return ok(undefined)
    } catch (e) {
      return err(`Failed to open existing vault: ${e}`)
    }
  }
  ```

- [x] Task 2: Add `openExistingVaultAndroid(vaultPath: string): AsyncResult<void>` to `src/features/sync-vault/model/sync-vault.ts` (AC: #2, #3, #4, #5)
  - See **Dev Notes — Android Binary Import** section for implementation details
  - At minimum: validate `lekto.db` exists in the selected folder, import the binary DB into internal storage, reinitialize DB connection, run migrations, persist path, set vault path in store
  - If `lekto.db` not found → return `err('This folder does not contain a valid Lekto vault')`

- [x] Task 3: Export new functions from `src/features/sync-vault/index.ts`
  - [x] Add `openExistingVaultDesktop` to exports
  - [x] Add `openExistingVaultAndroid` to exports

- [x] Task 4: Re-export from `src/features/index.ts`
  - [x] Add `openExistingVaultDesktop` and `openExistingVaultAndroid`

- [x] Task 5: Write unit tests `src/features/sync-vault/model/sync-vault.test.ts`
  - [x] `openExistingVaultDesktop` — lekto.db exists → DB init → migrate → persist → setVaultPath → ok
  - [x] `openExistingVaultDesktop` — lekto.db NOT found → err without side effects
  - [x] `openExistingVaultDesktop` — DB init fails → err
  - [x] `openExistingVaultDesktop` — migrations fail → err
  - [x] `openExistingVaultAndroid` — valid vault → binary imported, migrated, persisted → ok
  - [x] `openExistingVaultAndroid` — lekto.db NOT found → err without side effects

- [x] Task 6: Quality gate — commit 1
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 2: `feat(page): implement Open Existing Vault flow in VaultSetupPage`

- [x] Task 7: Extend `FlowState` type and add `open` flow states to `VaultSetupPage.tsx` (AC: #1, #2, #3)
  - [x] Change `FlowState` from `'idle' | 'create' | 'creating'` to `'idle' | 'create' | 'creating' | 'open' | 'opening'`
  - [x] Import `openExistingVaultDesktop` and `openExistingVaultAndroid` from `'../../../features'`
  - [x] Enable the "Open existing vault" button (remove `disabled` prop)
  - [x] Add `onClick` to "Open existing vault" button → trigger `handleOpenPick()` immediately
  - [x] Add `handleOpenPick()` with cancel/error handling and platform dispatch
  - [x] When `flowState === 'opening'`: show loading state paragraph
  - [x] "Open existing vault" button text: `flowState === 'opening' ? 'Opening…' : 'Open existing vault'` (disabled while opening)

- [x] Task 8: Update `src/pages/vault-setup-page/ui/VaultSetupPage.test.tsx` (AC: #1, #2, #3)
  - [x] Remove the test that asserts "Open existing vault" is disabled
  - [x] Add test: clicking "Open existing vault" → `filePickerAdapter.pickDirectory()` called
  - [x] Add Android test: picker returns path with valid vault → `openExistingVaultAndroid` called → success → navigate to `/library`
  - [x] Add Android test: picker returns path with invalid vault → `openExistingVaultAndroid` returns err → inline error shown, flow stays `idle`
  - [x] Add Android test: picker cancelled → no error shown, flow stays `idle`
  - [x] Add Desktop test (mock `isTauri` → `true`): picker returns path → `openExistingVaultDesktop` called → success → navigate
  - [x] Add Desktop test: `openExistingVaultDesktop` returns err → inline error shown
  - [x] Add Desktop test: picker returns real error (not `'cancelled'`) → inline error shown

- [x] Task 9: Quality gate — commit 2 (final)
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass
  - [x] `npm run build` — succeeds

### Review Follow-ups (AI)

- [x] [AI-Review][High] Android `filesystemAdapter.exists()` is scoped to `Directory.Documents` but `openExistingVaultAndroid` passes absolute paths from `FilePicker.pickDirectory()`. Capacitor `stat({ path, directory })` concatenates them → exists check fails for every real vault → "folder does not contain a valid Lekto vault" on valid folders. Breaks AC #2, #3, #5 on Android. Either (a) stop passing `directory` in the adapter when the path is absolute, (b) add a separate adapter method for absolute-path existence checks, or (c) bypass the adapter in `openExistingVaultAndroid` and call `Filesystem.stat` directly with no `directory` (`src/features/sync-vault/model/sync-vault.ts:57`, `src/shared/platform/filesystem/filesystem.android.ts:73-80`).
- [x] [AI-Review][High] Move raw Capacitor calls out of `shared/db/`. `importAndroidVaultDb` imports `@capacitor/filesystem` and `@capacitor-community/sqlite` directly — violates AGENTS.md Enforcement Checklist ("Direct Capacitor or Tauri API calls outside `shared/platform/`"). Extract binary import/delete/write to a new `shared/platform/vault-db/vault-db.android.ts` adapter; keep `shared/db/` as the Drizzle surface only (`src/shared/db/index.ts:30-61`).
- [x] [AI-Review][High] Eliminate hardcoded Android internal DB path `/data/user/0/com.lekto.app/databases/lektoSQLite.db`. Read package name from a build-time constant (or native plugin call), handle non-zero user IDs, and stop depending on the `@capacitor-community/sqlite` internal filename (`lektoSQLite.db`). Prefer the plugin's own `copyFromPath` / `importFromJson` API over raw filesystem writes (`src/shared/db/index.ts:53`).
- [x] [AI-Review][High] `importAndroidVaultDb` has zero test coverage — violates AGENTS.md TDD protocol. Add unit tests mocking Capacitor Filesystem + SQLiteConnection that verify: valid binary import path, missing-file path, deleteDatabase invoked only when `isDatabase('lekto')` returns true, `_db` singleton reset, write-failure propagation (`src/shared/db/index.ts:30-61`).
- [x] [AI-Review][High] `Filesystem.writeFile({ path: '/data/user/0/...' })` without `directory` scope is likely unsupported — Capacitor plugin requires a directory scope or `file://` URI. Validate on a real device (or switch to `@capacitor-community/sqlite`'s `copyFromPath` / `importFromJson`) before relying on this flow (`src/shared/db/index.ts:53`).
- [x] [AI-Review][Medium] Remove dead `'open'` literal from `FlowState`. The flow only uses `'idle' → 'opening'`; `'open'` was spec'd in Task 7 but never wired (`src/pages/vault-setup-page/ui/VaultSetupPage.tsx:15`).
- [x] [AI-Review][Medium] Deduplicate `VAULT_PATH_KEY`. `shared/db/index.ts:12` redefines the key with a sync-by-comment. Hoist to `shared/lib/constants.ts` (or `shared/lib/vault`) and import from both sync-vault and shared/db.
- [x] [AI-Review][Medium] Resolve Vite warning `@capacitor/filesystem is dynamically imported ... but also statically imported`. The `await import('@capacitor/filesystem')` in `importAndroidVaultDb` is dead chunking intent. Drop the dynamic import (or move the static one) (`src/shared/db/index.ts:33`).
- [ ] [AI-Review][Medium] Add an integration-level assertion for the Android exists check against the actual adapter, not just mocks — catches the class of bug in H1 (`src/features/sync-vault/model/sync-vault.test.ts:260`).
- [x] [AI-Review][Medium] `filesystemAdapter.exists()` on Android swallows every error as `ok(false)`; callers cannot distinguish absent from permission-denied. Return `err(...)` for true errors so `openExistingVaultAndroid` can surface meaningful messages (`src/shared/platform/filesystem/filesystem.android.ts:73-80`).
- [ ] [AI-Review][Low] Rename `initDbForNewVault` → `connectDbAtVault` (or add an explicit `openDbAtVault` wrapper) — current name is misleading when called from the "open existing" flow (`src/shared/db/index.ts:20`).
- [x] [AI-Review][Low] `openExistingVaultAndroid` discards the `DrizzleDb` returned by `importAndroidVaultDb`. Change the import signature to `AsyncResult<void>` so consumer contracts are tight (`src/features/sync-vault/model/sync-vault.ts:61`).
- [x] [AI-Review][Low] Harden `importAndroidVaultDb` teardown: nulling `_db` and then `closeAllConnections()` may leave stale handles in `@capacitor-community/sqlite`'s plugin cache. Add a defensive `close('lekto')` before `deleteDatabase` (`src/shared/db/index.ts:45-49`).

## Dev Notes

### Architecture Context

**Platform architecture:** Two platforms only — Android (Capacitor) + Desktop (Tauri). No web. All platform-specific code goes in `shared/platform/` adapters.

**Vault validity marker (from architecture.md):**
```
filesystemAdapter.exists('lekto.db') — the DB file is the canonical marker, not books/
```
On Desktop, the path is absolute (`/home/user/Documents/my-vault/lekto.db`) and `filesystemAdapter.exists()` passes it directly to Tauri FS which handles absolute paths. On Android, the situation is more complex (see Android section below).

**No seeding on open existing vault:**
`openExistingVaultDesktop` must NOT call `seedLanguages()`. Seeding on an existing vault would attempt to insert rows that already exist (on conflict → ignore), which is safe but unnecessary. The vault already has language data from creation. Only call `runMigrations()`.

**Contrast with `initVaultDesktop`:**
- `initVaultDesktop` → creates `books/` dir + initializes fresh DB + seeds languages + migrates + persists path
- `openExistingVaultDesktop` → validates lekto.db exists + connects to existing DB + migrates (no create, no seed) + persists path

### Platform Detection Pattern

```typescript
import { isTauri } from '@/shared/platform'

const isDesktop = isTauri()  // place inside component function for test mockability

if (isDesktop) {
  // Tauri Desktop path
} else {
  // Android (Capacitor) path
}
```

### Desktop: `filesystemAdapter.exists()` with Absolute Paths

The desktop filesystem adapter uses `@tauri-apps/plugin-fs`'s `exists()` which accepts absolute OS paths:

```typescript
// Desktop — absolute path works correctly
const existsResult = await filesystemAdapter.exists(`${vaultPath}/lekto.db`)
// vaultPath e.g. '/home/user/Documents/my-vault' → checks '/home/user/Documents/my-vault/lekto.db'
```

On Windows, Tauri normalizes slashes — no manual `path.sep` needed.

### Android Binary Import

**Architecture requirement (from `architecture.md`):**
> On open existing vault: read `lekto.db` binary from vault folder → import into internal storage → reinitialize DB connection

**Implementation approach:**

The `@capawesome/capacitor-file-picker`'s `pickDirectory()` on Android returns an absolute path (not a `content://` URI) in the current implementation — see `src/shared/platform/file-picker/file-picker.android.ts`.

Step 1 — Read binary from vault:
```typescript
// Capacitor Filesystem without encoding → returns base64 for binary files
import { Filesystem } from '@capacitor/filesystem'
const result = await Filesystem.readFile({ path: `${vaultPath}/lekto.db` })
// result.data is string (base64) when no encoding is specified
```
If this throws, `lekto.db` does not exist at the path → vault is invalid.

Step 2 — Import binary into internal SQLite storage:
`@capacitor-community/sqlite` stores native Android DBs in the app's internal databases directory. To replace the DB:
1. Close current connection (reset `_db = null` via `resetDb()`)
2. Delete existing internal DB if present (use SQLiteConnection's `deleteDatabase()` or `isDatabase()` + `deleteDatabase()`)
3. Write the base64 binary to internal storage using a calculated path (package name: `com.lekto.app`):
   - Internal path pattern: `/data/user/0/com.lekto.app/databases/lektoSQLite.db`
   - Use `@capacitor-community/sqlite`'s `copyFromPath` if available in v8
   - Alternatively: write binary directly using `Capacitor.convertFileSrc()` + native plugin calls
4. Reinitialize by calling the existing `createAndroidDb()` function (currently private — may need to extract or duplicate)

**Simpler alternative approach (if binary write is blocked):** Use `@capacitor-community/sqlite`'s `importFromJson` / `exportToJson` APIs to transfer data in JSON format. This avoids binary file manipulation at the cost of being slightly slower for large DBs.

**Fallback MVP approach:** If the Android binary import proves too complex for this sprint:
- Detect vault validity (step 1 above)
- Store the vault path in preferences
- Show a message explaining the user's data will be available after restarting (or re-prompt)
- This is a degraded experience and should be resolved before ship

### `initDbForNewVault` Reuse

`initDbForNewVault(vaultPath)` already exists in `src/shared/db/index.ts`:
```typescript
export async function initDbForNewVault(vaultPath: string): Promise<Result<DrizzleDb, string>> {
  _db = null;  // resets singleton
  // creates new Desktop Tauri SQL connection at vaultPath/lekto.db
}
```
This is the correct function to reuse for Desktop open-existing-vault. It resets the DB singleton and opens a connection at the specified path (connecting to an existing file works the same as creating a new one with Tauri SQL plugin).

### Mocking in Tests

Mock `isTauri` per test file (must be inside component function for mockability):
```typescript
vi.mock('@/shared/platform', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/platform')>()
  return { ...original, isTauri: vi.fn().mockReturnValue(true) }
})
```

Mock `openExistingVaultDesktop` / `openExistingVaultAndroid`:
```typescript
vi.mock('../../../features', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../../features')>()
  return {
    ...original,
    openExistingVaultDesktop: vi.fn().mockResolvedValue(ok(undefined)),
    openExistingVaultAndroid: vi.fn().mockResolvedValue(ok(undefined)),
  }
})
```

Mock `filePickerAdapter.pickDirectory` to return a path or error:
```typescript
import * as platform from '@/shared/platform'
// ...
vi.spyOn(platform.filePickerAdapter, 'pickDirectory').mockResolvedValue(ok('/some/path'))
```

### Error Strings

Use consistent error messages:
- Vault invalid: `'This folder does not contain a valid Lekto vault'`
- Picker real error (Desktop): surface `result.error` directly in the UI
- DB/migration failures: surface the underlying error message

### Files Affected (Expected)

**Modified:**
- `src/features/sync-vault/model/sync-vault.ts` — add `openExistingVaultDesktop`, `openExistingVaultAndroid`
- `src/features/sync-vault/model/sync-vault.test.ts` — tests for new functions
- `src/features/sync-vault/index.ts` — export new functions
- `src/features/index.ts` — re-export new functions
- `src/pages/vault-setup-page/ui/VaultSetupPage.tsx` — enable open flow
- `src/pages/vault-setup-page/ui/VaultSetupPage.test.tsx` — tests for open flow

**No new files required** — the existing feature/page structure is sufficient.

### Cross-Story Context (Epic 2)

- **Story 2.1 (done):** Created vault setup screen with "Create new vault" working on both platforms. "Open existing vault" button exists but is `disabled`.
- **Story 2.2 (this):** Enable "Open existing vault" — the button is already in place; only logic and event handlers needed.
- **Story 2.3 (next):** Vault relocation from Settings — will reuse `openExistingVaultDesktop` / `openExistingVaultAndroid` logic for the "Use existing vault" branch.

### Previous Story Review Follow-Ups (Applied Fixes from 2.1-desktop)

- Desktop picker: `pickDirectory()` returns real error vs `'cancelled'` — already handled in `handleModify`. Apply same pattern to `handleOpenPick`.
- Desktop Confirm disabled while `selectedPath === ''` — not applicable to open flow (open immediately picks, no path displayed before pick).

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation proceeded without blocking issues.

### Completion Notes List

- `openExistingVaultDesktop` uses `filesystemAdapter.exists` + `initDbForNewVault` (Desktop Tauri SQL) + `runMigrations` — no `seedLanguages` call (existing vault already has language data).
- `openExistingVaultAndroid` uses `filesystemAdapter.exists` + new `importAndroidVaultDb` (reads binary via Capacitor `Filesystem.readFile`, deletes existing internal DB via `CapacitorSQLite.deleteDatabase`, writes binary to internal path, reinits Android connection) + `runMigrations`.
- `importAndroidVaultDb` added to `src/shared/db/index.ts` to encapsulate the Android-specific binary import flow; hardcodes internal databases path `/data/user/0/com.lekto.app/databases/lektoSQLite.db` — can be improved in a follow-up if package name needs to be dynamic.
- VaultSetupPage: `FlowState` extended to `'idle' | 'create' | 'creating' | 'open' | 'opening'`. The "Open existing vault" button now triggers `handleOpenPick()` directly (no intermediate `'open'` state needed since the picker opens immediately).
- `handleOpenPick` follows same cancel-vs-real-error distinction as `handleModify` on Desktop: `err('cancelled')` is silent, all other errors surface inline.
- Error display on the idle screen added (needed for open-vault failures that return to idle).

### File List

- `src/features/sync-vault/model/sync-vault.ts` — added `openExistingVaultDesktop`, `openExistingVaultAndroid`; imported `importAndroidVaultDb`
- `src/features/sync-vault/model/sync-vault.test.ts` — added tests for both new functions; added `exists` and `importAndroidVaultDb` to mocks
- `src/features/sync-vault/index.ts` — exported `openExistingVaultDesktop`, `openExistingVaultAndroid`
- `src/features/index.ts` — re-exported `openExistingVaultDesktop`, `openExistingVaultAndroid`
- `src/shared/db/index.ts` — added `importAndroidVaultDb`
- `src/pages/vault-setup-page/ui/VaultSetupPage.tsx` — extended FlowState, added `handleOpenPick`, enabled "Open existing vault" button
- `src/pages/vault-setup-page/ui/VaultSetupPage.test.tsx` — removed disabled-button test; added 7 new tests for open vault flow (Android + Desktop)

### Change Log

- 2026-04-11: Implemented Story 2-2 — Open Existing Vault. Added `openExistingVaultDesktop` and `openExistingVaultAndroid` feature functions with full unit test coverage. Added `importAndroidVaultDb` to shared DB module for Android binary import. Enabled "Open existing vault" flow in VaultSetupPage with `handleOpenPick` and proper loading/error states.
- 2026-04-11: Addressed code review findings (H1–H5, M1–M3, M5, L2–L3). Extracted Android vault DB import to `shared/platform/vault-db/vault-db.android.ts` adapter with dependency injection + 8 unit tests. Eliminated hardcoded internal DB path via `connection.getUrl()`. Removed dead `'open'` FlowState. Hoisted `VAULT_PATH_KEY` to `shared/lib/constants.ts`. Fixed Vite static/dynamic import warning. Fixed `filesystemAdapter.exists()` error/not-found distinction on Android. Changed `importAndroidVaultDb` return type to `AsyncResult<void>`. All 189 tests pass.
