# Story 2.3: Vault Relocation from Settings

Status: in-progress

## Story

As a user,
I want to change my vault location from the settings at any time,
so that I can move my data to a cloud-synced folder or a new path without losing anything.

## Acceptance Criteria

1. **Given** the user navigates to Settings → Vault
   **When** the vault settings screen loads
   **Then** the current vault path is displayed with a "Change location" button

2. **Given** the user taps "Change location"
   **When** the file picker opens and a new folder is selected
   **Then** the app detects whether the new folder contains an existing `lekto.db` and presents the appropriate option: "Use existing vault data" or "Migrate current data to this location"

3. **Given** the user confirms migration to a new empty folder
   **When** migration runs
   **Then** a progress indicator is shown, all vault contents (`books/` files and `lekto.db`) are copied to the new location, and the original vault remains untouched until the copy is confirmed successful

4. **Given** migration completes successfully
   **When** the app switches to the new vault
   **Then** the library reflects the new vault contents, the new path is persisted, and the original vault folder is left intact (not deleted)

5. **Given** migration fails at any point
   **When** the error occurs
   **Then** an inline error message is shown, the original vault remains active and unmodified, and no data is lost

6. **Given** the user selects a folder containing an existing `lekto.db` and confirms "Use existing vault data"
   **When** the vault switches
   **Then** the app loads the selected vault directly without copying, and the library reflects its contents

7. **Given** all changes
   **Then** `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build` all pass with zero errors

## Tasks / Subtasks

### Commit 1: `feat(platform): add copyFile to FilesystemAdapter`

- [x] Task 1: Add `copyFile(src: string, dest: string): AsyncResult<void>` to `src/shared/platform/filesystem/filesystem.interface.ts` (AC: #3)

- [x] Task 2: Implement `copyFile` in `src/shared/platform/filesystem/filesystem.desktop.ts` (AC: #3)
  - [x] Import `copyFile as tauriCopyFile` from `@tauri-apps/plugin-fs`
  - [x] Implement: `try { await tauriCopyFile(src, dest); return ok(undefined) } catch { return err(...) }`
  - [x] Both `src` and `dest` are absolute OS paths — Tauri `copyFile` handles absolute paths natively, no `BaseDirectory` needed
  - [x] Handles binary files (lekto.db) and text files (books/) correctly at the OS level

- [x] Task 3: Implement `copyFile` in `src/shared/platform/filesystem/filesystem.android.ts` (AC: #3)
  - [x] Use `Filesystem.copy({ from: src, to: dest })` without a `directory` scope when both paths are absolute (follows the same absolute-path pattern established in the 2-2 review fix for `exists()`)
  - [x] Wrap in try/catch and return `err(...)` on failure

- [x] Task 4: Add `copyFile` unit tests
  - [x] `src/shared/platform/filesystem/filesystem.desktop.test.ts`: mock `@tauri-apps/plugin-fs` — `copyFile` success returns `ok`, `copyFile` throws returns `err`
  - [x] `src/shared/platform/filesystem/filesystem.android.test.ts`: mock `@capacitor/filesystem` — `Filesystem.copy` success returns `ok`, throws returns `err`
  - [x] `src/shared/platform/filesystem/filesystem.index.test.ts`: verify `copyFile` is present on the exported `filesystemAdapter`

- [x] Task 5: Quality gate — commit 1
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 2: `feat(sync-vault): add relocateVaultDesktop`

- [x] Task 6: Add `relocateVaultDesktop(newVaultPath: string): AsyncResult<void>` to `src/features/sync-vault/model/sync-vault.ts` (AC: #3, #4, #5)
  - [x] Read `currentVaultPath` from `useVaultStore.getState().vaultPath`; return `err` if null
  - [x] Create `books/` directory at new vault: `filesystemAdapter.mkdir(`${newVaultPath}/books`)`
  - [x] Read all filenames from source books dir: `filesystemAdapter.readdir(`${currentVaultPath}/books`)`; on err, return `err`
  - [x] Copy each book file: `filesystemAdapter.copyFile(`${currentVaultPath}/books/${filename}`, `${newVaultPath}/books/${filename}`)` — return `err` on first failure (original vault untouched throughout)
  - [x] Reset DB connection: call `resetDb()` (exported from `@/shared/db`)
  - [x] Copy DB file: `filesystemAdapter.copyFile(`${currentVaultPath}/lekto.db`, `${newVaultPath}/lekto.db`)` — on failure, call `initDbForNewVault(currentVaultPath)` to restore the original DB connection before returning `err`
  - [x] Connect to new DB: `initDbForNewVault(newVaultPath)` — on failure, call `initDbForNewVault(currentVaultPath)` to restore, then return `err`
  - [x] Run migrations: `runMigrations()` — should be a no-op on a copied DB but ensures forward compatibility; on err, return `err`
  - [x] Persist path: `preferencesAdapter.set(VAULT_PATH_KEY, newVaultPath)` — on err, return `err`
  - [x] Update store: `useVaultStore.getState().setVaultPath(newVaultPath)`
  - [x] Return `ok(undefined)`

  ```typescript
  export async function relocateVaultDesktop(newVaultPath: string): AsyncResult<void> {
    const currentVaultPath = useVaultStore.getState().vaultPath
    if (!currentVaultPath) return err('No active vault to relocate')

    try {
      const mkResult = await filesystemAdapter.mkdir(`${newVaultPath}/books`)
      if (mkResult.isErr()) return err(mkResult.error)

      const readdirResult = await filesystemAdapter.readdir(`${currentVaultPath}/books`)
      if (readdirResult.isErr()) return err(readdirResult.error)

      for (const filename of readdirResult.value) {
        const copyResult = await filesystemAdapter.copyFile(
          `${currentVaultPath}/books/${filename}`,
          `${newVaultPath}/books/${filename}`,
        )
        if (copyResult.isErr()) return err(copyResult.error)
      }

      // Reset before copying DB — connection must be closed before the file is accessed
      resetDb()

      const copyDbResult = await filesystemAdapter.copyFile(
        `${currentVaultPath}/lekto.db`,
        `${newVaultPath}/lekto.db`,
      )
      if (copyDbResult.isErr()) {
        await initDbForNewVault(currentVaultPath) // best-effort restore
        return err(copyDbResult.error)
      }

      const dbResult = await initDbForNewVault(newVaultPath)
      if (dbResult.isErr()) {
        await initDbForNewVault(currentVaultPath) // best-effort restore
        return err(`DB init at new location failed: ${dbResult.error}`)
      }

      const migrationsResult = await runMigrations()
      if (migrationsResult.isErr()) return err(`DB migration failed: ${migrationsResult.error}`)

      const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, newVaultPath)
      if (setResult.isErr()) return err(setResult.error)

      useVaultStore.getState().setVaultPath(newVaultPath)
      return ok(undefined)
    } catch (e) {
      return err(`Failed to relocate vault: ${e}`)
    }
  }
  ```

- [x] Task 7: Export `relocateVaultDesktop` from `src/features/sync-vault/index.ts`

- [x] Task 8: Re-export `relocateVaultDesktop` from `src/features/index.ts`

- [x] Task 9: Write unit tests `src/features/sync-vault/sync-vault.test.ts` (AC: #3, #4, #5)
  - [x] `relocateVaultDesktop` — success path: readdir returns 2 files, all copies succeed, DB init succeeds, migrations succeed, persist succeeds → `ok`, store updated with new path
  - [x] `relocateVaultDesktop` — `mkdir` fails → `err`, original vault path unchanged in store
  - [x] `relocateVaultDesktop` — `copyFile` for a book fails → `err`, `resetDb` NOT yet called
  - [x] `relocateVaultDesktop` — `copyFile` for lekto.db fails → `err`, `initDbForNewVault` called with original path to restore
  - [x] `relocateVaultDesktop` — `initDbForNewVault` at new path fails → `err`, `initDbForNewVault` called with original path to restore
  - [x] `relocateVaultDesktop` — no active vault (null vaultPath) → `err` immediately

- [x] Task 10: Quality gate — commit 2
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 3: `feat(page): implement SettingsPage with vault relocation`

- [x] Task 11: Create `src/pages/settings-page/index.ts` (barrel)
  - [x] Export `SettingsPage` from `'./ui/SettingsPage'`

- [x] Task 12: Create `src/pages/settings-page/ui/SettingsPage.tsx` (AC: #1, #2, #3, #4, #5, #6)
  - [x] Import `useVaultStore` from `'../../../shared/stores'`
  - [x] Import `filePickerAdapter`, `isTauri` from `'../../../shared/platform'`
  - [x] Import `relocateVaultDesktop`, `openExistingVaultDesktop`, `openExistingVaultAndroid` from `'../../../features'`
  - [x] Import `filesystemAdapter` from `'../../../shared/platform'`
  - [x] Define `type VaultFlowState = 'idle' | 'confirm-migrate' | 'confirm-switch' | 'migrating' | 'switching'`
  - [x] Local state: `flowState`, `pickedPath`, `error`
  - [x] Read `vaultPath` from `useVaultStore`
  - [x] `handleChangePick()`: calls picker, checks for lekto.db, sets confirm-switch or confirm-migrate
  - [x] `handleConfirmMigrate()` (AC: #3, #4, #5): guards Android, calls relocateVaultDesktop on Desktop
  - [x] `handleConfirmSwitch()` (AC: #6): calls openExistingVaultDesktop or openExistingVaultAndroid
  - [x] `handleCancel()`: resets flow state, pickedPath, error
  - [x] Render vault section with all flow states, inline error, never navigates away

- [x] Task 13: Create `src/pages/settings-page/ui/SettingsPage.test.tsx`
  - [x] Mock `filePickerAdapter`, `filesystemAdapter`, `isTauri`, `relocateVaultDesktop`, `openExistingVaultDesktop`, `openExistingVaultAndroid`, `useVaultStore`
  - [x] Seed `useVaultStore` with `vaultPath: '/some/vault'` before each test
  - [x] Test: renders current vault path from `useVaultStore`
  - [x] Test: "Change location" button is present and enabled when vault is active
  - [x] Test: clicking "Change location" calls `filePickerAdapter.pickDirectory()`
  - [x] Test: picker returns path with existing `lekto.db` → `confirm-switch` state shown, picked path visible
  - [x] Test: picker returns path without `lekto.db` → `confirm-migrate` state shown, picked path visible
  - [x] Test: picker cancelled → state stays `idle`, no error shown
  - [x] Test: picker errors → inline error shown
  - [x] Test: confirm migrate → `relocateVaultDesktop` called with picked path → on `ok`, `idle` state
  - [x] Test: confirm migrate → `relocateVaultDesktop` returns `err` → inline error shown, `idle` state
  - [x] Test: confirm switch (Desktop, `isTauri` mocked true) → `openExistingVaultDesktop` called → on `ok`, `idle` state
  - [x] Test: confirm switch (Android, `isTauri` mocked false) → `openExistingVaultAndroid` called
  - [x] Test: cancel from confirm state → returns to `idle`
  - [x] Test: "migrating" state disables all buttons

- [x] Task 14: Export `SettingsPage` from `src/pages/index.ts`

- [x] Task 15: Quality gate — commit 3
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 4: `feat(router): add /settings route and settings link from LibraryPage`

- [x] Task 16: Add `/settings` route to `src/app/router.tsx` (AC: #1)
  - [x] Import `SettingsPage` from `'../pages'`
  - [x] Add `{ path: '/settings', loader: libraryLoader, element: <SettingsPage /> }` — uses existing `libraryLoader` to guard against accessing settings with no vault
  - [x] `libraryLoader` is already defined in `router.tsx` and checks for `vaultPath` — reuse as-is

- [x] Task 17: Add a Settings link to `src/pages/library-page/ui/LibraryPage.tsx`
  - [x] Read the current file first — it currently renders `<p>Library (coming soon)</p>`
  - [x] Add a `Link` (from `react-router-dom`) or `Button` pointing to `/settings`
  - [x] Minimal addition — do not restructure or style the library page beyond the navigation link

- [x] Task 18: Add router tests to `src/app/router.test.tsx` (AC: #1)
  - [x] Read existing router tests to understand the established test pattern
  - [x] Add test: `/settings` with active vault → allows access (libraryLoader returns null)
  - [x] Add test: `/settings` with no vault → redirects to `/vault-setup`

- [x] Task 19: Final quality gate
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass
  - [x] `npm run build` — succeeds

## Dev Notes

### Architecture Context

**This story touches three FSD layers:**
- `shared/platform/filesystem/` — new `copyFile` method
- `features/sync-vault/` — new `relocateVaultDesktop` function
- `pages/settings-page/` — new page (does NOT exist yet; create the FSD structure from scratch)

**SettingsPage does not exist yet.** The architecture file mentions it at `pages/settings-page/ui/SettingsPage.tsx`. The FSD structure must be created:
```
src/pages/settings-page/
├── index.ts          ← barrel: export { SettingsPage } from './ui/SettingsPage'
└── ui/
    ├── SettingsPage.tsx
    └── SettingsPage.test.tsx
```

**`syncVault` feature reuse:** `openExistingVaultDesktop` and `openExistingVaultAndroid` are already implemented and fully tested (story 2-2). The "Use existing vault data" path in this story calls them directly — no duplication.

### Desktop `copyFile` Implementation

`@tauri-apps/plugin-fs` exports `copyFile(fromPath, toPath)` which accepts absolute OS paths. Already imported in `filesystem.desktop.ts` as the `readDir` etc. imports use the same plugin. Add `copyFile as tauriCopyFile` to the existing destructured import.

```typescript
import {
  readTextFile,
  writeTextFile,
  remove,
  mkdir,
  readDir,
  exists,
  copyFile as tauriCopyFile,  // add this
} from '@tauri-apps/plugin-fs'
```

Then in the adapter:
```typescript
async copyFile(src: string, dest: string): AsyncResult<void> {
  try {
    await tauriCopyFile(src, dest)
    return ok(undefined)
  } catch {
    return err(`Failed to copy file: ${src} → ${dest}`)
  }
},
```

### Android `copyFile` Implementation

Use `Filesystem.copy({ from, to })` without a `directory` scope when both paths are absolute. This follows the pattern from the story 2-2 review fix (H1) which established how to handle absolute paths from the SAF picker in Android adapters. Do NOT pass `directory: BASE_DIR` when the path is absolute, as Capacitor concatenates them.

```typescript
async copyFile(src: string, dest: string): AsyncResult<void> {
  try {
    await Filesystem.copy({ from: src, to: dest })
    return ok(undefined)
  } catch {
    return err(`Failed to copy file: ${src} → ${dest}`)
  }
},
```

### Android Migration — Not Supported in This Story

Android vault migration (copying `lekto.db` + `books/` to a new Android location) is NOT fully implemented in this story. Reasons:
- On Android, `lekto.db` lives in app internal storage (not the vault folder) — the vault folder only gets a periodic export
- Moving the DB requires export → copy → import at new path, which involves the `vault-db` adapter
- The periodic DB export sync mechanism is not yet implemented

**For Android in this story:**
- The "Use existing vault data" path works fully (delegates to `openExistingVaultAndroid`)
- The "Migrate current data" path (`handleConfirmMigrate`): on Android (non-Tauri), show an inline message "Vault migration is not yet supported on Android" and set `flowState = 'idle'`. Do not disable the "Change location" button; just guard the confirm-migrate handler.

In `handleConfirmMigrate()`:
```typescript
if (!isTauri()) {
  setError('Vault migration is not yet supported on Android')
  setFlowState('idle')
  return
}
// ... Desktop migration
```

### `resetDb` is exported from `shared/db`

`resetDb()` is already exported from `src/shared/db/index.ts`:
```typescript
export function resetDb(): void {
  _db = null;
}
```
Import it in `sync-vault.ts` alongside the other db imports: `import { initDbForNewVault, runMigrations, resetDb } from '@/shared/db'`.

### Vault Path Safety Invariant

The copy sequence intentionally writes to the NEW location first (books/) before touching the DB connection or copying `lekto.db`. The original vault is read-only during migration. If any step before `resetDb()` fails, the DB connection is still live and pointing at the original vault — no recovery needed. Only after `resetDb()` is called do we lose the active connection; the best-effort `initDbForNewVault(currentVaultPath)` call then restores it.

### `readdir` on an Empty `books/` Directory

If the `books/` directory at the current vault is empty, `readdir` returns `ok([])`. The `for` loop iterates zero times. Migration proceeds correctly to copy `lekto.db`. This is not an error condition.

### Migration Leaves Original Vault Intact

Per AC #4, the original vault is never deleted or modified. The implementation only reads from the original and writes to the new destination. Explicitly do NOT add any cleanup/delete logic for the original vault.

### Inline Error, Never Toast

Per AGENTS.md: "Errors display inline where the action was triggered, never toasts overlaying reading content." Use `<p role="alert">` for the error display, consistent with `VaultSetupPage.tsx`.

### UX Flow State Machine

```
idle → (user clicks "Change location") → picker
  picker cancelled → idle
  picker error → idle + error shown
  picker returns path with lekto.db → confirm-switch
  picker returns path without lekto.db → confirm-migrate

confirm-migrate → (user confirms) → migrating → idle (success or error)
confirm-migrate → (user cancels) → idle

confirm-switch → (user confirms) → switching → idle (success or error)
confirm-switch → (user cancels) → idle
```

The "migrating" and "switching" states disable the "Change location" button and show a progress paragraph. No spinner component needed for MVP — a text paragraph is sufficient (consistent with `VaultSetupPage`'s `'Opening vault…'` paragraph).

### Existing Test Patterns

Look at `src/pages/vault-setup-page/ui/VaultSetupPage.test.tsx` for the established testing pattern: mock `filePickerAdapter`, `isTauri`, and feature functions, seed the vault store, and assert on rendered output + function calls.

### Files to Touch (Summary)

| File | Action |
|------|--------|
| `src/shared/platform/filesystem/filesystem.interface.ts` | Add `copyFile` to interface |
| `src/shared/platform/filesystem/filesystem.desktop.ts` | Implement `copyFile` |
| `src/shared/platform/filesystem/filesystem.android.ts` | Implement `copyFile` |
| `src/shared/platform/filesystem/filesystem.desktop.test.ts` | Test `copyFile` |
| `src/shared/platform/filesystem/filesystem.android.test.ts` | Test `copyFile` |
| `src/shared/platform/filesystem/filesystem.index.test.ts` | Test adapter has `copyFile` |
| `src/features/sync-vault/model/sync-vault.ts` | Add `relocateVaultDesktop` |
| `src/features/sync-vault/sync-vault.test.ts` | Test `relocateVaultDesktop` |
| `src/features/sync-vault/index.ts` | Export `relocateVaultDesktop` |
| `src/features/index.ts` | Re-export `relocateVaultDesktop` |
| `src/pages/settings-page/index.ts` | Create (new) |
| `src/pages/settings-page/ui/SettingsPage.tsx` | Create (new) |
| `src/pages/settings-page/ui/SettingsPage.test.tsx` | Create (new) |
| `src/pages/index.ts` | Export `SettingsPage` |
| `src/app/router.tsx` | Add `/settings` route |
| `src/pages/library-page/ui/LibraryPage.tsx` | Add Settings link |
| `src/app/router.test.tsx` | Test `/settings` route |

### References

- Acceptance criteria: `_bmad-output/planning-artifacts/epics.md#Story 2.3`
- UX journey: `_bmad-output/planning-artifacts/ux-design-specification.md#Journey 4`
- Architecture — sync vault: `_bmad-output/planning-artifacts/architecture.md#Vault DB sync strategy`
- Architecture — settings page location: `_bmad-output/planning-artifacts/architecture.md` (line ~608)
- Existing vault functions (to reuse): `src/features/sync-vault/model/sync-vault.ts`
- DB module (resetDb, initDbForNewVault): `src/shared/db/index.ts`
- Filesystem interface: `src/shared/platform/filesystem/filesystem.interface.ts`
- Desktop FS adapter: `src/shared/platform/filesystem/filesystem.desktop.ts`
- Android FS adapter: `src/shared/platform/filesystem/filesystem.android.ts`
- VaultSetupPage (pattern reference): `src/pages/vault-setup-page/ui/VaultSetupPage.tsx`
- Router (route addition): `src/app/router.tsx`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Implemented `copyFile` on both desktop (Tauri `copyFile`) and Android (`Filesystem.copy`) adapters following the absolute-path pattern established in story 2-2.
- `relocateVaultDesktop` copies books first, then resets the DB connection, copies `lekto.db`, and restores the original DB connection on any failure after `resetDb()` is called.
- Android migration is intentionally not supported in this story — `handleConfirmMigrate` on Android shows an inline error and returns to idle.
- The `/settings` route reuses `libraryLoader` (vault-guard) to prevent access with no configured vault.
- `useVaultStore` mock in `SettingsPage.test.tsx` uses `as never` cast to satisfy the typed selector signature at build time.
- 215 tests pass across 25 test files; lint, typecheck, and build all clean.

### Review Follow-ups (AI)

- [ ] [AI-Review][HIGH] `relocateVaultDesktop`: add `await initDbForNewVault(currentVaultPath)` rollback before returning `err` after `runMigrations()` failure — DB connection is already at new vault but store/prefs still point to old path [src/features/sync-vault/model/sync-vault.ts:113]
- [ ] [AI-Review][MEDIUM] Add test: `relocateVaultDesktop — runMigrations fails → err returned and original DB restored` to cover the H1 failure path [src/features/sync-vault/sync-vault.test.ts]
- [ ] [AI-Review][LOW] `SettingsPage`: `exists()` returning `err` silently falls into `confirm-migrate`; consider setting an inline error instead [src/pages/settings-page/ui/SettingsPage.tsx:23]
- [ ] [AI-Review][LOW] `SettingsPage`: call `setPickedPath('')` on successful migration/switch (currently only reset by `handleCancel`) [src/pages/settings-page/ui/SettingsPage.tsx:41]
- [ ] [AI-Review][LOW] `sync-vault.test.ts`: replace hardcoded `'vault_path'` string with imported `VAULT_PATH_KEY` constant [src/features/sync-vault/sync-vault.test.ts:76]

### File List

- src/shared/platform/filesystem/filesystem.interface.ts
- src/shared/platform/filesystem/filesystem.desktop.ts
- src/shared/platform/filesystem/filesystem.android.ts
- src/shared/platform/filesystem/filesystem.desktop.test.ts
- src/shared/platform/filesystem/filesystem.android.test.ts
- src/shared/platform/filesystem/filesystem.index.test.ts
- src/features/sync-vault/model/sync-vault.ts
- src/features/sync-vault/sync-vault.test.ts
- src/features/sync-vault/index.ts
- src/features/index.ts
- src/pages/settings-page/index.ts
- src/pages/settings-page/ui/SettingsPage.tsx
- src/pages/settings-page/ui/SettingsPage.test.tsx
- src/pages/index.ts
- src/app/router.tsx
- src/app/router.test.tsx
- src/pages/library-page/ui/LibraryPage.tsx
