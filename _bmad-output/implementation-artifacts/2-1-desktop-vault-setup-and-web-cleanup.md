# Story 2.1-desktop: Desktop Vault Setup & Web Adapter Cleanup

Status: review

## Story

As a developer shipping Lekto for Desktop and Android,
I want the vault setup flow to work correctly on Tauri desktop
and all dead web-specific code removed,
so that the codebase reflects the actual two-platform architecture (Tauri + Capacitor/Android) with no OPFS/browser-handle artefacts.

## Acceptance Criteria

1. **Given** the app runs on Tauri desktop and no vault is configured **When** `VaultSetupPage` mounts **Then** the default vault path shown is `{documentDir}/lekto-vault` (resolved via `@tauri-apps/api/path`) — not an OPFS sentinel

2. **Given** the user is on `VaultSetupPage` (desktop) and clicks "Modify" **When** the picker opens **Then** `filePickerAdapter.pickDirectory()` is called (Tauri native dialog) and the selected absolute path replaces the default

3. **Given** the user confirms vault creation on desktop **When** `initVaultDesktop(vaultPath)` runs **Then**: the directory `{vaultPath}/books` is created via `filesystemAdapter.mkdir`, the vault path is persisted in preferences, the DB is initialized + migrated + seeded, `useVaultStore.setVaultPath(vaultPath)` is called, and the app navigates to `/library`

4. **Given** vault creation completes on desktop **When** the app is relaunched **Then** `main.tsx` reads the stored absolute path and sets it in the store — the vault setup screen is skipped and the library is shown directly

5. **Given** the app runs on Android **When** vault setup is used **Then** all existing Android behaviors are unchanged: default `lekto-vault`, Modify via SAF picker, confirm via `initVault`, DB init in `main.tsx`

6. **Given** the cleanup is applied **Then** `filesystem.web.ts` and `filesystem.web.test.ts` no longer exist in the codebase

7. **Given** the cleanup is applied **Then** `handle-store.ts` and `handle-store.test.ts` no longer exist in the codebase

8. **Given** the cleanup is applied **Then** `preferences.web.ts` and `preferences.web.test.ts` are renamed to `preferences.android.ts` / `preferences.android.test.ts` with the factory renamed `createAndroidPreferencesAdapter`

9. **Given** the cleanup is applied **Then** `useVaultStore` no longer has `pendingPermissionHandle` or `setPendingPermissionHandle`

10. **Given** the cleanup is applied **Then** `sync-vault` model no longer exports `WEB_OPFS_PATH`, `WEB_NATIVE_PATH`, `initVaultWithNativeHandle`, `loadAndRestoreVaultHandle`, or `grantVaultPermission`

11. **Given** the cleanup is applied **Then** `VaultSetupPage` contains no `showDirectoryPicker` call and no permission re-grant UI

12. **Given** the cleanup is applied **Then** `main.tsx` contains no `WEB_NATIVE_PATH` branching and no `loadAndRestoreVaultHandle` call

13. **Given** all changes **Then** `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build` all pass with zero errors

## Tasks / Subtasks

### Commit 1: `refactor(platform): remove dead web filesystem adapter and setFilesystemRoot`

- [x] Task 1: Delete `filesystem.web.ts` and `filesystem.web.test.ts` (AC: #6)
  - [x] Delete `src/shared/platform/filesystem/filesystem.web.ts`
  - [x] Delete `src/shared/platform/filesystem/filesystem.web.test.ts`

- [x] Task 2: Clean up `src/shared/platform/filesystem/index.ts` (AC: #6)
  - [x] Remove `import { setWebFilesystemRoot } from './filesystem.web'`
  - [x] Remove the `setFilesystemRoot` export function entirely
  - [x] Final file exports only: `FilesystemAdapter` type + `filesystemAdapter` instance

- [x] Task 3: Remove `setFilesystemRoot` from `src/shared/platform/index.ts` (AC: #6)
  - [x] Change `export { filesystemAdapter, setFilesystemRoot } from './filesystem'` to `export { filesystemAdapter } from './filesystem'`

- [x] Task 4: Update `src/shared/platform/filesystem/filesystem.index.test.ts` if it tests `setFilesystemRoot` (AC: #6)
  - [x] Remove any test that imports or tests `setFilesystemRoot`

- [x] Task 5: Quality gate — commit 1
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 2: `refactor(platform): rename preferences.web to preferences.android`

- [x] Task 6: Rename and update preferences web adapter (AC: #8)
  - [x] Rename `src/shared/platform/preferences/preferences.web.ts` → `preferences.android.ts`
  - [x] In the new file, rename `createWebPreferencesAdapter` → `createAndroidPreferencesAdapter`
  - [x] Rename `src/shared/platform/preferences/preferences.web.test.ts` → `preferences.android.test.ts`
  - [x] Update import in test file to match new factory name
  - [x] Update `src/shared/platform/preferences/index.ts`:
    - Change import: `from './preferences.web'` → `from './preferences.android'`
    - Change call: `createWebPreferencesAdapter()` → `createAndroidPreferencesAdapter()`

- [x] Task 7: Quality gate — commit 2
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 3: `refactor(sync-vault): remove web-only vault logic and handle-store`

- [x] Task 8: Delete handle-store files (AC: #7)
  - [x] Delete `src/features/sync-vault/lib/handle-store.ts`
  - [x] Delete `src/features/sync-vault/lib/handle-store.test.ts`

- [x] Task 9: Rewrite `src/features/sync-vault/model/sync-vault.ts` (AC: #10)
  - [x] Remove imports: `setFilesystemRoot`, `storeVaultHandle`, `loadVaultHandle`
  - [x] Remove constants: `WEB_OPFS_PATH`, `WEB_NATIVE_PATH`
  - [x] Add constant: `export const DESKTOP_DEFAULT_VAULT_NAME = 'lekto-vault'`
  - [x] Simplify `initVault(path: string)` — remove the `if (path === WEB_OPFS_PATH) setFilesystemRoot(null)` line and the `if (isTauri())` DB init block (DB init moves to `initVaultDesktop`):
    ```typescript
    export async function initVault(path: string): AsyncResult<void> {
      try {
        const mkdirResult = await filesystemAdapter.mkdir('books')
        if (mkdirResult.isErr()) return err(mkdirResult.error)
        const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, path)
        if (setResult.isErr()) return err(setResult.error)
        useVaultStore.getState().setVaultPath(path)
        return ok(undefined)
      } catch (e) {
        return err(`Failed to init vault: ${e}`)
      }
    }
    ```
  - [x] Remove functions: `initVaultWithNativeHandle`, `loadAndRestoreVaultHandle`, `grantVaultPermission`
  - [x] Remove `isTauri` import (no longer needed in this file)
  - [x] Add `initVaultDesktop(vaultPath: string): AsyncResult<void>`:
    ```typescript
    export async function initVaultDesktop(vaultPath: string): AsyncResult<void> {
      try {
        const mkdirResult = await filesystemAdapter.mkdir(`${vaultPath}/books`)
        if (mkdirResult.isErr()) return err(mkdirResult.error)

        const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, vaultPath)
        if (setResult.isErr()) return err(setResult.error)

        const dbResult = await initDb()
        if (dbResult.isErr()) return err(`DB init failed: ${dbResult.error}`)
        if (dbResult.value === null) return err('DB init returned null unexpectedly')

        const migrationsResult = await runMigrations()
        if (migrationsResult.isErr()) return err(`DB migration failed: ${migrationsResult.error}`)

        const seedResult = await seedLanguages()
        if (seedResult.isErr()) return err(`DB seed failed: ${seedResult.error}`)

        useVaultStore.getState().setVaultPath(vaultPath)
        return ok(undefined)
      } catch (e) {
        return err(`Failed to init desktop vault: ${e}`)
      }
    }
    ```

- [x] Task 10: Update `src/features/sync-vault/index.ts` (AC: #10)
  - [x] Remove exports: `WEB_OPFS_PATH`, `WEB_NATIVE_PATH`, `initVaultWithNativeHandle`, `loadAndRestoreVaultHandle`, `grantVaultPermission`
  - [x] Add exports: `DESKTOP_DEFAULT_VAULT_NAME`, `initVaultDesktop`

- [x] Task 11: Update `src/features/index.ts`
  - [x] Remove re-exports of removed symbols; add `DESKTOP_DEFAULT_VAULT_NAME`, `initVaultDesktop`

- [x] Task 12: Rewrite `src/features/sync-vault/model/sync-vault.test.ts`
  - [x] Remove tests for: `initVaultWithNativeHandle`, `loadAndRestoreVaultHandle`, `grantVaultPermission`
  - [x] Keep tests for: `getVaultPath`, `isVaultConfigured`, `initVault`
  - [x] Add tests for `initVaultDesktop`:
    - Success path: mkdir called with `${vaultPath}/books`, preference set, DB init/migrate/seed called, setVaultPath called
    - mkdir failure → returns err, no preference stored
    - DB init failure → returns err

- [x] Task 13: Quality gate — commit 3
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 4: `refactor(store): remove pendingPermissionHandle from useVaultStore`

- [x] Task 14: Update `src/shared/stores/use-vault-store.ts` (AC: #9)
  - [x] Remove `pendingPermissionHandle: FileSystemDirectoryHandle | null` from interface
  - [x] Remove `setPendingPermissionHandle` action
  - [x] Remove `pendingPermissionHandle: null` from initial state
  - [x] Update `setVaultPath` — remove `pendingPermissionHandle: null` from its set call (field no longer exists)
  - [x] Update `clearVault` — remove `pendingPermissionHandle: null` from its set call
  - [x] Updated interface:
    ```typescript
    interface VaultState {
      vaultPath: string | null
      isVaultReady: boolean
      setVaultPath: (path: string) => void
      clearVault: () => void
    }
    ```

- [x] Task 15: Update `src/shared/stores/use-vault-store.test.ts`
  - [x] Remove all tests involving `pendingPermissionHandle` / `setPendingPermissionHandle`
  - [x] Verify remaining tests pass

- [x] Task 16: Quality gate — commit 4
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 5: `refactor(app): simplify vault hydration in main.tsx and router`

- [x] Task 17: Simplify `src/main.tsx` (AC: #12)
  - [x] Remove imports: `WEB_NATIVE_PATH`, `loadAndRestoreVaultHandle`, `Capacitor`
  - [x] Replace vault hydration block with:
    ```typescript
    const vaultPathResult = await getVaultPath()
    if (vaultPathResult.isOk()) {
      useVaultStore.getState().setVaultPath(vaultPathResult.value)
    }
    ```
  - [x] Note: DB init for desktop happens inside `initVaultDesktop` at vault creation time, not at startup (the startup `initDb()` call already present in main.tsx handles the case where vault is already configured)

- [x] Task 18: Simplify `src/app/router.tsx` (AC: #9)
  - [x] Remove `pendingPermissionHandle` from `rootLoader`:
    ```typescript
    export function rootLoader() {
      const { vaultPath } = useVaultStore.getState()
      if (vaultPath) return redirect('/library')
      return redirect('/vault-setup')
    }
    ```

- [x] Task 19: Quality gate — commit 5
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass

### Commit 6: `feat(page): add Tauri desktop branch to VaultSetupPage`

- [x] Task 20: Rewrite `src/pages/vault-setup-page/ui/VaultSetupPage.tsx` (AC: #1, #2, #3, #11)
  - [x] Remove imports: `Capacitor`, `WEB_OPFS_PATH`, `initVaultWithNativeHandle`, `grantVaultPermission`, `DEFAULT_ANDROID_PATH`
  - [x] Add imports: `isTauri` from `'@/shared/platform'`, `documentDir` from `'@tauri-apps/api/path'`, `DESKTOP_DEFAULT_VAULT_NAME`, `initVaultDesktop`
  - [x] Remove `FlowState` variant `'grant-permission'` — only `'idle' | 'create' | 'creating'` remain
  - [x] Remove `selectedHandle` state (FileSystemDirectoryHandle — web only)
  - [x] Remove `isGrantPermission` derived variable
  - [x] Remove `pendingPermissionHandle` from store subscription
  - [x] Remove `handleGrantPermission` function
  - [x] Remove grant-permission render branch
  - [x] Add `const isDesktop = isTauri()` constant
  - [x] **Default path resolution** (desktop only, in `useEffect`):
    ```typescript
    useEffect(() => {
      if (isDesktop) {
        documentDir().then((dir) => {
          const defaultPath = `${dir}/${DESKTOP_DEFAULT_VAULT_NAME}`
          setSelectedPath(defaultPath)
          setSelectedLabel(defaultPath)
        })
      }
    }, [])
    ```
  - [x] **Default path for Android** (unchanged): `'lekto-vault'` label = `'lekto-vault'`
  - [x] **`handleModify`**: both desktop and Android use `filePickerAdapter.pickDirectory()` — remove `showDirectoryPicker` branch:
    ```typescript
    async function handleModify() {
      setError(null)
      const result = await filePickerAdapter.pickDirectory()
      if (result.isOk()) {
        setSelectedPath(result.value)
        setSelectedLabel(result.value)
      }
    }
    ```
  - [x] Show "Modify" button when `isDesktop || !isDesktop` (always — both platforms support picker) — simplify to always show in `create` state
  - [x] **`handleConfirm`**: dispatch to `initVaultDesktop` on desktop, `initVault` on Android:
    ```typescript
    async function handleConfirm() {
      if (flowState === 'creating') return
      setError(null)
      setFlowState('creating')
      const result = isDesktop
        ? await initVaultDesktop(selectedPath)
        : await initVault(selectedPath)
      if (result.isOk()) {
        navigate('/library')
      } else {
        setError(result.error)
        setFlowState('create')
      }
    }
    ```
  - [x] Initialize `selectedPath` and `selectedLabel` as `''` (desktop resolves async in useEffect); Android initializes to `DEFAULT_ANDROID_PATH` — use lazy init:
    ```typescript
    const [selectedPath, setSelectedPath] = useState(() =>
      isDesktop ? '' : DEFAULT_ANDROID_PATH,
    )
    const [selectedLabel, setSelectedLabel] = useState(() =>
      isDesktop ? 'Resolving default location…' : DEFAULT_ANDROID_PATH,
    )
    ```

- [x] Task 21: Rewrite `src/pages/vault-setup-page/ui/VaultSetupPage.test.tsx`
  - [x] Remove tests: permission re-grant state, `showDirectoryPicker`, `pendingPermissionHandle` in store, `grantVaultPermission`, `initVaultWithNativeHandle`
  - [x] Keep/update tests:
    - Initial render shows both buttons; "Open existing vault" is disabled
    - "Create new vault" → create state, Confirm present
    - Confirm (Android path) → calls `initVault` → on success navigates
    - `initVault` error → inline error shown, no navigation
  - [x] Add desktop tests (mock `isTauri` to return `true`):
    - Mount on desktop → `documentDir` called, path resolved and shown
    - "Modify" on desktop → `filePickerAdapter.pickDirectory()` called, path updated
    - Confirm on desktop → calls `initVaultDesktop` with resolved path → on success navigates
    - `initVaultDesktop` error → inline error, no navigation

- [x] Task 22: Quality gate — commit 6 (final)
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass
  - [x] `npm run build` — succeeds

## Dev Notes

### Architecture Context

The target platform architecture (per `architecture.md`) is **two platforms only**: Android (Capacitor) + Desktop (Tauri). There is no web distribution. All `.web.ts` adapter files are transitional artefacts from the initial web-first prototype.

- `filesystem.web.ts` was already removed from `filesystem/index.ts` in Epic 8 — it's dead code
- `handle-store.ts` (IndexedDB) is exclusively a browser API workaround for `FileSystemDirectoryHandle` persistence — meaningless on Tauri
- The permission re-grant problem (`requestPermission()` after browser restart) is a browser security constraint that does not apply to Tauri (native OS file access is permanent once granted)

### Platform Detection Pattern

```typescript
// shared/platform/is-tauri.ts — already exists
import { isTauri } from '@/shared/platform'

// Two-platform branch:
if (isTauri()) {
  // Desktop (Tauri) path
} else {
  // Android (Capacitor) path
}
```

`Capacitor.isNativePlatform()` can be removed from `VaultSetupPage` — since web is gone, `!isTauri()` is equivalent.

### Desktop Vault Path

```typescript
import { documentDir } from '@tauri-apps/api/path'
// @tauri-apps/api is already in package.json

const dir = await documentDir()  // e.g. '/home/user/Documents' on Linux
const vaultPath = `${dir}/${DESKTOP_DEFAULT_VAULT_NAME}`  // '/home/user/Documents/lekto-vault'
```

On Windows, `documentDir()` returns the user's Documents folder (e.g. `C:\Users\User\Documents`). Tauri's `plugin-fs` handles OS path separators natively — no manual `path.sep` needed.

### initVaultDesktop — Path Handling

```typescript
await filesystemAdapter.mkdir(`${vaultPath}/books`)
```

The desktop filesystem adapter calls `@tauri-apps/plugin-fs`'s `mkdir` with `{ recursive: true }`. On Linux/macOS the `/` separator works. On Windows, Tauri normalizes slashes internally. No path.join import needed.

### main.tsx — Desktop DB Init

On desktop, `initDb()` at startup returns `null` when no vault is configured yet (the DB file lives inside the vault directory which doesn't exist). The `if (db.value !== null)` guard in `main.tsx` already handles this — migrations/seed are skipped until vault creation. `initVaultDesktop` calls `initDb()` after creating the vault directory.

On Android, `initDb()` at startup always succeeds (DB in Capacitor's app data dir, not in vault). No change needed.

### Mocking isTauri in Tests

```typescript
vi.mock('@/shared/platform', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/platform')>()
  return { ...original, isTauri: vi.fn().mockReturnValue(true) }
})
```

Mock `documentDir` from `@tauri-apps/api/path`:
```typescript
vi.mock('@tauri-apps/api/path', () => ({
  documentDir: vi.fn().mockResolvedValue('/home/user/Documents'),
}))
```

### Files Deleted by This Story

- `src/shared/platform/filesystem/filesystem.web.ts`
- `src/shared/platform/filesystem/filesystem.web.test.ts`
- `src/features/sync-vault/lib/handle-store.ts`
- `src/features/sync-vault/lib/handle-store.test.ts`

### Files Renamed by This Story

- `preferences.web.ts` → `preferences.android.ts` (factory: `createWebPreferencesAdapter` → `createAndroidPreferencesAdapter`)
- `preferences.web.test.ts` → `preferences.android.test.ts`

### File List (Changes Summary)

**Deleted:**
- `src/shared/platform/filesystem/filesystem.web.ts`
- `src/shared/platform/filesystem/filesystem.web.test.ts`
- `src/features/sync-vault/lib/handle-store.ts`
- `src/features/sync-vault/lib/handle-store.test.ts`

**Renamed:**
- `src/shared/platform/preferences/preferences.web.ts` → `preferences.android.ts`
- `src/shared/platform/preferences/preferences.web.test.ts` → `preferences.android.test.ts`

**Modified:**
- `src/shared/platform/filesystem/index.ts`
- `src/shared/platform/index.ts`
- `src/shared/platform/preferences/index.ts`
- `src/shared/stores/use-vault-store.ts`
- `src/shared/stores/use-vault-store.test.ts`
- `src/features/sync-vault/model/sync-vault.ts`
- `src/features/sync-vault/model/sync-vault.test.ts`
- `src/features/sync-vault/index.ts`
- `src/features/index.ts`
- `src/pages/vault-setup-page/ui/VaultSetupPage.tsx`
- `src/pages/vault-setup-page/ui/VaultSetupPage.test.tsx`
- `src/main.tsx`
- `src/app/router.tsx`

### References

- Architecture target state: `_bmad-output/planning-artifacts/architecture.md` — "Platform adapter structure (target state)"
- Web cleanup rationale: `architecture.md` — "Migration note: The current codebase contains `.web.ts` adapter files inherited from the initial web-first implementation. These are transitional..."
- Filesystem adapter (desktop): `src/shared/platform/filesystem/filesystem.desktop.ts`
- File picker adapter (desktop): `src/shared/platform/file-picker/file-picker.desktop.ts`
- Original 2.1 story: `2-1-vault-setup-screen-and-create-new-vault.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation was straightforward.

### Completion Notes List

- Commits 1–6 implemented and committed in order per story structure.
- Commit 4 required also removing the grant-permission UI from VaultSetupPage (it subscribed to `pendingPermissionHandle` which was removed from the store), plus updating VaultSetupPage.test.tsx and router.test.tsx accordingly. This is a direct consequence of the store cleanup and aligned with Commit 6 goals.
- VaultSetupPage: `isDesktop = isTauri()` placed inside the component function (not module-level) to allow per-test mocking without module resets.
- Desktop test for `documentDir` requires clicking "Create new vault" first since the resolved path is only rendered in the create state.
- All 13 ACs verified: filesystem.web.ts deleted (AC6), handle-store deleted (AC7), preferences renamed (AC8), pendingPermissionHandle removed (AC9), web-only symbols removed (AC10), VaultSetupPage cleaned (AC11), main.tsx simplified (AC12), all quality gates pass (AC13).

### File List

**Deleted:**
- `src/shared/platform/filesystem/filesystem.web.ts`
- `src/shared/platform/filesystem/filesystem.web.test.ts`
- `src/features/sync-vault/lib/handle-store.ts`
- `src/features/sync-vault/lib/handle-store.test.ts`

**Renamed:**
- `src/shared/platform/preferences/preferences.web.ts` → `preferences.android.ts`
- `src/shared/platform/preferences/preferences.web.test.ts` → `preferences.android.test.ts`

**Modified:**
- `src/shared/platform/filesystem/index.ts`
- `src/shared/platform/filesystem/filesystem.index.test.ts`
- `src/shared/platform/index.ts`
- `src/shared/platform/preferences/index.ts`
- `src/shared/stores/use-vault-store.ts`
- `src/shared/stores/use-vault-store.test.ts`
- `src/features/sync-vault/model/sync-vault.ts`
- `src/features/sync-vault/model/sync-vault.test.ts`
- `src/features/sync-vault/index.ts`
- `src/features/index.ts`
- `src/pages/vault-setup-page/ui/VaultSetupPage.tsx`
- `src/pages/vault-setup-page/ui/VaultSetupPage.test.tsx`
- `src/main.tsx`
- `src/app/router.tsx`
- `src/app/router.test.tsx`

### Change Log

- 2026-04-08: Implemented story 2-1-desktop — 6 commits: removed web filesystem adapter, renamed preferences.android, removed handle-store and web-only vault logic, introduced initVaultDesktop, cleaned useVaultStore, simplified main.tsx/router, rewrote VaultSetupPage with Tauri desktop branch. All ACs satisfied, 22 test files, 155 tests passing.
