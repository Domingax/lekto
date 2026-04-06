# Story 8.1: Tauri Initialization & Project Setup

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want Tauri v2 integrated into the project alongside Capacitor,
so that I can build and run the app as a native desktop application on Linux and Windows.

## Acceptance Criteria

1. **Given** the repository is cloned on a fresh machine **When** the developer runs `npm run tauri:dev` **Then** the Tauri desktop window opens and the app renders correctly without errors

2. **Given** Tauri is configured **When** a developer inspects the project **Then** `src-tauri/` exists with `tauri.conf.json`, `Cargo.toml`, and a minimal Rust backend — Capacitor's `android/` directory coexists without conflict

3. **Given** the project has both Tauri and Capacitor targets **When** `npm run build` runs **Then** it produces a standard `dist/` web bundle — no separate `build:android` or `build:tauri` scripts exist; the Tauri CLI calls `npm run build` internally via `beforeBuildCommand`, so one Vite config serves both platforms (with `__TAURI__` differing between invocations)

4. **Given** Tauri is configured **When** the developer runs `npm run tauri:build` **Then** an AppImage is produced on Linux and an NSIS installer on Windows without errors

5. **Given** `isTauri()` is defined as a build-time Vite constant **When** feature code checks platform **Then** `isTauri()` returns `true` in a Tauri build and `false` in a Capacitor/Android build — no runtime import of Tauri APIs in non-Tauri builds

## Tasks / Subtasks

### Commit 1: `feat(platform): add isTauri() build-time constant and Vite define`

- [x] Task 1: Install Tauri v2 npm packages (AC: #1, #4)
  - [x] Run `npm install --save-dev @tauri-apps/cli@^2`
  - [x] Run `npm install @tauri-apps/api@^2`
  - [x] Verify Rust toolchain is available (`rustc --version`, `cargo --version`) — document that this is a prerequisite in AGENTS.md Dev Setup section
  - [x] Add `src-tauri/target/` to `.gitignore` (Rust build artifacts — can be very large)

- [x] Task 2: Create `src/shared/platform/is-tauri.ts` (AC: #5)
  - [x] Create `src/shared/platform/is-tauri.ts`:
    ```typescript
    // Build-time constant injected by Vite define.
    // Set to true only when the Tauri CLI builds/serves the frontend.
    declare const __TAURI__: boolean

    export function isTauri(): boolean {
      return typeof __TAURI__ !== 'undefined' && __TAURI__
    }
    ```
  - [x] Export `isTauri` from `src/shared/platform/index.ts` barrel

- [x] Task 3: Update `vite.config.ts` to inject `__TAURI__` define (AC: #5)
  - [x] Add `define` block to `vite.config.ts`:
    ```typescript
    define: {
      // Set to true by the Tauri CLI via TAURI_ENV_TARGET env var.
      // Ensures Tauri-only imports are tree-shaken out of Capacitor/web builds.
      '__TAURI__': JSON.stringify(!!process.env['TAURI_ENV_TARGET']),
    },
    ```
  - [x] No other changes to `vite.config.ts` — the `server.headers` COOP/COEP block and `optimizeDeps.exclude` remain

- [x] Task 4: Quality gate — commit 1
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all existing tests pass

### Commit 2: `feat(tauri): initialize src-tauri with minimal Rust backend`

- [x] Task 5: Initialize Tauri v2 project (AC: #2, #3)
  - [x] Run `npx @tauri-apps/cli init` from the project root — it will create `src-tauri/`
  - [x] When prompted:
    - App name: `lekto`
    - Window title: `lekto`
    - Web assets location: `../dist`
    - Dev server URL: `http://localhost:5173`
    - Frontend dev command: `npm run dev`
    - Frontend build command: `npm run build`
  - [x] Verify `src-tauri/` contains: `tauri.conf.json`, `Cargo.toml`, `Cargo.lock`, `build.rs`, `src/main.rs`, `src/lib.rs`, `icons/`
  - [x] Confirm `android/` directory is untouched — the two directories are independent

- [x] Task 6: Validate and tune `src-tauri/tauri.conf.json` (AC: #1, #4)
  - [x] Confirm `productName` is `"lekto"`, `identifier` is `"com.lekto.app"` (matches `capacitor.config.ts`)
  - [x] Confirm `build.devUrl` is `"http://localhost:5173"` and `build.frontendDist` is `"../dist"`
  - [x] Set `bundle.targets` to `["appimage", "nsis"]` — only the two required targets, nothing else
  - [x] Set `bundle.active` to `true`
  - [x] Set `app.security.csp` to `null` for development — revisit when plugins need CSP entries in later stories
  - [x] Do NOT add any plugins to `tauri.conf.json` yet — plugins are added in Stories 8.2 and 8.3

- [x] Task 7: Validate `src-tauri/Cargo.toml` (AC: #2)
  - [x] Confirm `[package] name = "lekto"` and `edition = "2021"`
  - [x] Keep the `[dependencies]` section minimal — only `tauri` (no plugins):
    ```toml
    [dependencies]
    tauri = { version = "2", features = [] }
    serde = { version = "1", features = ["derive"] }
    serde_json = "1"

    [build-dependencies]
    tauri-build = { version = "2", features = [] }
    ```
  - [x] Do NOT add `tauri-plugin-sql`, `tauri-plugin-fs`, or any other plugin crate — those belong in Stories 8.2 and 8.3
  - [x] Add `src-tauri/target/` to `.gitignore` if not already done in Task 1

- [x] Task 8: Add npm scripts for Tauri (AC: #1, #4)
  - [x] Add to `package.json` scripts:
    ```json
    "tauri:dev": "tauri dev",
    "tauri:build": "tauri build"
    ```
  - [x] The existing `build` script (`tsc -b && vite build`) is **unchanged** — Tauri calls it internally via `beforeBuildCommand`
  - [x] Do NOT add a separate `build:tauri` or `build:desktop` script — one `build` command serves both platforms (AC: #3)

- [x] Task 9: Smoke-test Tauri dev build locally (AC: #1)
  - [x] Run `npm run tauri:dev` — confirm the desktop window opens with the app rendered
  - [x] Confirm no TypeScript errors in the Vite output during Tauri dev server startup
  - [x] Confirm `android/` builds still work: `npm run build` followed by `npx cap sync android` — no regressions

- [x] Task 10: Quality gate — commit 2
  - [x] `npm run lint` — zero warnings
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all existing tests pass (Tauri packages must not break unit tests — they are never imported in test paths)
  - [x] `npm run build` — exits zero and produces `dist/` usable by both Capacitor and Tauri

## Dev Notes

### What This Story Delivers

Tauri v2 scaffolded alongside the existing Capacitor project. No feature code changes — this story is pure infrastructure. The deliverables are:

1. `src-tauri/` with a minimal Rust backend (no plugins yet)
2. `src/shared/platform/is-tauri.ts` — the single source of truth for desktop detection
3. `vite.config.ts` updated with `__TAURI__` define
4. `npm run tauri:dev` and `npm run tauri:build` scripts

Subsequent stories wire in actual plugins: Story 8.2 adds the SQL plugin; Story 8.3 adds filesystem, file-picker, secure-storage, and preferences adapters.

### isTauri() — Critical Architecture Rule

`isTauri()` is a **build-time constant**, not a runtime check. The Tauri CLI sets `TAURI_ENV_TARGET` when it invokes `vite build`; the regular `npm run build` does not set it. Vite's `define` replaces `__TAURI__` at bundle time, enabling tree-shaking.

**Correct use:**
```typescript
import { isTauri } from '@/shared/platform'

export const filesystemAdapter =
  isTauri() ? desktopFilesystemAdapter : androidFilesystemAdapter
```

**This pattern is NOT yet applied in 8.1.** Adapter index files still use `Capacitor.isNativePlatform()`. The switch to `isTauri()` in adapter selectors happens in Stories 8.2 (DB layer) and 8.3 (all other adapters).

**Never do this:**
```typescript
// ❌ Runtime sniffing — breaks tree-shaking, imports Tauri internals in Android builds
import { invoke } from '@tauri-apps/api/core'
const isDesktop = typeof window.__TAURI_INTERNALS__ !== 'undefined'
```

### Tauri v2 vs v1 — Key Differences

This project uses **Tauri v2** (not v1). Key differences developers may encounter:

- Package names: `@tauri-apps/cli@^2`, `@tauri-apps/api@^2` (not `^1`)
- `tauri.conf.json` schema: `"$schema": "https://schema.tauri.app/config/2"` — the v1 `"tauri"` top-level key is gone
- Plugin system: plugins are separate crates (`tauri-plugin-sql`, `tauri-plugin-fs`, etc.) and separate npm packages (`@tauri-apps/plugin-sql`, etc.) — not bundled in the core
- `src/main.rs` entry: `tauri::Builder::default().run(tauri::generate_context!())` — no `invoke_handler` needed until plugins are added

### Project Structure After This Story

```
lekto/
├── android/                  ← Capacitor Android (unchanged)
├── src/
│   └── shared/platform/
│       ├── is-tauri.ts       ← NEW: isTauri() build-time constant
│       └── index.ts          ← UPDATED: exports isTauri
├── src-tauri/                ← NEW: Tauri v2 backend
│   ├── tauri.conf.json
│   ├── Cargo.toml
│   ├── Cargo.lock
│   ├── build.rs
│   ├── src/
│   │   ├── main.rs
│   │   └── lib.rs
│   └── icons/
├── vite.config.ts            ← UPDATED: __TAURI__ define added
└── package.json              ← UPDATED: tauri:dev + tauri:build scripts
```

### Coexistence With Capacitor

Tauri and Capacitor coexist without conflict because:
- `src-tauri/` is a separate directory from `android/` — they never overlap
- Both consume the same `dist/` output from `npm run build`
- Capacitor reads `dist/` via `capacitor.config.ts` (`webDir: 'dist'`)
- Tauri reads `dist/` via `tauri.conf.json` (`build.frontendDist: "../dist"`)
- The Vite dev server runs once; both Tauri dev and Capacitor live-reload connect to it

No changes to `capacitor.config.ts` are needed.

### Rust Toolchain Prerequisite

Tauri requires a Rust toolchain. Developers must have:
- `rustup` installed
- Stable toolchain: `rustup default stable`
- Linux-specific build deps: `sudo apt install libwebkit2gtk-4.1-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev` (for Ubuntu/Debian)

This is a one-time local setup. CI/CD handles this via the `tauri-action` GitHub Action (Story 8.4). Update `AGENTS.md` Dev Setup section to document this requirement.

### Tree-Shaking Guarantee

Because `__TAURI__` is set to `false` in non-Tauri builds, any code inside `if (isTauri()) { ... }` blocks importing from `@tauri-apps/api` will be dead-code-eliminated by Vite/Rollup. This means Android APKs and web builds never bundle Tauri code.

Verify this in a non-Tauri build: `npm run build` should not include any `@tauri-apps` modules in the output bundle.

### No Plugin Changes in This Story

Story 8.1 scope is intentionally minimal: scaffolding only. The following are explicitly **out of scope** and belong in later stories:

| Plugin | Story |
|---|---|
| `tauri-plugin-sql` | Story 8.2 |
| `tauri-plugin-fs` | Story 8.3 |
| `tauri-plugin-dialog` | Story 8.3 |
| `tauri-plugin-stronghold` | Story 8.3 |
| `tauri-plugin-store` | Story 8.3 |
| GitHub Actions for desktop CI/CD | Story 8.4 |

Do not install these crates or npm packages in this story.

### Existing `.web.ts` Adapters — Do Not Touch

The architecture notes that `.web.ts` adapter files are transitional artifacts. Story 8.1 does **not** remove or replace them — the platform adapter selectors still use `Capacitor.isNativePlatform()`. These are replaced story by story (8.2 for DB, 8.3 for all others).
