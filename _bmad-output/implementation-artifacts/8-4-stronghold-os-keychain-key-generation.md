# Story 8.4: Stronghold Key Generation via OS Keychain

Status: draft

## Story

As a developer,
I want the Stronghold vault passphrase to be generated randomly at first launch and stored in the OS keychain,
So that the vault encryption key is unique per installation and never hardcoded in the source code.

## Context

Story 8.3 introduced `secure-storage.desktop.ts` with a hardcoded constant `STRONGHOLD_VAULT_KEY = 'lekto-desktop-secure-storage-v1'` passed as the Stronghold passphrase. SonarQube flagged this as a hard-coded credential (S2068, renamed but not resolved). The root cause is architectural: anyone with access to the source code can derive the vault encryption key for any installation.

This story replaces the static constant with a randomly generated passphrase, stored in the OS native keychain (GNOME Keyring on Linux, macOS Keychain, Windows Credential Store) via the `keyring` Rust crate.

**Breaking change:** Existing Stronghold vaults (created during Story 8.3 development) will be inaccessible after this change since the passphrase changes. Developers must delete `~/.local/share/lekto/lekto-secrets.holsd` before first run on the new version. No production users are affected (app not yet released).

**Argon2 salt:** The static salt `b"lekto-stronghold-salt-v1"` in `lib.rs` can remain as-is — its purpose is to slow down brute-force attacks. Since the passphrase is now a 32-byte random hex string (effectively unguessable), the salt's contribution to security is academic. It is not a secret.

## Acceptance Criteria

1. **Given** the app is launched for the first time (no keychain entry exists)
   **When** `secureStorageAdapter.set()` or `get()` is first called
   **Then** a 32-byte random passphrase is generated, stored in the OS keychain under service `lekto`, account `stronghold-passphrase`, and used to open Stronghold

2. **Given** the app has been launched before (keychain entry exists)
   **When** `secureStorageAdapter` initializes
   **Then** the existing passphrase is retrieved from the OS keychain and used — no new passphrase is generated

3. **Given** the passphrase is stored in the OS keychain
   **When** a developer inspects the source code
   **Then** no hardcoded passphrase string is present in any file in the repository

4. **Given** the OS keychain is unavailable (e.g. no secret service running on Linux)
   **When** the app attempts to retrieve or store the passphrase
   **Then** a clear error is surfaced via `err('Secure storage: keychain unavailable')` — the app does not panic or silently fall back to a hardcoded value

5. **Given** the existing test suite for `secure-storage.desktop.ts`
   **When** tests run after this change
   **Then** all tests pass — the Tauri `invoke` call is mocked like any other Tauri plugin

## Tasks / Subtasks

### Commit 1: `chore(deps): add keyring crate for OS keychain access`

- [ ] Task 1: Add `keyring` to `src-tauri/Cargo.toml`
  ```toml
  [dependencies]
  keyring = { version = "3", features = ["default-credential"] }
  ```
  - Run `cargo check` inside `src-tauri/` to verify compilation
  - Note: `keyring` v3 requires `libsecret` on Linux (`libsecret-1-dev` package). Verify it compiles in CI environment. Add to CI apt-get install if needed (see Story 8.5 CI/CD scope).

### Commit 2: `feat(platform): add get_or_create_vault_passphrase Tauri command`

- [ ] Task 2: Create `src-tauri/src/keychain.rs`
  ```rust
  use keyring::Entry;

  const KEYCHAIN_SERVICE: &str = "lekto";
  const KEYCHAIN_ACCOUNT: &str = "stronghold-passphrase";

  #[tauri::command]
  pub fn get_or_create_vault_passphrase() -> Result<String, String> {
      let entry = Entry::new(KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT)
          .map_err(|e| format!("Keychain unavailable: {e}"))?;

      match entry.get_password() {
          Ok(passphrase) => Ok(passphrase),
          Err(keyring::Error::NoEntry) => {
              let passphrase = generate_passphrase();
              entry
                  .set_password(&passphrase)
                  .map_err(|e| format!("Keychain write failed: {e}"))?;
              Ok(passphrase)
          }
          Err(e) => Err(format!("Keychain read failed: {e}")),
      }
  }

  fn generate_passphrase() -> String {
      use std::fmt::Write;
      let mut bytes = [0u8; 32];
      getrandom::getrandom(&mut bytes).expect("getrandom failed");
      bytes.iter().fold(String::with_capacity(64), |mut s, b| {
          let _ = write!(s, "{b:02x}");
          s
      })
  }
  ```
  - Note: `getrandom` is a transitive dependency of many crates already present — verify it's available or add it explicitly.
  - Alternative if `getrandom` is unavailable: use `rand = "0.8"` with `rand::thread_rng().gen::<[u8; 32]>()`.

- [ ] Task 3: Register the command in `src-tauri/src/lib.rs`
  ```rust
  mod keychain;

  pub fn run() {
    tauri::Builder::default()
      // ... existing plugins ...
      .invoke_handler(tauri::generate_handler![keychain::get_or_create_vault_passphrase])
      .run(tauri::generate_context!())
      .expect("error while running tauri application")
  }
  ```

- [ ] Task 4: Run `cargo check` inside `src-tauri/` — zero errors

### Commit 3: `feat(platform): retrieve Stronghold passphrase from OS keychain`

- [ ] Task 5: Modify `src/shared/platform/secure-storage/secure-storage.desktop.ts`
  - Remove the `STRONGHOLD_VAULT_KEY` constant entirely
  - Add a `getVaultPassphrase()` helper that calls `invoke<string>('get_or_create_vault_passphrase')`
  - Update `getClient()` to await `getVaultPassphrase()` before calling `Stronghold.load()`
  - Resulting shape:
    ```typescript
    import { invoke } from '@tauri-apps/api/core'

    async function getVaultPassphrase(): Promise<string> {
      return invoke<string>('get_or_create_vault_passphrase')
    }

    async function getClient(): Promise<Client> {
      if (_client) return _client
      const dir = await appDataDir()
      const vaultPath = `${dir}/lekto-secrets.holsd`
      const passphrase = await getVaultPassphrase()
      _stronghold = await Stronghold.load(vaultPath, passphrase)
      try {
        _client = await _stronghold.loadClient(STRONGHOLD_CLIENT)
      } catch {
        _client = await _stronghold.createClient(STRONGHOLD_CLIENT)
      }
      return _client
    }
    ```
  - `invoke` is already available via `@tauri-apps/api/core` (installed in Story 8.1)

- [ ] Task 6: Update `src/shared/platform/secure-storage/secure-storage.desktop.test.ts`
  - Add `vi.doMock('@tauri-apps/api/core', () => ({ invoke: vi.fn().mockResolvedValue('mock-passphrase-hex') }))` in `beforeEach`
  - Verify all existing tests still pass
  - Add test: `getClient() calls invoke('get_or_create_vault_passphrase')`

- [ ] Task 7: Quality gate
  - `npm run lint` — zero warnings
  - `npm run typecheck` — zero errors
  - `npm run test` — all tests pass
  - Manual smoke test: delete `~/.local/share/lekto/lekto-secrets.holsd`, run `npm run tauri:dev`, confirm Stronghold initializes without error and a new keychain entry is created

## Dev Notes

### Why `keyring` and not a Tauri plugin?

There is no official `tauri-plugin-keyring`. The `keyring` Rust crate is the established cross-platform abstraction (used by tools like `cargo`, `aws-cli`). It uses:
- **Linux:** `libsecret` (GNOME Keyring) or `KWallet` depending on the desktop environment
- **macOS:** Security framework (Keychain)
- **Windows:** Windows Credential Manager

### Keychain availability on Linux headless environments

In CI (GitHub Actions Ubuntu runners), there is no GNOME session, so `libsecret` may fail. This affects Story 8.5 (CI/CD) which needs to compile and run Tauri. Options:
- Use `keyring` feature `mock-credential` in test/CI builds
- Or gate the `get_or_create_vault_passphrase` command behind `#[cfg(not(test))]` and mock it in Vitest
- Document this constraint in Story 8.5

### Passphrase persistence across OS reinstalls

If the user reinstalls their OS, the keychain entry is lost. When the app runs again:
- It generates a new passphrase
- The existing `.holsd` vault was encrypted with the old passphrase → it is unreadable
- The user loses their stored API keys (not their vault books/vocabulary, which are stored separately in plain vault files)

This is acceptable behavior — API keys can be re-entered. Document in user-facing notes if/when a settings screen is built.

### Files to Create / Modify

| File | Action |
|------|--------|
| `src-tauri/Cargo.toml` | MODIFY — add `keyring` dependency |
| `src-tauri/src/keychain.rs` | CREATE — `get_or_create_vault_passphrase` command |
| `src-tauri/src/lib.rs` | MODIFY — declare `mod keychain`, register command |
| `src/shared/platform/secure-storage/secure-storage.desktop.ts` | MODIFY — remove hardcoded constant, call `invoke` |
| `src/shared/platform/secure-storage/secure-storage.desktop.test.ts` | MODIFY — mock `invoke`, add keychain test |

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
