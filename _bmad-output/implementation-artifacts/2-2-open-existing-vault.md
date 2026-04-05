# Story 2.2: Open Existing Vault

Status: ready-for-dev

## Story

As a returning user or multi-device user,
I want to point the app to an existing vault folder,
so that all my previous reading history and vocabulary are immediately restored.

## Acceptance Criteria

1. From VaultSetupScreen (idle state), clicking "Open existing vault" opens a folder picker (platform-specific).
2. The folder picker allows selecting from any device or cloud-synced directory accessible on the platform.
3. If the selected folder contains a `books/` subdirectory (valid vault marker), the vault is accepted: path persisted, vault store updated, library displayed.
4. If the selected folder does NOT contain `books/`, an inline error is shown, VaultSetupScreen remains open, no state modified.
5. On loading an existing vault, Drizzle migrations already run at startup (`main.tsx`) ensure schema is current without data loss — no extra migration step needed in this story.
6. On subsequent app launches after opening an existing vault, the app navigates directly to library (vault already configured).

## Tasks / Subtasks

- [ ] Task 1 — Add `openExistingVault` business logic to `sync-vault.ts` (AC: 3, 4, 6)
  - [ ] 1.1 Add `openExistingVaultWithNativeHandle(handle: FileSystemDirectoryHandle): AsyncResult<void>` to `sync-vault.ts`:
    - Call `setFilesystemRoot(handle)` to configure the web adapter
    - Call `filesystemAdapter.exists('books')` to validate; if `false` → `err('Not a valid Letko vault')`
    - Call `storeVaultHandle(handle)` to persist handle for future sessions
    - `preferencesAdapter.set(VAULT_PATH_KEY, WEB_NATIVE_PATH)`
    - `useVaultStore.getState().setVaultPath(WEB_NATIVE_PATH)`
  - [ ] 1.2 Add `openExistingVault(path: string): AsyncResult<void>` for OPFS/Android:
    - If `path === WEB_OPFS_PATH` → call `setFilesystemRoot(null)` (same as `initVault`)
    - Call `filesystemAdapter.exists('books')` to validate; if `false` → `err('Not a valid Letko vault')`
    - `preferencesAdapter.set(VAULT_PATH_KEY, path)`
    - `useVaultStore.getState().setVaultPath(path)`
  - [ ] 1.3 Export both functions from `src/features/sync-vault/index.ts`
  - [ ] 1.4 Write tests (mock all adapters and store):
    - Valid folder (books exists) → `ok(undefined)`, path persisted, store updated
    - Invalid folder (books absent) → `err('Not a valid Letko vault')`, no preferences written
    - Native handle variant: handle stored in IndexedDB + filesystem root set
  - [ ] Quality gate: lint, typecheck, test all pass

- [ ] Task 2 — Enable "Open existing vault" flow in `VaultSetupPage.tsx` (AC: 1, 2, 3, 4)
  - [ ] 2.1 Extend `FlowState` type: `'idle' | 'create' | 'creating' | 'opening'`
    - `'opening'` = async validation in progress after folder selection
  - [ ] 2.2 Add `handleOpenExisting()` async function:
    - Set `flowState('opening')` immediately (disables button during async work)
    - **Android** (`isNativePlatform`): `filePickerAdapter.pickDirectory()` → if ok call `openExistingVault(result.value)`, else revert to `idle`
    - **Web Chrome/Edge** (`hasDirectoryPicker`): `showDirectoryPicker()` → if ok call `openExistingVaultWithNativeHandle(handle)`, else revert to `idle` (user cancel is not an error)
    - **Web Firefox/Safari** (neither): path is unreachable — button is disabled (see 2.3)
    - On success: `navigate('/library')`
    - On error: `setError(result.error)`, revert to `idle`
  - [ ] 2.3 Update "Open existing vault" button in idle state:
    - `disabled` when `!isNativePlatform && !hasDirectoryPicker` (OPFS-only browsers cannot pick a folder)
    - `disabled` when `flowState === 'opening'` (async in progress)
    - `onClick={handleOpenExisting}` otherwise
  - [ ] 2.4 Show inline error (already exists via `{error && <p role="alert">{error}</p>}`) — verify it renders in idle state
  - [ ] 2.5 Write tests:
    - "Open existing vault" button disabled for OPFS-only browsers
    - "Open existing vault" button enabled for Chrome/Edge (hasDirectoryPicker)
    - Successful open → navigates to `/library`
    - Validation failure → inline error shown, stays on VaultSetupPage
    - User cancels picker → no error shown, button re-enabled
  - [ ] Quality gate: lint, typecheck, test all pass

## Dev Notes

### Existing functions to reuse — do NOT reinvent

| Function | Location | Purpose |
|---|---|---|
| `setFilesystemRoot(handle\|null)` | `src/shared/platform/filesystem/index.ts` | Configure web adapter root |
| `storeVaultHandle(handle)` | `src/features/sync-vault/lib/handle-store.ts` | Persist handle in IndexedDB |
| `filePickerAdapter.pickDirectory()` | `src/shared/platform` | Android directory picker |
| `filesystemAdapter.exists(path)` | `src/shared/platform` | Check file/dir existence |
| `preferencesAdapter.set/get` | `src/shared/platform` | Persist vault path |
| `useVaultStore.getState().setVaultPath()` | `src/shared/stores` | Update Zustand state |

### Validation: `books/` not `lekto.db`

The acceptance criteria mentions validating `lekto.db`, but the DB is NOT stored in the user-accessible vault folder:
- **Web**: `lekto.db` lives in OPFS root (`/lekto.db`) managed directly by `@sqlite.org/sqlite-wasm` — it is NOT inside the user's native folder.
- **Android**: the DB is managed by `@capacitor-community/sqlite` at a system path — it is NOT in `Directory.Documents`.

Therefore, use `filesystemAdapter.exists('books')` as the vault validity marker. A folder with a `books/` subdirectory was previously initialized by this app.

### Platform behavior matrix

| Platform | Picker | `filesystemAdapter.exists('books')` root | Handle storage |
|---|---|---|---|
| Web Chrome/Edge | `showDirectoryPicker()` → `FileSystemDirectoryHandle` | Inside native handle | IndexedDB (via `storeVaultHandle`) |
| Web Firefox/Safari | N/A — button disabled | N/A | N/A |
| Android | `filePickerAdapter.pickDirectory()` → `content://` URI | `Directory.Documents` (fixed) | None needed |

### Android filesystem adapter: fixed base directory

The Android adapter (`filesystem.android.ts`) always resolves paths relative to `Directory.Documents` — it **ignores** the vault path string for file operations. The path stored in preferences on Android is used for display only. Therefore `filesystemAdapter.exists('books')` checks `Documents/books` regardless of which folder the user picked.

### ⚠️ Deferred work: Android URI permissions

`filePickerAdapter.pickDirectory()` returns a `content://` tree URI. Persistable URI permissions (`takePersistableUriPermission`) may be required to keep access across app restarts. This is tracked in `_bmad-output/implementation-artifacts/deferred-work.md`. Do **not** solve it in this story — just implement the happy path and note the limitation.

### Migrations: no action needed in this story

`runMigrations()` is called in `main.tsx` at every startup before the vault is configured. If the user opens an existing vault, the DB is already migrated on startup. AC5 is satisfied by the existing startup flow — no additional migration call is needed here.

### FlowState: `'opening'` not `'open'`

There is no "preview before confirm" step for opening a vault (unlike `'create'` which shows the path + Confirm button). The flow is: button click → picker → instant validate/navigate. A single transient state `'opening'` (while async work runs) is sufficient. The component returns early for `isGrantPermission` and specific flow states — keep that pattern.

### Error message reuse: error clears on next action

In the existing component, `setError(null)` is called at the start of every handler. Follow this pattern in `handleOpenExisting()`.

### Project Structure Notes

- New functions go in `src/features/sync-vault/model/sync-vault.ts` (same file as existing vault functions — no new files needed)
- Export additions in `src/features/sync-vault/index.ts`
- UI changes confined to `src/pages/vault-setup-page/ui/VaultSetupPage.tsx`
- Tests co-located: `sync-vault.test.ts` and `VaultSetupPage.test.tsx` already exist (add cases, don't create new files)

### References

- Existing vault functions: `src/features/sync-vault/model/sync-vault.ts`
- VaultSetupPage with current stub: `src/pages/vault-setup-page/ui/VaultSetupPage.tsx:109`
- Filesystem adapter web impl: `src/shared/platform/filesystem/filesystem.web.ts`
- Filesystem adapter android impl: `src/shared/platform/filesystem/filesystem.android.ts`
- Handle store (IndexedDB): `src/features/sync-vault/lib/handle-store.ts`
- Deferred work: `_bmad-output/implementation-artifacts/deferred-work.md`
- Epic source: `_bmad-output/planning-artifacts/epics.md` (Story 2.2, lines ~432–459)
- Architecture: `_bmad-output/planning-artifacts/architecture.md` (Platform adapters section)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
