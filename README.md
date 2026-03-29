# letko

An open-source immersive language learning app for Web and Android. Replicates the LingQ reading experience on a fully local architecture — no server, no subscription, data stays on your device.

Import an EPUB or PDF → words color-coded by mastery level → click to translate → vocabulary saved with context. Every step is local and uninterrupted.

BYOK AI: connect your own OpenAI, Anthropic, or Ollama account. The app is fully functional without AI; AI is a progressive enhancement.

## Browser Support

| Browser | Reading & Vocabulary | Cloud Sync |
|---|---|---|
| Chrome / Edge | ✅ Full support | ✅ Via native folder picker |
| Firefox | ✅ Full support | ❌ Not possible |
| Safari | ✅ Full support | ❌ Not possible |
| Android app | ✅ Full support | ✅ Via SAF folder picker |

**Cloud sync** (Google Drive, Syncthing, etc.) requires pointing the vault to a native folder. This relies on the [File System Access API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API) (`showDirectoryPicker`), which Firefox and Safari do not support — [Mozilla has formally opposed the API](https://mozilla.github.io/standards-positions/#file-system-access). On these browsers the vault is stored in OPFS (browser-internal storage), which is fully persistent locally but invisible to external sync tools.

## Tech Stack

| | |
|---|---|
| Framework | React 19 + TypeScript 5.9 (strict) |
| Build | Vite 7 |
| Mobile | Capacitor 8 (Android) |
| Styling | Tailwind CSS v4 + shadcn/ui |
| State | Zustand 5 |
| Routing | React Router v7 |
| Database | SQLite via OPFS (Story 1.2) |
| Error handling | neverthrow |
| Testing | Vitest + Testing Library |

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
    ├── platform/ # Capacitor adapters
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
