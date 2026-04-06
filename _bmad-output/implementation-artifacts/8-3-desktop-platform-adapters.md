# Story 8.3: Desktop Platform Adapters

Status: review

## Story

As a developer,
I want `*.desktop.ts` implementations for all platform adapters,
So that filesystem, secure storage, and file picker features work natively on Linux and Windows.

## Acceptance Criteria

1. **Given** `filesystem.desktop.ts` is implemented
   **When** feature code calls `filesystemAdapter.readFile(path)` on desktop
   **Then** the Tauri FS plugin reads the file at the given absolute OS path without error

2. **Given** `filePicker.desktop.ts` is implemented
   **When** feature code calls `filePickerAdapter.pickDirectory()` on desktop
   **Then** the Tauri dialog plugin opens a native OS folder picker and returns the selected path

3. **Given** `secureStorage.desktop.ts` is implemented using Tauri Stronghold
   **When** an API key is stored on desktop
   **Then** it is persisted in Tauri Stronghold — never written to the vault or any plain file — and survives app restarts

4. **Given** all four adapters have `*.desktop.ts` implementations
   **When** `isTauri()` is true
   **Then** the `index.ts` runtime selector routes all adapter calls to the desktop implementation — `*.android.ts` is never loaded in a Tauri build

5. **Given** unit tests run for a feature using a platform adapter on desktop
   **When** the adapter is mocked via dependency injection
   **Then** tests pass without importing any Tauri-specific modules

## Tasks / Subtasks

### Commit 1: `chore(deps): add Tauri plugin crates and npm packages, remove sqlite-wasm`

- [x] Task 1: Add Rust crates to `src-tauri/Cargo.toml` under `[dependencies]`
  ```toml
  tauri-plugin-fs = "2"
  tauri-plugin-dialog = "2"
  tauri-plugin-stronghold = "2"
  tauri-plugin-store = "2"
  argon2 = "0.5"
  ```

- [x] Task 2: Register all plugins in `src-tauri/src/lib.rs`
  ```rust
  pub fn run() {
    tauri::Builder::default()
      .plugin(tauri_plugin_sql::Builder::default().build())
      .plugin(tauri_plugin_fs::init())
      .plugin(tauri_plugin_dialog::init())
      .plugin(tauri_plugin_store::Builder::default().build())
      .plugin(
        tauri_plugin_stronghold::Builder::new(|password| {
          use argon2::{Argon2, PasswordHasher};
          use argon2::password_hash::SaltString;
          let salt = SaltString::encode_b64(b"lekto-stronghold-salt-v1").unwrap();
          let argon2 = Argon2::default();
          let hash = argon2
            .hash_password(password.as_ref(), &salt)
            .expect("stronghold hash error")
            .to_string();
          hash.as_bytes().to_vec()
        })
        .build(),
      )
      .run(tauri::generate_context!())
      .expect("error while running tauri application")
  }
  ```
  - Note: The hash function receives the `password` string passed from `Stronghold.load()` in JS and derives the actual vault encryption key. Using a static salt + argon2 is intentional — no user accounts, local-only app.

- [x] Task 3: Install npm packages
  ```bash
  npm install @tauri-apps/plugin-fs @tauri-apps/plugin-dialog @tauri-apps/plugin-stronghold @tauri-apps/plugin-store
  ```

- [x] Task 4: Remove `@sqlite.org/sqlite-wasm` (cleanup from Story 8.2)
  ```bash
  npm uninstall @sqlite.org/sqlite-wasm
  ```
  - Verify no remaining references: `grep -r "sqlite-wasm\|sqlite\.org" src/` should return nothing
  - Also remove the `optimizeDeps.exclude: ['@sqlite.org/sqlite-wasm']` entry from `vite.config.ts`

- [x] Task 5: Update `src-tauri/capabilities/default.json` to add plugin permissions
  ```json
  {
    "$schema": "../gen/schemas/desktop-schema.json",
    "identifier": "default",
    "description": "enables the default permissions",
    "windows": ["main"],
    "permissions": [
      "core:default",
      "sql:default",
      "fs:default",
      "dialog:default",
      "stronghold:default",
      "store:default"
    ]
  }
  ```
  - Note: `fs:default` allows read/write to paths within the app scope. Because vault paths are user-chosen and can be anywhere, add broad scope permissions using `fs:allow-read-text-file`, `fs:allow-write-text-file`, `fs:allow-remove-file`, `fs:allow-mkdir`, `fs:allow-read-dir`, `fs:allow-exists` with `allow: [{ "path": "**" }]` if needed. Check what `fs:default` covers and expand the permissions object if the FS adapter fails with permission errors at runtime.

- [x] Task 6: Run `cargo check` inside `src-tauri/` to verify Rust compilation

- [x] Task 7: Quality gate — commit 1
  - `npm run lint` — zero warnings
  - `npm run typecheck` — zero errors
  - `npm run test` — all tests pass

---

### Commit 2: `feat(platform): implement preferences.desktop.ts (Tauri Store)`

- [x] Task 8: Create `src/shared/platform/preferences/preferences.desktop.ts`
  ```typescript
  import { Store } from '@tauri-apps/plugin-store'
  import { ok, err } from 'neverthrow'
  import type { PreferencesAdapter } from './preferences.interface'
  import type { AsyncResult } from '../../lib/types'

  // Store file lives in Tauri app data dir (OS-managed, not in user vault)
  const STORE_FILE = 'lekto-preferences.json'

  export function createDesktopPreferencesAdapter(): PreferencesAdapter {
    return {
      async get(key: string): AsyncResult<string> {
        try {
          const store = await Store.load(STORE_FILE)
          const value = await store.get<string>(key)
          if (value === undefined || value === null) return err('Not found')
          return ok(value)
        } catch {
          return err('Preferences get failed')
        }
      },

      async set(key: string, value: string): AsyncResult<void> {
        try {
          const store = await Store.load(STORE_FILE)
          await store.set(key, value)
          await store.save()
          return ok(undefined)
        } catch {
          return err('Preferences set failed')
        }
      },

      async remove(key: string): AsyncResult<void> {
        try {
          const store = await Store.load(STORE_FILE)
          await store.delete(key)
          await store.save()
          return ok(undefined)
        } catch {
          return err('Preferences remove failed')
        }
      },
    }
  }
  ```

- [x] Task 9: Update `src/shared/platform/preferences/index.ts`
  ```typescript
  import { isTauri } from '../is-tauri'
  import { createWebPreferencesAdapter } from './preferences.web'
  import { createDesktopPreferencesAdapter } from './preferences.desktop'

  export type { PreferencesAdapter } from './preferences.interface'

  export const preferencesAdapter = isTauri()
    ? createDesktopPreferencesAdapter()
    : createWebPreferencesAdapter()
  ```
  - **IMPORTANT:** `preferencesAdapter` is used in `src/shared/db/index.ts` to retrieve the vault path at app startup. The Tauri Store plugin uses async `Store.load()` internally. However, the adapter is initialized synchronously (the factory functions are called eagerly). The actual Store operations are async (called when `get/set/remove` are invoked), so this is fine — no top-level await needed.

- [x] Task 10: Quality gate — commit 2
  - `npm run lint` — zero warnings
  - `npm run typecheck` — zero errors
  - `npm run test` — all tests pass (existing preferences tests mock the adapter)

---

### Commit 3: `feat(platform): implement filesystem.desktop.ts (Tauri FS plugin)`

- [x] Task 11: Create `src/shared/platform/filesystem/filesystem.desktop.ts`
  ```typescript
  import {
    readTextFile,
    writeTextFile,
    remove,
    mkdir,
    readDir,
    exists,
  } from '@tauri-apps/plugin-fs'
  import { ok, err } from 'neverthrow'
  import type { FilesystemAdapter } from './filesystem.interface'
  import type { AsyncResult } from '../../lib/types'

  export function createDesktopFilesystemAdapter(): FilesystemAdapter {
    return {
      async readFile(path: string): AsyncResult<string> {
        try {
          const content = await readTextFile(path)
          return ok(content)
        } catch {
          return err(`Failed to read file: ${path}`)
        }
      },

      async writeFile(path: string, data: string): AsyncResult<void> {
        try {
          await writeTextFile(path, data)
          return ok(undefined)
        } catch {
          return err(`Failed to write file: ${path}`)
        }
      },

      async deleteFile(path: string): AsyncResult<void> {
        try {
          await remove(path)
          return ok(undefined)
        } catch {
          return err(`Failed to delete file: ${path}`)
        }
      },

      async mkdir(path: string): AsyncResult<void> {
        try {
          await mkdir(path, { recursive: true })
          return ok(undefined)
        } catch {
          return err(`Failed to create directory: ${path}`)
        }
      },

      async readdir(path: string): AsyncResult<string[]> {
        try {
          const entries = await readDir(path)
          const names = entries.map((e) => e.name ?? '')
          return ok(names)
        } catch {
          return err(`Failed to read directory: ${path}`)
        }
      },

      async exists(path: string): AsyncResult<boolean> {
        try {
          const result = await exists(path)
          return ok(result)
        } catch {
          return ok(false)
        }
      },
    }
  }
  ```

- [x] Task 12: Update `src/shared/platform/filesystem/index.ts`
  ```typescript
  import { isTauri } from '../is-tauri'
  import { createWebFilesystemAdapter, setWebFilesystemRoot } from './filesystem.web'
  import { createAndroidFilesystemAdapter } from './filesystem.android'
  import { createDesktopFilesystemAdapter } from './filesystem.desktop'
  import type { FilesystemAdapter } from './filesystem.interface'

  export type { FilesystemAdapter }

  export const filesystemAdapter: FilesystemAdapter = isTauri()
    ? createDesktopFilesystemAdapter()
    : createAndroidFilesystemAdapter()

  // No-op on desktop and Android; only used for web OPFS flows.
  export function setFilesystemRoot(handle: FileSystemDirectoryHandle | null): void {
    if (!isTauri()) {
      setWebFilesystemRoot(handle)
    }
  }
  ```
  - **Note:** `setFilesystemRoot` stays in the public API because `sync-vault.ts` calls it for web OPFS flows (`WEB_OPFS_PATH` / `WEB_NATIVE_PATH` paths). On desktop these code paths are never reached (desktop vault path is always an OS path). Keeping it as a no-op on desktop avoids breaking the sync-vault feature's existing logic.

- [x] Task 13: Update `src/shared/platform/filesystem/filesystem.index.test.ts`
  - Add `vi.mock('@/shared/platform/is-tauri', () => ({ isTauri: vi.fn().mockReturnValue(false) }))` (or equivalent)
  - Add a mock for `./filesystem.desktop`
  - Add test case: when `isTauri()` returns `true`, `filesystemAdapter` is the desktop adapter
  - Keep existing test cases (just update mocks to use `isTauri` instead of `Capacitor.isNativePlatform`)

- [x] Task 14: Quality gate — commit 3
  - `npm run lint` — zero warnings
  - `npm run typecheck` — zero errors
  - `npm run test` — all tests pass

---

### Commit 4: `feat(platform): implement file-picker.desktop.ts (Tauri Dialog plugin)`

- [x] Task 15: Create `src/shared/platform/file-picker/file-picker.desktop.ts`
  ```typescript
  import { open } from '@tauri-apps/plugin-dialog'
  import { readFile } from '@tauri-apps/plugin-fs'
  import { ok, err } from 'neverthrow'
  import type { FilePickerAdapter, PickedFile } from './file-picker.interface'
  import type { AsyncResult } from '../../lib/types'

  export function createDesktopFilePickerAdapter(): FilePickerAdapter {
    return {
      async pickFile(options: { accept?: `.${string}`[] }): AsyncResult<PickedFile> {
        try {
          const extensions = (options.accept ?? []).map((ext) => ext.slice(1)) // remove leading dot
          const selected = await open({
            multiple: false,
            filters:
              extensions.length > 0
                ? [{ name: 'Book files', extensions }]
                : undefined,
          })
          if (selected === null) return err('File pick cancelled or failed')
          const path = typeof selected === 'string' ? selected : selected[0]
          if (!path) return err('File pick cancelled or failed')
          const bytes = await readFile(path)
          const name = path.split('/').at(-1) ?? path.split('\\').at(-1) ?? path
          return ok({ name, data: bytes.buffer })
        } catch {
          return err('File pick cancelled or failed')
        }
      },

      async pickDirectory(): AsyncResult<string> {
        try {
          const selected = await open({ directory: true })
          if (selected === null) return err('Directory pick cancelled or failed')
          return ok(typeof selected === 'string' ? selected : selected[0] ?? '')
        } catch {
          return err('Directory pick cancelled or failed')
        }
      },
    }
  }
  ```
  - **Note:** `open()` from `@tauri-apps/plugin-dialog` returns `string | string[] | null`. With `multiple: false`, it returns `string | null`. The `typeof selected === 'string'` guard handles the union type without special-casing.
  - **Note:** `readFile()` (binary, not `readTextFile()`) returns `Uint8Array`. The `.buffer` property gives the `ArrayBuffer` that the `PickedFile` interface expects.
  - **Note:** `readFile` is imported from `@tauri-apps/plugin-fs` — same package already used in filesystem adapter (no extra npm install).

- [x] Task 16: Update `src/shared/platform/file-picker/index.ts`
  ```typescript
  import { isTauri } from '../is-tauri'
  import { createWebFilePickerAdapter } from './file-picker.web'
  import { createAndroidFilePickerAdapter } from './file-picker.android'
  import { createDesktopFilePickerAdapter } from './file-picker.desktop'
  import type { FilePickerAdapter, PickedFile } from './file-picker.interface'

  export type { FilePickerAdapter, PickedFile }

  export const filePickerAdapter: FilePickerAdapter = isTauri()
    ? createDesktopFilePickerAdapter()
    : createAndroidFilePickerAdapter()
  ```

- [x] Task 17: Quality gate — commit 4
  - `npm run lint` — zero warnings
  - `npm run typecheck` — zero errors
  - `npm run test` — all tests pass

---

### Commit 5: `feat(platform): implement secure-storage.desktop.ts (Tauri Stronghold)`

- [x] Task 18: Create `src/shared/platform/secure-storage/secure-storage.desktop.ts`
  ```typescript
  import { Stronghold, Client } from '@tauri-apps/plugin-stronghold'
  import { appDataDir } from '@tauri-apps/api/path'
  import { ok, err } from 'neverthrow'
  import type { SecureStorageAdapter } from './secure-storage.interface'
  import type { AsyncResult } from '../../lib/types'

  // Static password passed to the Rust hash function (argon2 derives the actual key).
  // For a local-only app with no user accounts, a static passphrase is acceptable.
  // Never changes after first vault initialization — changing it would lock out existing secrets.
  const STRONGHOLD_PASSWORD = 'lekto-desktop-secure-storage-v1'
  const STRONGHOLD_CLIENT = 'lekto-client'

  let _stronghold: Stronghold | null = null
  let _client: Client | null = null

  async function getClient(): Promise<Client> {
    if (_client) return _client
    const dir = await appDataDir()
    const vaultPath = `${dir}/lekto-secrets.holsd`
    _stronghold = await Stronghold.load(vaultPath, STRONGHOLD_PASSWORD)
    try {
      _client = await _stronghold.loadClient(STRONGHOLD_CLIENT)
    } catch {
      _client = await _stronghold.createClient(STRONGHOLD_CLIENT)
    }
    return _client
  }

  const encoder = new TextEncoder()
  const decoder = new TextDecoder()

  export function createDesktopSecureStorageAdapter(): SecureStorageAdapter {
    return {
      async get(key: string): AsyncResult<string> {
        try {
          const client = await getClient()
          const store = client.getStore()
          const data = await store.get(key)
          if (data === null || data === undefined) return err('Secure storage: key not found')
          return ok(decoder.decode(new Uint8Array(data)))
        } catch {
          return err('Secure storage get failed')
        }
      },

      async set(key: string, value: string): AsyncResult<void> {
        try {
          const client = await getClient()
          const store = client.getStore()
          await store.insert(key, Array.from(encoder.encode(value)))
          await _stronghold!.save()
          return ok(undefined)
        } catch {
          return err('Secure storage set failed')
        }
      },

      async remove(key: string): AsyncResult<void> {
        try {
          const client = await getClient()
          const store = client.getStore()
          await store.remove(key)
          await _stronghold!.save()
          return ok(undefined)
        } catch {
          return err('Secure storage remove failed')
        }
      },
    }
  }
  ```
  - **Critical:** `_stronghold` and `_client` are module-level singletons — Stronghold is initialized once per app session. This is intentional: loading Stronghold is expensive (argon2 key derivation).
  - **Critical:** `_stronghold!.save()` must be called after every write to persist changes to disk. Without it, changes are in-memory only.
  - **Critical:** The `STRONGHOLD_PASSWORD` must never change after the Stronghold vault is first created. Changing it would produce a different argon2-derived key and make all previously stored secrets inaccessible. The password is NOT a user-visible password — it's a static app-level secret.
  - **Note:** `appDataDir()` returns the OS-appropriate app data directory (`~/.local/share/lekto` on Linux, `%APPDATA%\lekto` on Windows). The Stronghold file (`lekto-secrets.holsd`) is stored there, separate from the user vault.

- [x] Task 19: Update `src/shared/platform/secure-storage/index.ts`
  ```typescript
  import { isTauri } from '../is-tauri'
  import { createWebSecureStorageAdapter } from './secure-storage.web'
  import { createAndroidSecureStorageAdapter } from './secure-storage.android'
  import { createDesktopSecureStorageAdapter } from './secure-storage.desktop'
  import type { SecureStorageAdapter } from './secure-storage.interface'

  export type { SecureStorageAdapter }

  export const secureStorageAdapter: SecureStorageAdapter = isTauri()
    ? createDesktopSecureStorageAdapter()
    : createAndroidSecureStorageAdapter()
  ```

- [x] Task 20: Quality gate — commit 5
  - `npm run lint` — zero warnings
  - `npm run typecheck` — zero errors
  - `npm run test` — all tests pass (secure storage tests mock the adapter)

---

### Commit 6: `chore(platform): update remaining index.ts selectors + verify integration`

- [x] Task 21: Verify `tts/index.ts` — no change needed
  - The web TTS adapter uses `window.speechSynthesis`, which is available in Tauri's WebView
  - Keep existing `Capacitor.isNativePlatform() ? android : web` selector as-is

- [x] Task 22: Verify `in-app-browser/index.ts` — no change needed
  - The web adapter opens a new browser tab/window, which works in Tauri's context
  - Keep existing selector as-is

- [x] Task 23: Verify `sync-vault.ts` is unaffected
  - `setFilesystemRoot` is now a no-op on desktop — the `WEB_OPFS_PATH`/`WEB_NATIVE_PATH` branches in `sync-vault.ts` are dead code on desktop but harmless
  - On desktop, `initVault(path)` is called with an OS path (e.g., `/home/user/my-vault`) — the `WEB_OPFS_PATH` check is false, so `setFilesystemRoot(null)` is not called
  - No changes to `sync-vault.ts` are needed in this story

- [x] Task 24: Run full integration test on desktop
  - `npm run tauri:dev` — desktop window opens without errors
  - First-launch flow: VaultSetupScreen shows → pick directory → `preferencesAdapter.set()` stores path via Tauri Store → `filesystemAdapter.mkdir('books')` creates the books dir → DB initializes
  - Confirm `~/.local/share/lekto/lekto-preferences.json` is created (Linux) with vault path
  - Confirm `~/.local/share/lekto/lekto-secrets.holsd` does NOT exist yet (Stronghold is lazy — only created when an API key is first stored)

- [x] Task 25: Quality gate — commit 6
  - `npm run lint` — zero warnings
  - `npm run typecheck` — zero errors
  - `npm run test` — all tests pass
  - `npm run build` — exits zero

## Dev Notes

### Adapter Selector Pattern (After This Story)

All four adapter `index.ts` files follow this pattern:

```typescript
import { isTauri } from '../is-tauri'

export const xyzAdapter = isTauri()
  ? createDesktopXyzAdapter()
  : createAndroidXyzAdapter()
```

`isTauri()` is `true` in Tauri builds (`TAURI_ENV_PLATFORM` is set), `false` in Android/test builds. In Vitest (`npm run test`), `TAURI_ENV_PLATFORM` is not set → `__TAURI__ = false` → `isTauri() = false` → Android adapter is instantiated. Tests that need to test the desktop adapter branch must mock `isTauri`.

**No web fallback:** `.web.ts` adapter files are kept on disk (their tests are useful documentation) but they are no longer referenced by any `index.ts` selector. The architecture phaseout is complete for these four adapters.

### `@tauri-apps/plugin-fs` API Quick Reference (v2)

```typescript
import {
  readTextFile, writeTextFile,       // text I/O
  readFile,                          // binary I/O → Uint8Array
  remove,                            // delete file
  mkdir,                             // create directory (options.recursive)
  readDir,                           // list directory → DirEntry[]
  exists,                            // check existence → boolean
} from '@tauri-apps/plugin-fs'

// DirEntry shape: { name: string | null, path: string, isDirectory: boolean, isFile: boolean, isSymlink: boolean }
// Use entry.name for the filename (can be null for root entries — filter in readdir)
```

### `@tauri-apps/plugin-dialog` API Quick Reference (v2)

```typescript
import { open } from '@tauri-apps/plugin-dialog'

// Pick single file → string | null (null = cancelled)
const path = await open({
  multiple: false,
  filters: [{ name: 'ePub', extensions: ['epub'] }],
})

// Pick directory → string | null
const dir = await open({ directory: true })
```

### `@tauri-apps/plugin-store` API Quick Reference (v2)

```typescript
import { Store } from '@tauri-apps/plugin-store'

const store = await Store.load('filename.json')  // auto-creates in app data dir
await store.set('key', value)    // value: any JSON-serializable type
await store.save()               // flush to disk (required after set/delete)
const v = await store.get<T>('key')  // returns T | undefined
await store.delete('key')
await store.save()
```

- **Auto-save behavior:** The store does NOT auto-save on `set()`. Always call `store.save()` after mutations.
- **File location:** `{app_data_dir}/filename.json` — NOT inside the user vault. If the user moves their vault, preferences persist.

### `@tauri-apps/plugin-stronghold` API Quick Reference (v2)

```typescript
import { Stronghold, Client } from '@tauri-apps/plugin-stronghold'

// Load or create vault
const stronghold = await Stronghold.load(vaultPath, password)

// Load existing client (throws if not found)
const client = await stronghold.loadClient(clientName)
// Create new client (first run)
const client = await stronghold.createClient(clientName)

// Get key-value store
const store = client.getStore()

// Insert (key: string, value: number[])
await store.insert('my-key', Array.from(encoder.encode('secret-value')))
await stronghold.save()  // REQUIRED

// Get (returns number[] | null)
const data = await store.get('my-key')  // null if not found
const value = decoder.decode(new Uint8Array(data))

// Remove
await store.remove('my-key')
await stronghold.save()  // REQUIRED
```

### Stronghold Password and Security Model

The `STRONGHOLD_PASSWORD` constant in `secure-storage.desktop.ts` is the plaintext password passed to `Stronghold.load()`. The Rust `tauri_plugin_stronghold::Builder::new(|password| { ... })` callback transforms this into the actual vault encryption key using argon2.

**This is safe for this app because:**
1. No user accounts — there's no per-user secret to derive from
2. The app is local-only — the threat model is casual physical access to the disk, not network attacks
3. The actual vault encryption key is never stored — it's re-derived from the static password on every app start

**Do NOT change `STRONGHOLD_PASSWORD` after shipping.** Doing so will permanently lock all users out of their stored API keys. Version the constant name if behavior must change (e.g., `lekto-desktop-secure-storage-v2` for a breaking migration).

### Circular Import: Stronghold + `appDataDir`

`appDataDir` is imported from `@tauri-apps/api/path`. This import is safe in the secure storage desktop adapter because:
- The adapter is only instantiated when `isTauri()` is true
- `@tauri-apps/api` is already installed and used elsewhere (`is-tauri.ts` uses the Tauri `__TAURI__` constant)
- No circular dependencies — this is a leaf module

### Vitest / Testing Strategy

Desktop adapter files import Tauri plugins (`@tauri-apps/plugin-fs`, etc.). These plugins make IPC calls to the Rust backend that do not exist in the JSDOM test environment. Testing strategies:

1. **Index selector tests** (e.g., `filesystem.index.test.ts`): Mock `isTauri`, `filesystem.android`, and `filesystem.desktop` — test that the correct factory is called based on `isTauri()` return value.

2. **Feature tests** (`features/*/model/*.test.ts`): Mock `@/shared/platform` entirely (already done in most feature tests — see `sync-vault.test.ts`). Feature tests never import Tauri modules directly.

3. **Desktop adapter unit tests**: NOT recommended — Tauri IPC calls can't be meaningfully mocked in JSDOM. Runtime verification via `npm run tauri:dev` is the integration test for these adapters (same approach as Story 8.2 for the Tauri SQL adapter).

### Files to Create / Modify

| File | Action | Notes |
|------|--------|-------|
| `src-tauri/Cargo.toml` | MODIFY | Add fs, dialog, stronghold, store crates + argon2 |
| `src-tauri/src/lib.rs` | MODIFY | Register all 4 new plugins + stronghold password hasher |
| `src-tauri/capabilities/default.json` | MODIFY | Add fs, dialog, stronghold, store permissions |
| `vite.config.ts` | MODIFY | Remove `optimizeDeps.exclude: ['@sqlite.org/sqlite-wasm']` |
| `src/shared/platform/preferences/preferences.desktop.ts` | CREATE | Tauri Store adapter |
| `src/shared/platform/preferences/index.ts` | MODIFY | Use `isTauri()` selector |
| `src/shared/platform/filesystem/filesystem.desktop.ts` | CREATE | Tauri FS adapter |
| `src/shared/platform/filesystem/index.ts` | MODIFY | Use `isTauri()` selector |
| `src/shared/platform/file-picker/file-picker.desktop.ts` | CREATE | Tauri Dialog adapter |
| `src/shared/platform/file-picker/index.ts` | MODIFY | Use `isTauri()` selector |
| `src/shared/platform/secure-storage/secure-storage.desktop.ts` | CREATE | Tauri Stronghold adapter |
| `src/shared/platform/secure-storage/index.ts` | MODIFY | Use `isTauri()` selector |
| `src/shared/platform/filesystem/filesystem.index.test.ts` | MODIFY | Update mocks for new selector pattern |
| `vitest.config.ts` | MODIFY | Add `__TAURI__: false` define for test environment |

### References

- Previous story learnings: `_bmad-output/implementation-artifacts/8-2-desktop-db-layer-tauri-sql-plugin.md#Dev Notes`
- Architecture adapter structure: `_bmad-output/planning-artifacts/architecture.md#Platform adapter structure`
- `isTauri()` implementation: `src/shared/platform/is-tauri.ts`
- Existing Android adapter reference: `src/shared/platform/filesystem/filesystem.android.ts`
- Tauri FS plugin v2: https://tauri.app/plugin/file-system/
- Tauri Dialog plugin v2: https://tauri.app/plugin/dialog/
- Tauri Store plugin v2: https://tauri.app/plugin/store/
- Tauri Stronghold plugin v2: https://tauri.app/plugin/stronghold/
- Tauri Stronghold security model: static password + argon2 key derivation in Rust builder

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
