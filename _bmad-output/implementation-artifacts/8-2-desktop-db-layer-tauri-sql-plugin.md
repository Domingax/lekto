# Story 8.2: Desktop DB Layer (Tauri SQL Plugin)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want the SQLite database accessed via the Tauri SQL plugin on desktop,
so that `lekto.db` lives directly in the vault folder at any OS path — no sync needed.

## Acceptance Criteria

1. **Given** the app starts on Desktop with a configured vault path
   **When** `main.tsx` runs
   **Then** the Tauri SQL plugin opens `lekto.db` at `{vaultPath}/lekto.db`, Drizzle's `migrate()` executes, and the database is ready for queries — no OPFS or internal storage involved

2. **Given** the app starts on Desktop with no vault path yet (first launch)
   **When** `main.tsx` runs
   **Then** `initDb()` returns `ok(null)` (no connection opened), migrations are skipped, and the VaultSetupScreen is displayed — no crash, no error banner

3. **Given** the database layer abstraction in `src/shared/db/`
   **When** `isTauri()` is true
   **Then** the Tauri SQL adapter is selected; when `Capacitor.isNativePlatform()` is true, the Android adapter is selected; otherwise an error is thrown — no platform branching in feature code outside `db/index.ts`

4. **Given** a Drizzle migration is pending
   **When** the desktop app starts with a vault already configured
   **Then** `runMigrations()` runs exactly once against `{vaultPath}/lekto.db` and is idempotent on subsequent restarts

5. **Given** a vault path is set to a cloud-synced directory (e.g. `~/Dropbox/lekto-vault/`)
   **When** the app starts on Desktop
   **Then** `lekto.db` is opened directly at that path — the cloud sync tool handles replication transparently

6. **Given** a new vault is created on Desktop via the VaultSetupScreen
   **When** the user confirms the chosen folder
   **Then** `initVault()` triggers DB init at the new vault path, `lekto.db` is created, and migrations run — the user proceeds to the library without reload

## Tasks / Subtasks

### Commit 1: `feat(db): add Tauri SQL Rust plugin and capabilities`

- [x] Task 1: Add `tauri-plugin-sql` to Rust backend (AC: #1, #4)
  - [x] In `src-tauri/Cargo.toml`, add under `[dependencies]`:
    ```toml
    tauri-plugin-sql = { version = "2", features = ["sqlite"] }
    ```
  - [x] In `src-tauri/src/lib.rs`, register the plugin:
    ```rust
    #[cfg_attr(mobile, tauri::mobile_entry_point)]
    pub fn run() {
      tauri::Builder::default()
        .plugin(tauri_plugin_sql::Builder::default().build())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
    }
    ```
  - [x] Run `cargo check` inside `src-tauri/` to verify compilation

- [x] Task 2: Add SQL plugin permissions to capabilities (AC: #1)
  - [x] In `src-tauri/capabilities/default.json`, add SQL permissions:
    ```json
    {
      "$schema": "../gen/schemas/desktop-schema.json",
      "identifier": "default",
      "description": "enables the default permissions",
      "windows": ["main"],
      "permissions": [
        "core:default",
        "sql:default"
      ]
    }
    ```
  - [x] Note: `sql:default` includes `allow-execute`, `allow-select`, `allow-load`, and `allow-close`

- [x] Task 3: Install frontend npm package (AC: #1)
  - [x] Run `npm install @tauri-apps/plugin-sql`
  - [x] Verify it appears in `package.json` under `dependencies`

- [x] Task 4: Quality gate — commit 1
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all existing tests pass

### Commit 2: `feat(db): add desktop db adapter, drop web adapter, update initDb selector`

- [ ] Task 5: Update `src/shared/db/index.ts` (AC: #1, #2, #3)
  - [ ] Change the return type of `initDb` to `Result<DrizzleDb | null, string>` to represent "no vault configured yet" on desktop
  - [ ] Add `import Database from '@tauri-apps/plugin-sql'` at the top of the file — `createDesktopDb` vit dans `index.ts` aux côtés de `createAndroidDb`, pas dans un fichier séparé
  - [ ] Add `createDesktopDb` function:
    ```typescript
    async function createDesktopDb(vaultPath: string): Promise<DrizzleDb> {
      const db = await Database.load(`sqlite:${vaultPath}/lekto.db`)

      return drizzle(
        async (sql, params, method) => {
          if (method === 'run') {
            await db.execute(sql, params as unknown[])
            return { rows: [] }
          }
          const rows = await db.select<Record<string, unknown>>(sql, params as unknown[])
          if (method === 'values') {
            return { rows: rows.map((row) => Object.values(row)) }
          }
          return { rows }
        },
        { schema },
      )
    }
    ```
  - [ ] The `Database.load()` call creates `lekto.db` at the path if it does not exist (SQLite `OPEN_CREATE` flag is the default)
  - [ ] Import `isTauri` from `@/shared/platform` — **CAUTION**: check for circular imports (see Dev Notes below)
  - [ ] Delete `createWebDb()` and its import (`@sqlite.org/sqlite-wasm`) — no web distribution target exists
  - [ ] Update `initDb` function body:
    ```typescript
    export async function initDb(): Promise<Result<DrizzleDb | null, string>> {
      if (_db) return ok(_db)
      try {
        if (isTauri()) {
          const vaultResult = await preferencesAdapter.get(VAULT_PATH_KEY)
          if (vaultResult.isErr()) return ok(null)  // No vault configured yet — first launch
          _db = await createDesktopDb(vaultResult.value)
        } else if (Capacitor.isNativePlatform()) {
          _db = await createAndroidDb()
        } else {
          throw new Error('Unsupported platform')
        }
        return ok(_db)
      } catch (e) {
        return err(e instanceof Error ? e.message : String(e))
      }
    }
    ```
  - [ ] **Circular import workaround**: importer `preferencesAdapter` directement depuis `@/shared/platform` (pas depuis `features/sync-vault`) pour éviter le cycle. Définir localement `const VAULT_PATH_KEY = 'vault_path'` avec un commentaire `// Must match VAULT_PATH_KEY in features/sync-vault`.
  - [ ] Export type: `getDb()` reste non-null ; `initDb()` retourne `DrizzleDb | null` — pas de changement pour les appelants qui passent par `getDb()`.

- [ ] Task 7: Update `src/main.tsx` to handle null DB on desktop (AC: #1, #2)
  - [ ] Change the DB init check to skip migrations when db is null:
    ```typescript
    const db = await initDb()
    if (db.isErr()) {
      showError(`Database initialization failed: ${db.error}`)
      return
    }

    if (db.value !== null) {
      const migrations = await runMigrations()
      if (migrations.isErr()) {
        showError(`Database migration failed: ${migrations.error}`)
        return
      }

      const seed = await seedLanguages()
      if (seed.isErr()) {
        showError(`Database seed failed: ${seed.error}`)
        return
      }
    }
    ```
  - [ ] The rest of main.tsx (vault path restore, createRoot render) is unchanged

- [ ] Task 8: Update vault creation flow to init DB on desktop (AC: #6)
  - [ ] In `src/features/sync-vault/model/sync-vault.ts`, update `initVault()`:
    - After `preferencesAdapter.set(VAULT_PATH_KEY, path)` succeeds, and when `isTauri()` is true, call `initDb()` then `runMigrations()` then `seedLanguages()`
    - Return `err(...)` if any of these fail
  - [ ] Import `isTauri` from `@/shared/platform`
  - [ ] Import `initDb`, `runMigrations`, `seedLanguages` from `@/shared/db`
  - [ ] This ensures that on first vault creation on desktop, the DB is fully ready before navigation

- [ ] Task 9: Quality gate — commit 2
  - [ ] `npm run lint` — zero warnings
  - [ ] `npm run typecheck` — zero errors
  - [ ] `npm run test` — all existing tests pass
  - [ ] `npm run build` — exits zero, produces `dist/`
  - [ ] `npm run tauri:dev` — desktop window opens, console shows no DB errors on a machine with a pre-existing vault

## Dev Notes

### Critical Architecture: Desktop DB is Vault-Path-Dependent

On Android/web, `initDb()` always succeeds at startup regardless of vault state. On Desktop, the DB file lives AT the vault path — it cannot be opened without knowing where the vault is.

**First-launch flow (desktop):**
1. `main.tsx` calls `initDb()` → preferences have no vault path → returns `ok(null)` → no DB, no migrations
2. App renders → VaultSetupScreen is shown (existing logic via `useVaultStore.vaultPath === null`)
3. User picks a folder → `initVault(path)` is called → **NOW** DB is opened at `{path}/lekto.db`, migrations run, seed runs
4. `useVaultStore.setVaultPath(path)` → VaultSetupScreen dismisses → Library shown

**Subsequent-launch flow (desktop):**
1. `main.tsx` calls `initDb()` → preferences have vault path → `createDesktopDb(vaultPath)` → DB opened
2. `runMigrations()` runs → idempotent (already applied)
3. `seedLanguages()` runs → idempotent
4. App renders directly to Library (vault is already configured)

### Circular Import Risk

`src/shared/db/index.ts` must NOT import from `src/features/sync-vault` (which imports `shared/platform`, which would then import `shared/db` = cycle).

**Safe approach**: import `preferencesAdapter` directly from `src/shared/platform` and use the same key string `'vault_path'` (or import `VAULT_PATH_KEY` from the constant definition file if it's ever moved to `shared/`). Currently `VAULT_PATH_KEY = 'vault_path'` is defined in `features/sync-vault/model/sync-vault.ts` — hardcode the string `'vault_path'` in `db/index.ts` with a comment referencing the canonical constant, OR move `VAULT_PATH_KEY` to `shared/` as a shared constant.

Recommended: define `VAULT_PATH_KEY = 'vault_path'` as a local constant in `db/index.ts` with a comment `// Must match VAULT_PATH_KEY in features/sync-vault`.

### `@tauri-apps/plugin-sql` API Reference (v2)

```typescript
import Database from '@tauri-apps/plugin-sql'

// Open (creates if not exists)
const db = await Database.load('sqlite:/absolute/path/lekto.db')

// Mutations (INSERT, UPDATE, DELETE, CREATE TABLE…)
// Returns { lastInsertId: number, rowsAffected: number }
await db.execute('INSERT INTO books (title) VALUES (?)', ['Moby Dick'])

// Queries (SELECT)
// Returns T[] where T is Record<string, unknown>
const rows = await db.select<{ id: number; title: string }[]>(
  'SELECT id, title FROM books WHERE id = ?', [1]
)
```

Note: `db.select<T>()` returns an array of objects (not arrays of values). The Drizzle proxy must handle `method === 'values'` by mapping `Object.values(row)` — already shown in the task above, matching the same pattern used in `createAndroidDb()`.

### Tauri SQL Plugin Path Syntax

```
sqlite:/absolute/path/lekto.db       ← Linux: /home/user/vault/lekto.db
sqlite:C:/Users/user/vault/lekto.db  ← Windows
sqlite:relative.db                   ← relative to app data dir (do NOT use — vault paths are always absolute on desktop)
```

Always use absolute paths. Vault paths on desktop are OS paths chosen by the user via the file picker (Story 8.3).

### DB Adapter Selection (Target State After This Story)

```
initDb()
├── isTauri() === true
│   ├── vault path found → createDesktopDb(vaultPath) → Tauri SQL proxy
│   └── vault path not found → ok(null)  (first launch)
├── Capacitor.isNativePlatform() === true → createAndroidDb() → @capacitor-community/sqlite
└── else → throw Error('Unsupported platform')  ← createWebDb() supprimé
```

### `getDb()` Unchanged

`getDb()` remains a non-null getter (throws if `_db` is null). Callers that use `getDb()` (like `migrate.ts`) are only called after `initDb()` returns a non-null result, so no change to `migrate.ts` is needed.

### Suppression de `createWebDb()` — Périmètre de cette story

`createWebDb()` (OPFS via `@sqlite.org/sqlite-wasm`) est supprimé dans cette story. Il n'y a pas de cible de distribution web — les devs utilisent `npm run tauri:dev` sur desktop, `npx cap run android` sur Android. Conséquences :
- Supprimer `createWebDb()` et son import `@sqlite.org/sqlite-wasm` de `index.ts`
- Ne pas désinstaller `@sqlite.org/sqlite-wasm` de `package.json` dans cette story — d'autres fichiers `.web.ts` (platform adapters) y font peut-être référence ; le nettoyage complet se fera en Story 8.3
- Les tests Vitest (JSDOM) tombent sur `else → throw` si `isTauri()` et `isNativePlatform()` sont tous les deux `false` — mocker `isTauri` ou `isNativePlatform` dans les tests existants si nécessaire

### WAL Mode Warning

The Android sync feature (`sqlite3_js_db_export`) requires DELETE journal mode (not WAL). The desktop adapter does NOT use WAL either — `Database.load()` uses SQLite default (DELETE mode). Do not add `PRAGMA journal_mode=WAL` to the desktop adapter.

### Files to Create / Modify

| File | Action | Notes |
|------|--------|-------|
| `src/shared/db/index.ts` | MODIFY | Add `createDesktopDb` + `isTauri()` branch, null return for first launch |
| `src/main.tsx` | MODIFY | Skip migrations/seed when `db.value === null` |
| `src/features/sync-vault/model/sync-vault.ts` | MODIFY | `initVault()` triggers DB init on desktop |
| `src-tauri/Cargo.toml` | MODIFY | Add `tauri-plugin-sql` crate |
| `src-tauri/src/lib.rs` | MODIFY | Register SQL plugin |
| `src-tauri/capabilities/default.json` | MODIFY | Add `sql:default` permission |

### Testing Notes

- `createDesktopDb` n'a pas besoin d'unit tests — le plugin Tauri SQL est externe et ne peut pas être mocké dans Vitest. Le test d'intégration se fait via `npm run tauri:dev`.
- `src/shared/db/index.test.ts` et `migrate.test.ts` tournent en JSDOM où `isTauri()` = `false` et `isNativePlatform()` = `false` → ils tomberaient sur le `throw`. Vérifier ces fichiers et mocker `Capacitor.isNativePlatform()` à `true` si ce n'est pas déjà le cas.
- Ajouter un test pour le cas null : mocker `isTauri()` à `true` et `preferencesAdapter.get()` à `err(...)` → `initDb()` doit retourner `ok(null)`.

### Project Structure Notes

- `createDesktopDb` est une fonction privée dans `src/shared/db/index.ts` — pas de fichier séparé, cohérent avec `createAndroidDb` et l'ancienne `createWebDb`
- No new FSD slice needed — this is a `shared/db` internal implementation detail

### References

- Tauri SQL plugin v2: `tauri-plugin-sql` crate + `@tauri-apps/plugin-sql` npm package
- Existing Android proxy pattern: `src/shared/db/index.ts:47-74` (`createAndroidDb`)
- `isTauri()` build-time constant: `src/shared/platform/is-tauri.ts`
- Vault path key: `src/features/sync-vault/model/sync-vault.ts:7` (`VAULT_PATH_KEY = 'vault_path'`)
- Architecture platform adapter strategy: `_bmad-output/planning-artifacts/architecture.md#Platform Strategy`
- Story 8.1 Dev Notes: `_bmad-output/implementation-artifacts/8-1-tauri-initialization-and-project-setup.md#Dev Notes`
- Tauri v2 capabilities system: `src-tauri/capabilities/default.json`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
