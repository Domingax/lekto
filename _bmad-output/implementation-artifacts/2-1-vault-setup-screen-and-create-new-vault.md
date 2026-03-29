# Story 2.1: Vault Setup Screen & Create New Vault

Status: ready-for-dev

## Story

As a first-time user,
I want to be guided through creating a vault on first launch,
so that my reading data has a persistent local home without any manual configuration required.

## Acceptance Criteria

1. **Given** the app has never been launched before (no vault path stored) **When** the app loads **Then** the `VaultSetupPage` is displayed — not the library — with two options: "Create new vault" (primary button) and "Open existing vault" (secondary button)

2. **Given** the app has a vault path already configured **When** the app loads **Then** the `VaultSetupPage` is skipped and the library is displayed directly

3. **Given** the user is on the `VaultSetupPage` and selects "Create new vault" **When** the create flow opens **Then** the default vault path is displayed with a "Modify" option before confirmation — no file picker is opened automatically

4. **Given** the user confirms the default vault path **When** vault creation runs **Then** the vault folder is created with a `books/` subdirectory, the DB is already initialized (done in `main.tsx`), and the vault path is persisted in preferences for future sessions

5. **Given** vault creation completes successfully **When** the app transitions **Then** the `VaultSetupPage` is dismissed and the empty library is displayed — the setup screen is never shown again on subsequent launches

6. **Given** the app runs on Chrome or Edge (where `showDirectoryPicker` is available) **When** the user taps "Modify" **Then** `showDirectoryPicker()` opens, the user selects a native folder (e.g. a cloud-synced directory), and the selected folder name replaces the default in the confirmation view — vault will be created there, enabling sync via Google Drive, Syncthing, etc.

7. **Given** the app runs on Firefox or Safari (where `showDirectoryPicker` is not available) **When** vault creation runs **Then** the vault is created in OPFS without error and the "Modify" button is not shown — the user is not prompted for a folder

8. **Given** the user previously chose a native folder (Chrome/Edge), closes and reopens the browser **When** the app loads **Then** a permission re-grant screen is shown asking the user to click to restore access to their vault folder — the browser requires explicit user interaction to re-grant file system permissions after a session ends

9. **Given** the user clicks "Restore vault access" on the permission re-grant screen **When** `requestPermission()` succeeds **Then** the vault handle is restored, the filesystem adapter is re-initialized, and the app proceeds to the library

10. **Given** the app runs on Android **When** the user taps "Modify" to choose a vault location **Then** the SAF file picker opens and the selected folder path is stored as the vault path

11. **Given** the user is on the `VaultSetupPage` **When** they tap "Open existing vault" **Then** the button is visible but non-functional (stub for Story 2.2) — it renders without crashing

## Tasks / Subtasks

### Commit 1: `feat(platform): add preferences adapter for non-secret key-value storage`

- [ ] Task 1: Create `src/shared/platform/preferences/` adapter (AC: #4, #2)
  - [ ] Create `src/shared/platform/preferences/preferences.interface.ts`:
    ```typescript
    export interface PreferencesAdapter {
      get(key: string): AsyncResult<string>
      set(key: string, value: string): AsyncResult<void>
      remove(key: string): AsyncResult<void>
    }
    ```
  - [ ] Create `src/shared/platform/preferences/preferences.web.ts` — wraps `localStorage` via neverthrow ok/err; return `err('Not found')` when key absent
  - [ ] Create `src/shared/platform/preferences/preferences.android.ts` — same localStorage wrapper (Capacitor WebView preserves localStorage; `@capacitor/preferences` deferred to Story 7)
  - [ ] Create `src/shared/platform/preferences/index.ts` — exports `preferencesAdapter`; both implementations are identical so always use the web version
  - [ ] Create `src/shared/platform/preferences/preferences.web.test.ts` — set/get/remove, missing key returns err
  - [ ] Create `src/shared/platform/preferences/preferences.android.test.ts` — same (mock localStorage)
  - [ ] Add export to `src/shared/platform/index.ts`

- [ ] Task 2: Quality gate — commit 1
  - [ ] `npm run lint` — zero warnings
  - [ ] `npm run typecheck` — zero errors
  - [ ] `npm run test` — all tests pass

### Commit 2: `feat(store): add useVaultStore Zustand store`

- [ ] Task 3: Create `src/shared/stores/use-vault-store.ts` (AC: #2, #5, #8, #9)
  - [ ] Shape:
    ```typescript
    interface VaultState {
      vaultPath: string | null
      isVaultReady: boolean
      // Web-only: handle pending permission re-grant after browser restart
      pendingPermissionHandle: FileSystemDirectoryHandle | null
      setVaultPath: (path: string) => void
      setPendingPermissionHandle: (handle: FileSystemDirectoryHandle | null) => void
      clearVault: () => void
    }
    ```
  - [ ] Initial state: `{ vaultPath: null, isVaultReady: false, pendingPermissionHandle: null }`
  - [ ] `setVaultPath(path)` → sets `vaultPath`, `isVaultReady: true`, clears `pendingPermissionHandle`
  - [ ] `setPendingPermissionHandle(handle)` → sets `pendingPermissionHandle`
  - [ ] Use Zustand `create()` — no persistence middleware
  - [ ] Create `src/shared/stores/use-vault-store.test.ts` — test initial state and all actions
  - [ ] Export from `src/shared/stores/index.ts`

- [ ] Task 4: Quality gate — commit 2
  - [ ] `npm run lint` — zero warnings
  - [ ] `npm run typecheck` — zero errors
  - [ ] `npm run test` — all tests pass

### Commit 3: `feat(platform): extend web filesystem adapter for native folder mode`

- [ ] Task 5: Extend `src/shared/platform/filesystem/filesystem.web.ts` (AC: #6, #9)
  - [ ] Add module-level mutable root variable:
    ```typescript
    let _nativeRoot: FileSystemDirectoryHandle | null = null
    ```
  - [ ] Export `setWebFilesystemRoot(handle: FileSystemDirectoryHandle | null): void` — sets `_nativeRoot`
  - [ ] Update internal `getRoot()` function:
    ```typescript
    function getRoot(): Promise<FileSystemDirectoryHandle> {
      if (_nativeRoot) return Promise.resolve(_nativeRoot)
      return navigator.storage.getDirectory()
    }
    ```
  - [ ] All existing adapter methods remain unchanged — they use `getRoot()` internally, so they automatically benefit from native folder mode when `_nativeRoot` is set
  - [ ] Add `setWebFilesystemRoot` test: call it with a mock handle, verify subsequent `getRoot()` returns it; call with `null`, verify OPFS fallback

- [ ] Task 6: Export `setFilesystemRoot` from `src/shared/platform/filesystem/index.ts` (AC: #6, #9)
  - [ ] Add a platform-aware wrapper:
    ```typescript
    export function setFilesystemRoot(handle: FileSystemDirectoryHandle | null): void {
      if (!Capacitor.isNativePlatform()) {
        setWebFilesystemRoot(handle)
      }
    }
    ```
  - [ ] This is always exported and safe to call anywhere — no-op on Android

- [ ] Task 7: Create `src/features/sync-vault/lib/handle-store.ts` — IndexedDB handle persistence (AC: #6, #8, #9)
  - [ ] Constants: `HANDLE_DB_NAME = 'lekto-meta'`, `HANDLE_STORE_NAME = 'handles'`, `VAULT_HANDLE_KEY = 'vault'`
  - [ ] Exports:
    - `storeVaultHandle(handle: FileSystemDirectoryHandle): Promise<void>`
    - `loadVaultHandle(): Promise<FileSystemDirectoryHandle | null>` — returns null on any error
    - `clearVaultHandle(): Promise<void>`
  - [ ] All functions use raw IndexedDB API (`indexedDB.open`, transactions) — no external library
  - [ ] `loadVaultHandle` catches all errors and returns null (IDB unavailable, store not initialized, etc.)
  - [ ] Create `src/features/sync-vault/lib/handle-store.test.ts` — mock `indexedDB` global; test store/load/clear cycle, load returns null when nothing stored

- [ ] Task 8: Quality gate — commit 3
  - [ ] `npm run lint` — zero warnings
  - [ ] `npm run typecheck` — zero errors
  - [ ] `npm run test` — all tests pass

### Commit 4: `feat(feature): add sync-vault business logic`

- [ ] Task 9: Create `src/features/sync-vault/model/sync-vault.ts` (AC: #4, #6, #7, #8, #9)
  - [ ] Constants:
    - `VAULT_PATH_KEY = 'vault_path'`
    - `WEB_OPFS_PATH = '__opfs__'` — sentinel for OPFS vault
    - `WEB_NATIVE_PATH = '__native__'` — sentinel for native folder vault (Chrome/Edge)
    - `DEFAULT_ANDROID_PATH = 'lekto-vault'`
  - [ ] Exports:
    - `getVaultPath(): AsyncResult<string>` — reads from `preferencesAdapter`; `err` if not set
    - `isVaultConfigured(): Promise<boolean>` — true if getVaultPath succeeds
    - `initVault(path: string): AsyncResult<void>` — for OPFS (web) and Android; calls `filesystemAdapter.mkdir('books')`, persists path, updates store
    - `initVaultWithNativeHandle(handle: FileSystemDirectoryHandle): AsyncResult<void>` — Chrome/Edge only; calls `storeVaultHandle(handle)`, `setFilesystemRoot(handle)`, `filesystemAdapter.mkdir('books')`, persists `WEB_NATIVE_PATH`, updates store
    - `loadAndRestoreVaultHandle(): Promise<'ok' | 'needs-permission' | 'not-found'>` — called on startup for native vaults; loads handle from IndexedDB, checks permission via `handle.queryPermission({ mode: 'readwrite' })`:
      - `'granted'` → calls `setFilesystemRoot(handle)` + `useVaultStore.getState().setVaultPath(WEB_NATIVE_PATH)` → returns `'ok'`
      - `'prompt'` → calls `useVaultStore.getState().setPendingPermissionHandle(handle)` → returns `'needs-permission'`
      - handle is null → returns `'not-found'`
    - `grantVaultPermission(handle: FileSystemDirectoryHandle): AsyncResult<void>` — calls `handle.requestPermission({ mode: 'readwrite' })`; on granted: `setFilesystemRoot(handle)` + updates store; on denied: returns `err`
  - [ ] Create `src/features/sync-vault/model/sync-vault.test.ts` — mock all adapters and store; test each export; for `loadAndRestoreVaultHandle` mock the handle's `queryPermission` method
  - [ ] Create `src/features/sync-vault/index.ts` — barrel export of all public functions and constants
  - [ ] Export from `src/features/index.ts`

- [ ] Task 10: Quality gate — commit 4
  - [ ] `npm run lint` — zero warnings
  - [ ] `npm run typecheck` — zero errors
  - [ ] `npm run test` — all tests pass

### Commit 5: `feat(page): add VaultSetupPage UI`

- [ ] Task 11: Create `src/pages/vault-setup-page/ui/VaultSetupPage.tsx` (AC: #1, #3, #4, #5, #6, #7, #8, #9, #10, #11)
  - [ ] **State machine** (React `useState`): `'idle' | 'create' | 'creating' | 'grant-permission'`
    - `idle`: initial screen — "Create new vault" primary + "Open existing vault" secondary (disabled stub)
    - `create`: path display + optional Modify + Confirm + Cancel
    - `creating`: loading indicator, buttons disabled
    - `grant-permission`: permission re-grant screen (see below)
  - [ ] **On mount**: check `useVaultStore(state => state.pendingPermissionHandle)` — if non-null, set state to `'grant-permission'` immediately
  - [ ] **`grant-permission` state UI**: message explaining why re-grant is needed + single primary button "Restore vault access" — on click calls `grantVaultPermission(pendingPermissionHandle)` → on success navigate to library, on error show inline error
  - [ ] **"Modify" display logic** (in `create` state):
    - Android (`Capacitor.isNativePlatform()`): show "Modify" button → calls `filePickerAdapter.pickDirectory()` → updates `selectedPath` state
    - Web Chrome/Edge (`typeof showDirectoryPicker === 'function'` AND not native): show "Choose folder" button → calls `showDirectoryPicker()` → stores handle in local state as `selectedHandle` + updates displayed path to `handle.name`
    - Web Firefox/Safari: neither condition met → no Modify button, display `"Built-in storage (OPFS)"`
  - [ ] **Default path initialization**:
    - Android: `DEFAULT_ANDROID_PATH`
    - Web Chrome/Edge: `"Built-in storage (OPFS)"` (label only; user can choose different via Modify)
    - Web Firefox/Safari: `"Built-in storage (OPFS)"` (static, no Modify)
  - [ ] **"Confirm" logic**:
    - Sets state to `'creating'`
    - If `selectedHandle` is set (Chrome/Edge native folder): calls `initVaultWithNativeHandle(selectedHandle)`
    - Otherwise: calls `initVault(selectedPath)`
    - On success: navigates to `/library` via `useNavigate()`
    - On error: reverts to `'create'`, shows inline error
  - [ ] **Props**: none — uses `useNavigate()` internally
  - [ ] Create `src/pages/vault-setup-page/index.ts` — barrel export
  - [ ] Export from `src/pages/index.ts`
  - [ ] Create `src/pages/vault-setup-page/ui/VaultSetupPage.test.tsx`:
    - Mock `sync-vault` and adapters with `vi.mock`
    - Test: initial render shows both buttons; "Open existing vault" is disabled
    - Test: "Create new vault" → create state, Confirm button present
    - Test: Confirm → calls initVault → on success navigates
    - Test: initVault error → inline error shown, no navigation
    - Test: mount with `pendingPermissionHandle` in store → renders `grant-permission` state immediately
    - Test: "Restore vault access" click → calls `grantVaultPermission`

- [ ] Task 12: Quality gate — commit 5
  - [ ] `npm run lint` — zero warnings
  - [ ] `npm run typecheck` — zero errors
  - [ ] `npm run test` — all tests pass

### Commit 6: `feat(app): wire vault gate — React Router + App.tsx + library stub`

- [ ] Task 13: Create `src/app/router.tsx` (AC: #1, #2, #5, #8)
  - [ ] `createBrowserRouter` from `react-router-dom`
  - [ ] Root `/` loader reads `useVaultStore.getState()`:
    ```typescript
    loader: () => {
      const { vaultPath, pendingPermissionHandle } = useVaultStore.getState()
      if (pendingPermissionHandle) return redirect('/vault-setup')  // permission re-grant
      if (vaultPath) return redirect('/library')
      return redirect('/vault-setup')
    }
    ```
  - [ ] Route `/vault-setup` → `<VaultSetupPage />`
  - [ ] Route `/library` → `<LibraryPage />`

- [ ] Task 14: Update `src/App.tsx`
  - [ ] Replace placeholder with `<RouterProvider router={router} />`

- [ ] Task 15: Create `src/pages/library-page/` stub
  - [ ] `src/pages/library-page/ui/LibraryPage.tsx` — renders `<p>Library (coming soon)</p>`
  - [ ] `src/pages/library-page/index.ts` — barrel export
  - [ ] Add to `src/pages/index.ts`

- [ ] Task 16: Update `src/main.tsx` vault hydration (AC: #2, #8, #9)
  - [ ] After existing DB init/migrations/seed, add:
    ```typescript
    const vaultPathResult = await getVaultPath()
    if (vaultPathResult.isOk()) {
      const storedPath = vaultPathResult.value
      if (storedPath === WEB_NATIVE_PATH && !Capacitor.isNativePlatform()) {
        // Native folder vault — try to restore handle (may need permission re-grant)
        await loadAndRestoreVaultHandle()
        // Store is updated by loadAndRestoreVaultHandle:
        //   'ok' → setVaultPath(WEB_NATIVE_PATH) → router goes to /library
        //   'needs-permission' → setPendingPermissionHandle(handle) → router goes to /vault-setup
        //   'not-found' → vault broken, stay on /vault-setup
      } else {
        useVaultStore.getState().setVaultPath(storedPath)
      }
    }
    ```

- [ ] Task 17: Quality gate — commit 6 (final)
  - [ ] `npm run lint` — zero warnings
  - [ ] `npm run typecheck` — zero errors
  - [ ] `npm run test` — all tests pass
  - [ ] `npm run build` — succeeds
  - [ ] `npm run test:e2e` — smoke test passes
  - [ ] Manual verification: first launch → VaultSetupPage; after create → library; refresh → library directly; simulated permission re-grant via DevTools file system handle invalidation

## Dev Notes

### What Already Exists — Do NOT Recreate

| Component | Location | Notes |
|---|---|---|
| `filesystemAdapter` | `src/shared/platform/filesystem/` | OPFS (web) + Capacitor Filesystem (Android). **Modified by this story** to support dynamic root. |
| `filePickerAdapter` | `src/shared/platform/file-picker/` | `pickDirectory()` → `showDirectoryPicker()` on Chrome/Edge, SAF on Android. Already implemented. |
| `secureStorageAdapter` | `src/shared/platform/secure-storage/` | **Reserved for API keys only.** Do NOT use for vault path. |
| DB init + migrations | `src/main.tsx` | Already runs before React renders. This story does NOT re-initialize the DB. |
| Drizzle schema | `src/shared/db/schema.ts` | No schema changes in this story. |
| shadcn `Button` | `src/components/ui/button.tsx` | Import from `@/components/ui/button` — not `@/shared/ui` |
| Zustand v5 | `package.json` | Already installed |
| React Router DOM v7 | `package.json` | Already installed |

### Web Platform Behavior — Two Distinct Modes

**Mode 1 — OPFS (Firefox, Safari, and Chrome/Edge default)**

The existing `filesystem.web.ts` uses `navigator.storage.getDirectory()` — OPFS. This is sandboxed within the browser; invisible to the OS and to sync tools (Google Drive, Syncthing, etc.). No permission prompt, always available, completely automatic. Firefox officially opposes the File System Access API and will never implement `showDirectoryPicker()`.

Vault path sentinel stored: `'__opfs__'`
Display string: `"Built-in storage (OPFS)"`
"Modify" button: not shown

**Mode 2 — Native folder (Chrome/Edge only, when user clicks "Modify")**

`showDirectoryPicker()` lets the user select any local folder — including cloud-synced ones (e.g. `~/Google Drive/lekto-vault`). This enables cross-device sync between Chrome desktop and the Android app (which can access the same synced folder via SAF).

The returned `FileSystemDirectoryHandle` cannot be serialized to a string. It must be stored in **IndexedDB** (the browser's native mechanism for persisting handles between sessions). See `handle-store.ts`.

Vault path sentinel stored: `'__native__'`
Display string: the folder `handle.name` (e.g. `"lekto-vault"`)
"Modify" button: shown only when `typeof showDirectoryPicker === 'function'`

**Detection pattern in VaultSetupPage:**

```typescript
const isNative = Capacitor.isNativePlatform()
const hasDirectoryPicker = !isNative && typeof showDirectoryPicker === 'function'
// Show "Modify" button: isNative OR hasDirectoryPicker
// Use filePickerAdapter.pickDirectory() on Android, showDirectoryPicker() on Chrome/Edge
```

### Permission Re-Grant After Browser Restart (Critical Web Constraint)

After the user picks a native folder and closes/reopens the browser, the `FileSystemDirectoryHandle` stored in IndexedDB is still there **but the permission is gone**. The File System Access API requires an explicit user gesture to re-grant write permission (`requestPermission()` must be called from a click handler — it cannot be called from `main.tsx` or a loader).

**Startup flow for native vault:**

```
main.tsx
  → preferencesAdapter.get('vault_path') === '__native__'
  → loadVaultHandle() from IndexedDB
    → handle.queryPermission({ mode: 'readwrite' })
      → 'granted' (same session, permission still active)
          → setFilesystemRoot(handle) + setVaultPath('__native__')
          → router goes to /library ✅
      → 'prompt' (new session, needs re-grant)
          → setPendingPermissionHandle(handle)
          → router goes to /vault-setup (shows grant-permission state)
          → user clicks "Restore vault access"
          → handle.requestPermission({ mode: 'readwrite' }) (must be in click handler!)
          → setFilesystemRoot(handle) + setVaultPath('__native__')
          → navigate to /library ✅
      → 'denied' (user denied previously)
          → treat as vault not configured → /vault-setup (idle state)
```

The `grantVaultPermission()` function in sync-vault must be called from a click event handler in VaultSetupPage — **never from a useEffect or async init code**.

### filesystem.web.ts — Dynamic Root Design

The adapter uses a module-level variable `_nativeRoot`. This is intentional: adapters are singletons in this project (exported as instances, not factories). The pattern is equivalent to dependency injection at startup.

```typescript
let _nativeRoot: FileSystemDirectoryHandle | null = null

export function setWebFilesystemRoot(handle: FileSystemDirectoryHandle | null): void {
  _nativeRoot = handle
}

function getRoot(): Promise<FileSystemDirectoryHandle> {
  if (_nativeRoot) return Promise.resolve(_nativeRoot)
  return navigator.storage.getDirectory()
}
// All existing methods (readFile, writeFile, mkdir, etc.) use getRoot() — no changes needed
```

`setFilesystemRoot` in `filesystem/index.ts` wraps this with a platform guard so it's safe to import and call in sync-vault without platform-specific imports.

### handle-store.ts — IndexedDB Pattern

Use raw IndexedDB API (no wrapper library). The pattern:

```typescript
function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = () => req.result.createObjectStore(STORE_NAME)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}
```

`loadVaultHandle` catches all errors and returns `null` — IDB may be unavailable in some environments (private browsing, security policies). Never throw from this function.

**Mocking IndexedDB in Vitest/jsdom:** use the `fake-indexeddb` package (check if already installed; if not, add as devDependency):

```typescript
import 'fake-indexeddb/auto'
```

This replaces the global `indexedDB` with an in-memory implementation — no setup needed beyond the import.

### sync-vault Feature — initVaultWithNativeHandle Flow

```
initVaultWithNativeHandle(handle)
  1. storeVaultHandle(handle)           — persist in IndexedDB
  2. setFilesystemRoot(handle)          — configure adapter to use this folder
  3. filesystemAdapter.mkdir('books')  — create vault structure in the native folder
  4. preferencesAdapter.set(VAULT_PATH_KEY, WEB_NATIVE_PATH)
  5. useVaultStore.getState().setVaultPath(WEB_NATIVE_PATH)
```

**Important:** `setFilesystemRoot` must be called BEFORE `mkdir` so the adapter operates on the native folder, not OPFS.

### Mocking in Tests

```typescript
// sync-vault.test.ts
vi.mock('@/shared/platform/filesystem', () => ({
  filesystemAdapter: { mkdir: vi.fn().mockResolvedValue(ok(undefined)) },
  setFilesystemRoot: vi.fn(),
}))
vi.mock('@/shared/platform/preferences', () => ({
  preferencesAdapter: {
    get: vi.fn().mockResolvedValue(err('Not found')),
    set: vi.fn().mockResolvedValue(ok(undefined)),
  },
}))
vi.mock('@/shared/stores', () => ({
  useVaultStore: { getState: () => ({ setVaultPath: vi.fn(), setPendingPermissionHandle: vi.fn() }) },
}))
vi.mock('../lib/handle-store', () => ({
  storeVaultHandle: vi.fn().mockResolvedValue(undefined),
  loadVaultHandle: vi.fn().mockResolvedValue(null),
  clearVaultHandle: vi.fn().mockResolvedValue(undefined),
}))
```

### File Structure Created by This Story

```
src/
├── app/
│   └── router.tsx                               ← NEW
├── pages/
│   ├── library-page/                            ← NEW (stub)
│   │   ├── index.ts
│   │   └── ui/LibraryPage.tsx
│   └── vault-setup-page/                        ← NEW
│       ├── index.ts
│       └── ui/
│           ├── VaultSetupPage.tsx
│           └── VaultSetupPage.test.tsx
├── features/
│   └── sync-vault/                              ← NEW
│       ├── index.ts
│       ├── lib/
│       │   ├── handle-store.ts
│       │   └── handle-store.test.ts
│       └── model/
│           ├── sync-vault.ts
│           └── sync-vault.test.ts
└── shared/
    ├── platform/
    │   └── preferences/                         ← NEW
    │       ├── index.ts
    │       ├── preferences.interface.ts
    │       ├── preferences.web.ts
    │       ├── preferences.web.test.ts
    │       ├── preferences.android.ts
    │       └── preferences.android.test.ts
    └── stores/
        ├── use-vault-store.ts                   ← NEW
        └── use-vault-store.test.ts              ← NEW
```

**Files modified:**
- `src/shared/platform/filesystem/filesystem.web.ts` — add `_nativeRoot` + `setWebFilesystemRoot`
- `src/shared/platform/filesystem/index.ts` — export `setFilesystemRoot` wrapper
- `src/App.tsx` — replace placeholder with `<RouterProvider router={router} />`
- `src/main.tsx` — add vault hydration after seed
- `src/shared/stores/index.ts` — export `useVaultStore`
- `src/shared/platform/index.ts` — export `preferencesAdapter`
- `src/pages/index.ts` — export new pages
- `src/features/index.ts` — export `sync-vault`

### Previous Story Intelligence (Epic 1)

- **TypeScript `~5.9.3` strict mode + `noUncheckedIndexedAccess`** — all array accesses need optional chaining or null checks
- **`neverthrow`**: use `ok()`/`err()` consistently; never throw across module boundaries
- **shadcn `Button`** in `src/components/ui/button.tsx`, import from `'@/components/ui/button'`
- **Zustand v5**: `create()` factory; `useVaultStore.getState()` for imperative reads outside React
- **Vitest mocking**: `vi.mock()` at module level; `vi.fn()` for stubs
- **FSD barrel rule**: every slice exposes only `index.ts` — consumers never import from internal paths

### References

- Story requirements: [Source: `_bmad-output/planning-artifacts/epics.md` — Story 2.1]
- Vault architecture (FR30–34): [Source: `_bmad-output/planning-artifacts/architecture.md` — Data Architecture]
- Filesystem adapter (existing): [Source: `src/shared/platform/filesystem/`]
- File picker adapter (existing): [Source: `src/shared/platform/file-picker/`]
- VaultSetupScreen UX: [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` — Component Strategy: VaultSetupScreen]
- First launch journey: [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` — Journey 1]
- FSD + naming conventions: [Source: `AGENTS.md`]
- File System Access API browser support: `showDirectoryPicker` not available on Firefox (Mozilla officially opposes the API) — OPFS is the only option on Firefox/Safari

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
