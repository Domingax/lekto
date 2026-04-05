# letko

An open-source immersive language learning app for Android and Desktop (Linux/Windows). Fully local architecture — no server, no subscription, data stays on your device inside a portable vault folder.

Import an EPUB or PDF → words color-coded by mastery level → tap/click to translate → vocabulary saved with context. Every step is local and uninterrupted.

BYOK AI: connect your own OpenAI, Anthropic, or Ollama account. The app is fully functional without AI; AI is a progressive enhancement.

## Platform Support

| Platform | Status |
|---|---|
| Android (Capacitor) | Active development |
| Linux Desktop (Tauri — AppImage) | Planned — Epic 8 |
| Windows Desktop (Tauri — NSIS) | Planned — Epic 8 |

**Vault portability:** the vault is a plain folder (`books/` + `lekto.db`) that lives wherever you choose — a local directory, a Syncthing folder, Google Drive, etc. Copying the folder moves all your data. No proprietary sync protocol.

## Tech Stack

| | |
|---|---|
| Framework | React 19 + TypeScript 5.9 (strict) |
| Build | Vite 7 |
| Mobile | Capacitor 8 (Android) |
| Desktop | Tauri v2 (Linux / Windows) — Epic 8 |
| Styling | Tailwind CSS v4 + shadcn/ui |
| State | Zustand 5 |
| Routing | React Router v7 |
| Database | SQLite — Capacitor SQLite (Android) · Tauri SQL plugin (Desktop) |
| Error handling | neverthrow |
| Testing | Vitest + Testing Library · Playwright (Desktop E2E) · Maestro (Android E2E) |

## Getting Started

```bash
npm install
npm run dev
```

## Scripts

```bash
npm run dev           # Start Vite dev server
npm run build         # Type-check + production build
npm run typecheck     # TypeScript strict check
npm run lint          # ESLint (zero warnings enforced)
npm run test          # Run test suite
npm run test:coverage # Coverage report
```

## Project Structure

Follows [Feature-Sliced Design](https://feature-sliced.design/) (FSD):

```
src/
├── app/          # Routing, providers, global CSS
├── pages/        # Page-level components
├── widgets/      # Composite UI blocks
├── features/     # User interactions
├── entities/     # Domain models (Book, Word, VocabEntry…)
└── shared/
    ├── ui/       # shadcn/ui components (re-exported)
    ├── db/       # Drizzle schema + migrations
    ├── platform/ # Platform adapters (*.android.ts / *.desktop.ts)
    ├── stores/   # Zustand stores
    └── lib/      # Utils, types, constants
```

Import direction is strictly unidirectional: `pages → widgets → features → entities → shared`.

## Commits

Uses [Conventional Commits](https://www.conventionalcommits.org/) enforced by commitlint + husky.

```bash
git cz   # Interactive commit via commitizen
```

## Android

Requires Android Studio + Java 17 + `ANDROID_HOME` set.

```bash
npm run build
npx cap sync
npx cap open android
```

## Desktop (Tauri)

Requires Rust toolchain ([rustup](https://rustup.rs/)) and system dependencies listed in the [Tauri prerequisites guide](https://v2.tauri.app/start/prerequisites/).

```bash
npm run tauri:dev     # Start dev window with hot-reload
npm run tauri:build   # Produce AppImage (Linux) or NSIS installer (Windows)
```

**GPU / GBM error on Linux?** If the window fails to open with a `Failed to create GBM buffer` error, disable WebKit GPU compositing:

```bash
WEBKIT_DISABLE_COMPOSITING_MODE=1 npm run tauri:dev
```
