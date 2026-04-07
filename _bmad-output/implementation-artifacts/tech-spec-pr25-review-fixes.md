---
title: 'Address PR #25 Review Findings'
type: 'bugfix'
created: '2026-04-07'
status: 'done'
baseline_commit: 'b29aed1ed09ad0d543d0d7cfb13b98366f2135b6'
---

# Address PR #25 Review Findings

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PR #25 (story 8.5 — Desktop CI/CD Pipelines) has three High and several Medium/Low findings that block merge: the desktop E2E spec uses `chromium.connectOverCDP` which is incompatible with `tauri-driver` (W3C WebDriver, not CDP); the `mock-keychain` feature has no release-build guard; and two action tags are unpinned against project supply-chain policy.

**Approach:** Replace the broken CDP-based desktop E2E with a WebDriverIO test, add a compile-time guard to `keychain.rs`, pin the two unpinned action SHAs, collapse the duplicate Tauri build in the PR workflow, cache `tauri-driver`, and tidy the Low findings.

## Boundaries & Constraints

**Always:**
- Keep the existing `tauri-driver` / port-4444 startup convention.
- The E2E test must use W3C WebDriver via `webdriverio` (not CDP, not Playwright).
- Supply-chain policy: all `uses:` references must be pinned to full commit SHAs with a version comment.
- The `mock-keychain` feature must never be buildable in a release (non-debug) binary.

**Ask First:**
- If the `vitest.desktop.config.ts` approach conflicts with any existing vitest config constraints.

**Never:**
- Add a new CI runner or matrix beyond what already exists.
- Change the Tauri app code beyond the `compile_error!` guard.
- Add or change any signing secrets.

</frozen-after-approval>

## Code Map

- `e2e/desktop/vault-flow.spec.ts` — broken desktop E2E spec (CDP); must be rewritten with webdriverio
- `playwright.desktop.config.ts` — Playwright desktop config; obsolete once webdriverio takes over; delete
- `vitest.desktop.config.ts` — new vitest config scoped to `e2e/desktop/`
- `package.json` — add `webdriverio` devDep; update `test:e2e:desktop` script to use vitest
- `src-tauri/src/keychain.rs` — add `compile_error!` guard for mock-keychain in release builds
- `.github/workflows/pr.yml` — pin two action SHAs; remove duplicate Tauri compile-check step; cache tauri-driver; fix `-- --features` syntax
- `.github/workflows/release.yml` — pin two action SHAs
- `_bmad-output/implementation-artifacts/8-5-desktop-cicd-pipelines.md` — add `sprint-status.yaml` to File List

## Tasks & Acceptance

**Execution:**

- [ ] `src-tauri/src/keychain.rs` — prepend `#[cfg(all(feature = "mock-keychain", not(debug_assertions)))] compile_error!("mock-keychain must never be enabled in release builds");` at the top of the file — prevents accidental release shipping of in-memory keychain stub
- [ ] `e2e/desktop/vault-flow.spec.ts` — rewrite using `remote` from `webdriverio` + vitest hooks; capabilities: `{ 'tauri:options': { application: 'src-tauri/target/debug/lekto' } }`; remove all Playwright imports — fixes broken CDP connection
- [ ] `playwright.desktop.config.ts` — delete file — no longer needed after E2E migration to webdriverio+vitest
- [ ] `vitest.desktop.config.ts` (new) — create with `environment: 'node'`, `include: ['e2e/desktop/**/*.spec.ts']`, `testTimeout: 60_000`, `hookTimeout: 60_000`, `pool: 'forks'` with `singleFork: true` — needed to run webdriverio in Node.js context
- [ ] `package.json` — add `"webdriverio": "^9"` to devDependencies; update `test:e2e:desktop` script to `"tauri-driver --port 4444 & sleep 2 && vitest run --config vitest.desktop.config.ts; pkill tauri-driver || true"` — fixes script to use correct test runner; add comment about Linux-only constraint
- [ ] `.github/workflows/pr.yml` — (a) pin `dtolnay/rust-toolchain@stable` → `29eef336d9b2848a0b548edc03f92a220660cdb8 # stable`; (b) pin `tauri-apps/tauri-action@v0` → `84b9d35b5fc46c1e45415bdb6144030364f7ebc5 # v0`; (c) remove the standalone "Tauri compile check" step (the debug build in the E2E section already proves compilation); (d) replace `cargo install tauri-driver` with `actions/cache` over `~/.cargo/bin/tauri-driver`; (e) fix `npx tauri build --debug -- --features mock-keychain` → `npx tauri build --debug --features mock-keychain`; (f) update "Desktop E2E tests" step to `vitest run --config vitest.desktop.config.ts`
- [ ] `.github/workflows/release.yml` — pin `dtolnay/rust-toolchain@stable` and `tauri-apps/tauri-action@v0` to same SHAs as above
- [ ] `_bmad-output/implementation-artifacts/8-5-desktop-cicd-pipelines.md` — add `_bmad-output/implementation-artifacts/sprint-status.yaml` to File List section; mark completed High/Med/Low review items

**Acceptance Criteria:**
- Given `mock-keychain` feature is enabled and `debug_assertions` is disabled (release profile), when `cargo build` is run, then compilation fails with `compile_error!` message.
- Given the desktop E2E step in `pr.yml` runs, when `tauri-driver` is started and the `vitest` command executes, then the test connects via W3C WebDriver (no CDP), completes the vault-creation flow, and reports pass/fail per spec file.
- Given any workflow file references `tauri-apps/tauri-action` or `dtolnay/rust-toolchain`, when the yaml is inspected, then both references use full 40-char commit SHAs with a version comment.
- Given the PR pipeline runs, when the YAML is inspected, then Tauri is only built once (the debug binary for E2E), not twice.

## Design Notes

**WebDriverIO capabilities for tauri-driver:** `tauri:options.application` must be a path to the compiled Tauri binary relative to the project root. On Linux debug builds this is `src-tauri/target/debug/lekto`. The `remote()` call establishes a W3C WebDriver session; tauri-driver spawns and wraps the app.

**Caching tauri-driver:** use `actions/cache` keyed on `cargo-bin-tauri-driver-${{ runner.os }}` with path `~/.cargo/bin/tauri-driver` plus a `cargo install tauri-driver` step guarded by `if: steps.cache-tauri-driver.outputs.cache-hit != 'true'`.

**vitest pool setting:** `pool: 'forks'` with `singleFork: true` ensures the webdriverio session runs in a single Node.js process, preventing test isolation from spawning multiple sessions.

## Verification

**Commands:**
- `cargo build --manifest-path src-tauri/Cargo.toml --features mock-keychain` — expected: compile error about mock-keychain in release builds (NOTE: only triggers in release profile — test with `--release` or check the guard fires for `not(debug_assertions)`)
- `cargo clippy --manifest-path src-tauri/Cargo.toml -- -D warnings` — expected: 0 warnings/errors
- `npm run lint && npm run typecheck` — expected: clean
- `npm test` — expected: all 179 unit tests pass

**Manual checks:**
- `pr.yml`: confirm "Tauri compile check" step is removed and only one Tauri build step exists
- `pr.yml` + `release.yml`: both action pins are 40-char SHAs

## Spec Change Log

