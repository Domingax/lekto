# Deferred Work

---

## Android vault — tree URI permissions (ACTION_OPEN_DOCUMENT_TREE)

**Source:** Adversarial review of `fix/android-directory-picker` (PR #15)

`FilePicker.pickDirectory()` returns a content tree URI (e.g. `content://com.android.externalstorage.documents/tree/primary%3A...`). On Android, access to a tree URI granted via `ACTION_OPEN_DOCUMENT_TREE` does **not** persist across app restarts unless `takePersistableUriPermission` is explicitly called and the URI is stored.

Before implementing vault read/write on Android, verify:
- Whether `@capawesome/capacitor-file-picker` calls `takePersistableUriPermission` internally after `pickDirectory()`
- Whether the Capacitor Filesystem plugin accepts raw tree URIs or requires a document URI derived from it
- If neither handles persistence, add a native call to persist the URI permission and store the URI in secure storage

**Surfaces during:** any story implementing vault file read/write on Android.

---

## Desktop E2E — tauri-driver startup race condition

**Source:** Adversarial review of PR #25 fixes (story 8.5)

`test:e2e:desktop` starts `tauri-driver` in background with `& sleep 2` before running vitest. The 2-second sleep is a heuristic that can fail on slow/loaded CI runners, causing WebDriverIO to attempt connection on a closed port with a misleading error.

**Fix when addressed:** Replace `sleep 2` with a retry loop or health-check polling `localhost:4444` until tauri-driver responds (or timeout).

**Surfaces during:** any story that touches desktop E2E CI steps.

---

## Desktop E2E — hardcoded binary name in vault-flow.spec.ts

**Source:** Adversarial review of PR #25 fixes (story 8.5)

`e2e/desktop/vault-flow.spec.ts` hardcodes `application: 'src-tauri/target/debug/lekto'`. If the binary is renamed in `src-tauri/Cargo.toml`, the spec silently breaks (tauri-driver can't find the app).

**Fix when addressed:** Read the binary name from `src-tauri/Cargo.toml` or pass it via environment variable; document the assumption in the spec file.

**Surfaces during:** any story renaming the Tauri binary or reorganising build outputs.

---

## Desktop CI — tauri-driver cache key assumes single runner OS

**Source:** Adversarial review of PR #25 fixes (story 8.5)

`pr.yml` caches `~/.cargo/bin/tauri-driver` with key `tauri-driver-${{ runner.os }}-${{ hashFiles('src-tauri/Cargo.lock') }}`. The current single-OS setup masks any issue, but the pattern would reuse stale binaries if runners are changed or a matrix is introduced.

**Surfaces during:** any story that adds a multi-platform PR matrix or changes the Ubuntu runner version.
