---
stepsCompleted: [1, 2]
inputDocuments:
  - '_bmad-output/planning-artifacts/product-brief-lingq-2026-03-02.md'
workflowType: 'research'
lastStep: 1
research_type: 'technical'
research_topic: 'Technical stack for LingQ Open (web + Android, local-first language learning app)'
research_goals: 'Propose the best technical stack based on the product brief constraints: Web + Android, local-first, vault sync, EPUB/PDF parsing, immersive reader, BYOK LLM, TTS, open source'
user_name: 'Damien'
date: '2026-03-03'
web_research_enabled: true
source_verification: true
---

# Research Report: Technical Stack — LingQ Open

**Date:** 2026-03-03
**Author:** Damien
**Research Type:** Technical

---

## Research Overview

Technical stack selection for LingQ Open — a local-first, open-source language learning app targeting Web and Android. Based on product-brief-lingq-2026-03-02.

---

## Technical Research Scope Confirmation

**Research Topic:** Technical stack for LingQ Open (web + Android, local-first language learning app)
**Research Goals:** Propose the best technical stack based on the product brief constraints: Web + Android, local-first, vault sync, EPUB/PDF parsing, immersive reader, BYOK LLM, TTS, open source

**Technical Research Scope:**

- Architecture Analysis - design patterns, frameworks, system architecture
- Implementation Approaches - development methodologies, coding patterns
- Technology Stack - languages, frameworks, tools, platforms
- Integration Patterns - APIs, protocols, interoperability
- Performance Considerations - scalability, optimization, patterns

**Research Methodology:**

- Current web data with rigorous source verification
- Multi-source validation for critical technical claims
- Confidence level framework for uncertain information
- Comprehensive technical coverage with architecture-specific insights

**Scope Confirmed:** 2026-03-03

---

## Technology Stack Analysis

### Cross-Platform Framework: Three-Way Comparison

Trois candidats sérieux ont été évalués : **Capacitor + React/Vite**, **Flutter**, et **Expo (React Native)**.

---

#### Flutter — Analyse complète

Flutter est un framework Google en Dart qui compile vers des moteurs de rendu natifs (Skia/Impeller). En 2025 il est utilisé en production par BMW, Alibaba et d'autres.

**Atouts pour LingQ Open :**

- **Performance Android native** : le rendu Impeller est significativement plus fluide qu'un WebView. Animations, scroll, transitions sont au niveau natif.
- **Drift (SQLite ORM)** : équivalent Dart de Drizzle, typé, réactif, fonctionne sur Web + Android + toutes plateformes. Idéal pour local-first.
- **File picker Android (SAF)** : le package `docman` utilise le Storage Access Framework d'Android — gestion des permissions persistées sur dossier utilisateur, compatible Android 11+.
- **TTS** : `flutter_tts` couvre Android et Web.
- **Flutter Web en 2025** : compilation WebAssembly disponible, hot reload web, déployé en production. 40–50% plus rapide qu'en 2024.
- **Un seul codebase** : Web + Android + iOS futur sans changement d'architecture.

**Limites pour LingQ Open :**

- **flutter_epub_viewer est une WebView** : ce package (le plus populaire) est construit sur epub.js + flutter_inappwebview. Le rendu EPUB se passe donc dans un WebView JavaScript même en Flutter — on perd l'avantage du rendu natif Dart pour le cœur du produit, et les interactions mot-à-mot nécessitent un pont JS↔Dart.
- **epub_view (alternative native Dart)** : rend le texte nativement avec des widgets Flutter Text. Permet des spans mot-à-mot en Flutter. Mais moins mature, moins maintenu, et la customisation fine (couleur par mot, clic par mot) requiert plus de travail personnalisé.
- **Flutter Web — limitations critiques** : pas de Ctrl+F natif dans la page, pas de SSR/SEO, WasmGC non supporté sur Safari/WebKit iOS (bloque le WASM sur iPhone), bundle initial lourd.
- **Dart** : plus petit pool de contributeurs open source que TypeScript/React. Frein à l'objectif d'un projet forkable et contributable.
- **Debugging Flutter Web** : les outils web classiques (DevTools, Playwright) ne s'appliquent pas directement — renderer custom, pas de HTML DOM standard.

_Source: [Flutter Web 2025 — Is it production ready?](https://nurobyte.medium.com/flutter-web-in-2025-production-ready-or-still-beta-9a04dfb32ceb), [Flutter web limitations — cleancodestack](https://cleancodestack.com/choosing-flutter-web-in-2025-top-8-issues/), [flutter_epub_viewer pub.dev](https://pub.dev/packages/flutter_epub_viewer), [Drift docs](https://drift.simonbinder.eu/), [docman package](https://pub.dev/packages/docman)_

---

#### Tableau comparatif : trois frameworks

| Critère | **Capacitor + React/Vite** | **Flutter** | **Expo (React Native)** |
|---|---|---|---|
| **Rendu lecteur immersif (word spans)** | ✅ HTML `<span>` trivial | ⚠️ WebView (epub_viewer) ou widgets Text custom (epub_view) | ⚠️ WebView ou composants RN custom |
| **EPUB parsing** | ✅ foliate-js / epub.js natif web | ⚠️ WebView pour flutter_epub_viewer ; epub_view natif mais moins mature | ⚠️ WebView wrapping |
| **PDF parsing** | ✅ PDF.js natif | ⚠️ packages tiers (syncfusion, pdfx) | ⚠️ react-native-pdf |
| **Support Web first-class** | ✅ HTML/CSS standard | ⚠️ Canvas renderer, pas de DOM, bugs Safari WASM | ⚠️ React Native Web limité |
| **File System Access API (web)** | ✅ Natif navigateur | ❌ Non disponible (canvas renderer) | ❌ Non disponible |
| **File picker Android (vault)** | ⚠️ @capacitor/filesystem + picker | ✅ docman (SAF, permissions persistées) | ✅ expo-filesystem |
| **Performance Android** | ⚠️ WebView-based | ✅ Natif (Impeller) | ✅ Near-native |
| **Base de données locale** | ✅ sql.js + Drizzle | ✅ Drift (réactif, typé, excellent) | ✅ expo-sqlite + Drizzle |
| **Stockage sécurisé clés API** | ✅ capacitor-secure-storage | ✅ flutter_secure_storage | ✅ expo-secure-store |
| **TTS** | ✅ Web Speech API + plugin | ✅ flutter_tts | ✅ expo-speech |
| **Accessibilité contributeurs** | ✅ TypeScript/React (pool maximal) | ⚠️ Dart (pool plus restreint) | ✅ TypeScript/React |
| **Tests E2E** | ✅ Playwright (web) + Maestro (Android) | ⚠️ Flutter Driver / Maestro limité | ✅ Maestro + EAS |
| **Ctrl+F in-page (web)** | ✅ Natif navigateur | ❌ Non supporté | ⚠️ Limité |
| **Safari/iOS futur** | ✅ Compatible | ⚠️ WASM non supporté WebKit | ✅ Compatible |

---

#### Core Decision: Pourquoi Capacitor reste recommandé

Le **lecteur immersif** est le cœur de LingQ Open. Il nécessite de rendre chaque mot comme un élément cliquable et coloré individuellement :

```html
<span class="word mastery-0" data-word="serendipity">serendipity</span>
```

En HTML/CSS, c'est trivial. En Flutter avec `flutter_epub_viewer`, le rendu EPUB passe de toute façon par un WebView interne (epub.js) — l'interaction mot-à-mot requiert un pont JS↔Dart. La seule voie Flutter entièrement native serait `epub_view` avec des widgets `Text` custom, mais ce package est moins mature et demande un travail considérable de personnalisation.

**Flutter serait la meilleure option si :**
- La priorité absolue est la fluidité Android native
- Le support web est secondaire (app Android principalement)
- L'équipe maîtrise Dart

**Capacitor + React/Vite reste recommandé pour LingQ Open parce que :**
- L'interface de lecture est web-native par nature (HTML/CSS/JS)
- foliate-js, PDF.js, File System Access API sont tous natifs au navigateur
- Le pool de contributeurs TypeScript/React est le plus large pour un projet open source
- Les tests web (Playwright) sont plus matures que les équivalents Flutter
- Safari/iOS future compatibilité sans refactoring

_Source: [Capacitor vs React Native — nextnative.dev](https://nextnative.dev/blog/capacitor-vs-react-native), [Capgo comparison](https://capgo.app/blog/comparing-react-native-vs-capacitor/), [Flutter Web production readiness 2025](https://medium.com/@tiger.chirag/flutter-web-in-2025-27d17cd77f65)_

---

---

### Architectural Pattern: Import → Tokenize → Store → Render

> **Insight clé (observée sur LingQ et Readlang)** : le lecteur n'analyse jamais l'EPUB ou le PDF en temps réel. Le fichier est transformé à l'import en une représentation structurée stockée en base de données. Le lecteur ne fait que lire et afficher cette représentation.

#### Pipeline LingQ (reverse-engineered depuis la console)

```
IMPORT (une fois, en arrière-plan)
  EPUB/PDF
    └→ Extraction texte + structure (chapitres, paragraphes)
    └→ Tokenisation : mots | ponctuation | espaces | balises
    └→ Stockage en base (chaque token avec wordId, type, position)

LECTURE (runtime, 0 parsing)
  Base de données (tokens)
    └→ Rendu HTML : <span id="w{wordId}" class="sentence-item {mastery}">mot</span>
  Base de données (vocabulaire)
    └→ Lookup statut/traduction par wordId → couleur du span
```

**Format de token observé dans le network LingQ :**
```json
{ "opentag": "h1" }
{ "text": "Chapter", "wordId": 506855, "indexInSentence": 1, "index": 1 }
{ "whitespace": " " }
{ "punct": "2:", "isNumber": true }
```

#### Implications pour LingQ Open

1. **EPUB.js / PDF.js uniquement à l'import** — pas besoin de ces bibliothèques dans le lecteur lui-même ; elles servent uniquement à l'extraction de texte.
2. **Le lecteur est un renderer de données** — trivial sur toutes les plateformes (HTML spans, Flutter InlineSpan, RN Text).
3. **Le choix du framework se rééquilibre** — Flutter redevient compétitif pour le lecteur si l'EPUB/PDF est traité en amont.
4. **Schéma SQLite** devient le cœur de l'architecture.

#### Schéma SQLite proposé (Drizzle)

```typescript
// books
export const books = sqliteTable('books', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  title: text('title').notNull(),
  fileName: text('file_name').notNull(),
  language: text('language').notNull(),
  createdAt: integer('created_at').notNull(),
});

// sections (chapitres EPUB / pages PDF)
export const sections = sqliteTable('sections', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  bookId: integer('book_id').notNull().references(() => books.id),
  index: integer('index').notNull(),
  title: text('title'),
});

// tokens (contenu pré-tokenisé)
export const tokens = sqliteTable('tokens', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  sectionId: integer('section_id').notNull().references(() => sections.id),
  index: integer('index').notNull(),
  type: text('type').notNull(),   // 'word' | 'whitespace' | 'punct' | 'tag'
  text: text('text').notNull(),
  wordKey: text('word_key'),      // clé normalisée (minuscule) → FK vers vocabulary
});

// vocabulary (un enregistrement par mot unique dans la langue)
export const vocabulary = sqliteTable('vocabulary', {
  wordKey: text('word_key').primaryKey(),  // ex: "serendipity"
  language: text('language').notNull(),
  status: integer('status').notNull().default(0), // 0=unknown,1-4=learning,5=known
  translation: text('translation'),
  notes: text('notes'),
  updatedAt: integer('updated_at'),
});
```

#### Tokenisation multi-langue

La tokenisation à l'import est la brique la plus sensible. Elle doit gérer :
- **Langues latines** : regex `/\b\w+\b/` suffit pour un MVP
- **CJK (Chinois, Japonais, Coréen)** : tokenisation caractère par caractère ou bibliothèques spécialisées (kuromoji pour le japonais)
- **Arabe / Hébreu** : tokenisation par espace avec normalisation de la direction

**Bibliothèques de tokenisation JavaScript (web/Capacitor) :**
- `wink-nlp` — léger, rapide, langues latines + tokenisation de base
- `kuromoji` — japonais (port JS)
- Regex custom — suffisant pour le MVP latin

**Bibliothèques Dart (Flutter) :**
- Pas d'équivalent mature à wink-nlp en Dart
- Tokenisation regex custom + intégration WebView pour kuromoji si besoin

> **Impact sur le choix de framework** : l'écosystème NLP/tokenisation est significativement plus riche en JavaScript/TypeScript qu'en Dart. C'est un avantage supplémentaire pour Capacitor + React sur Flutter pour cette architecture.

---

### Programming Language & Runtime

**TypeScript 5.x** across the entire stack:
- Single language for web, Android (Capacitor bridge), build scripts, and tests
- Type safety for vocabulary data models (Word, Book, ReadingProgress)
- Excellent tooling: Vite, Drizzle ORM, Vitest — all TypeScript-first
- Maximum contributor accessibility

---

### Development Frameworks and Libraries

#### Core Stack

| Layer | Technology | Rationale |
|---|---|---|
| Build tool | **Vite 6** | Instant HMR, fast builds, native ESM |
| UI Framework | **React 19** | Largest open-source ecosystem, Capacitor officially supports it |
| Mobile wrapper | **Capacitor 6** | Web → Android bridge, official Ionic support |
| Routing | **React Router v7** | File-based routing, data loaders, well-documented |
| State management | **Zustand** | Minimal boilerplate, no provider wrapping, easy to test |
| Styling | **Tailwind CSS v4** | Native CSS layers, no JIT, consistent design tokens |
| UI Components | **shadcn/ui** | Headless, accessible, copy-paste (no dependency lock-in) |

#### EPUB Parsing

Two mature options:

| Library | Stars | Maturity | Notes |
|---|---|---|---|
| **foliate-js** | Active | Production (used in GNOME Books) | Modern ES modules, no build step, supports EPUB 2/3, CBZ, FB2 |
| **epub.js** | ~7k | Stable, slower updates | Simpler API, large community, well-documented |

**Recommendation: foliate-js** for its modernity and broader format support. epub.js as fallback if API complexity is a concern.

_Source: [foliate-js — GitHub](https://github.com/johnfactotum/foliate-js), [epub.js — GitHub](https://github.com/futurepress/epub.js)_

#### PDF Parsing

**PDF.js (pdfjs-dist)** — Mozilla's standard, the only serious web-native option. Extracts text per page, renders to canvas. Used by Firefox, Google Drive, and countless apps.

**Word tokenization challenge (critical):** EPUB gives HTML structure (words are in DOM nodes). PDF is a flat character stream — extracting word boundaries with positions requires post-processing of PDF.js's text content layer. This is non-trivial for CJK languages but manageable for Latin scripts.

---

### Database and Storage Technologies

#### Architecture: Vault-Based SQLite

The vault is a user-managed folder. All app data lives inside it as portable files:

```
vault/
├── books/
│   ├── my-book.epub
│   └── another.pdf
├── vocabulary.db          ← SQLite database (portable)
└── progress.json          ← Reading positions (simple JSON)
```

The `.db` file travels with the vault → multi-device sync is transparent.

| Technology | Role | Platform |
|---|---|---|
| **sql.js / wa-sqlite** | SQLite in browser (WASM) | Web |
| **@capacitor-community/sqlite** | SQLite on device | Android |
| **Drizzle ORM** | TypeScript ORM, schema-first, migrations | Both |
| **IndexedDB (Dexie.js)** | Persist FileSystemDirectoryHandle | Web only |

**Drizzle + expo-sqlite integration is well-established** (December 2025 production articles confirm stability). The same Drizzle schema works across both sql.js and Capacitor SQLite drivers.

_Source: [Drizzle ORM Expo SQLite docs](https://orm.drizzle.team/docs/connect-expo-sqlite), [Building local-first apps with Expo SQLite and Drizzle](https://israataha.com/blog/build-local-first-app-with-expo-sqlite-and-drizzle/), [Expo blog — Modern SQLite](https://expo.dev/blog/modern-sqlite-for-react-native-apps)_

---

### Vault Synchronization (File System Access)

#### Web: File System Access API

The user picks a folder once via `showDirectoryPicker()`. The `FileSystemDirectoryHandle` is persisted in IndexedDB and restored on next app open. Files can be read and written directly in the vault folder.

```typescript
// User picks vault folder once
const handle = await window.showDirectoryPicker({ mode: 'readwrite' })
// Persisted in IndexedDB for subsequent sessions
await db.put('config', handle, 'vaultHandle')
```

**Browser support (2025):** Chrome, Edge, Firefox (with OPFS fallback for sandboxed contexts). Safari support is partial.

**Android support:** OPFS (Origin Private File System) is now available on Android — providing 5–10x faster file I/O than the File API, especially for files on microSD.

_Source: [MDN — File System API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API), [MDN — OPFS](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system), [OPFS on Android — chromestatus.com](https://chromestatus.com/feature/5079634203377664)_

#### Android: @capacitor/filesystem

On Android, the Capacitor Filesystem plugin provides access to the device file system. **Limitation on Android 11+:** `Directory.Documents` only allows access to files/folders created by the app. For a user-visible vault, a custom file picker flow is needed.

**Recommended approach for Android:** Use the Capacitor File Picker plugin (`@capawesome-team/capacitor-file-picker`) to let the user select their vault folder, then use `@capacitor/filesystem` for subsequent read/write operations.

_Source: [Capacitor Filesystem API](https://capacitorjs.com/docs/v5/apis/filesystem), [Capawesome File Picker](https://capawesome.io/plugins/file-picker/)_

---

### Security: API Key Storage

| Platform | Solution | Backing Store |
|---|---|---|
| Android | `@aparajita/capacitor-secure-storage` | Android Keystore |
| Web | `@aparajita/capacitor-secure-storage` | Encrypted localStorage |

This plugin supports AES-256 encryption and biometric unlock. API keys are **never written to the vault folder** — device-local only, reconfigured on each new device.

_Source: [capacitor-secure-storage — GitHub](https://github.com/aparajita/capacitor-secure-storage), [Expo SecureStore docs](https://docs.expo.dev/versions/latest/sdk/securestore/)_

---

### Integration Patterns

#### TTS Audio

| Platform | API | Notes |
|---|---|---|
| Web | `window.speechSynthesis` (Web Speech API) | Native browser, no setup required |
| Android | `@capacitor-community/text-to-speech` | Wraps Android TTS |

Capacitor plugin bridges both under a unified API. No additional setup required for the end user on either platform.

_Source: [Expo Speech docs](https://docs.expo.dev/versions/latest/sdk/speech/) (reference), [React Native TTS comparison — Netguru](https://www.netguru.com/blog/react-native-text-to-speech)_

#### Dictionary Services

WordReference, Reverso, Linguee, and Google Translate do not offer free public APIs and block iframe embedding via `X-Frame-Options`. **Recommended approach:**

- Open dictionary lookups in an **in-app browser tab** (Capacitor InAppBrowser plugin) on Android
- Open in a **new browser tab** on web
- Pre-construct deep-link URLs per service:
  - `https://www.wordreference.com/enfr/{word}`
  - `https://context.reverso.net/translation/english-french/{word}`
  - `https://www.linguee.com/english-french/search?query={word}`
  - `https://translate.google.com/?text={word}&sl=en&tl=fr`

This approach requires no API keys, is always free, and shows the full dictionary UI the user is familiar with.

#### BYOK LLM (AI Translation)

Direct `fetch()` calls to provider APIs using the user's own API key:

```typescript
// OpenAI
fetch('https://api.openai.com/v1/chat/completions', { headers: { Authorization: `Bearer ${key}` } })
// Anthropic
fetch('https://api.anthropic.com/v1/messages', { headers: { 'x-api-key': key } })
// Ollama (local)
fetch('http://localhost:11434/api/chat')
// Gemini
fetch('https://generativelanguage.googleapis.com/v1beta/models/...')
```

No SDK required. Provider-agnostic adapter pattern with a single `translate(text, config)` interface.

---

### Development Tools and Platforms

| Tool | Role |
|---|---|
| **Vitest** | Unit & integration tests (Vite-native, fast) |
| **React Testing Library** | Component tests |
| **Playwright** | E2E tests on web (Chromium, Firefox, WebKit) |
| **Maestro** | E2E tests on Android (YAML-based, readable) |
| **ESLint + Prettier** | Code quality & formatting |
| **GitHub Actions** | CI pipeline (lint → test → build → deploy) |
| **Android Studio** | Android build + emulator |

**Maestro** is the recommended E2E tool for Android: native React Native support, Capacitor/Cordova support, YAML syntax accessible to non-developers, integrates with GitHub Actions. Expo EAS Maestro integration is documented for CI.

_Source: [Maestro React Native support](https://docs.maestro.dev/platform-support/react-native), [Expo + Maestro E2E](https://docs.expo.dev/eas/workflows/examples/e2e-tests/)_

---

## Recommended Final Stack

```
┌─────────────────────────────────────────────────────────────┐
│                    LingQ Open — Tech Stack                  │
├─────────────────────────────────────────────────────────────┤
│  CORE                                                       │
│  React 19 + Vite 6 + TypeScript 5                          │
│  Capacitor 6 (Web → Android bridge)                        │
│  React Router v7 (routing)                                  │
│  Zustand (state management)                                 │
│                                                             │
│  UI                                                         │
│  Tailwind CSS v4 + shadcn/ui                               │
│                                                             │
│  READING                                                    │
│  foliate-js (EPUB)                                         │
│  PDF.js / pdfjs-dist (PDF)                                 │
│  Custom word-span renderer (React components)              │
│                                                             │
│  STORAGE (vault-based)                                      │
│  SQLite: sql.js (web) + @capacitor-community/sqlite (Android)│
│  ORM: Drizzle ORM (TypeScript, migrations)                 │
│  File access: File System Access API (web) +               │
│               @capacitor/filesystem (Android)              │
│  Handle persistence: Dexie.js (IndexedDB, web only)        │
│                                                             │
│  SECURITY                                                   │
│  @aparajita/capacitor-secure-storage (Keystore/Keychain)   │
│                                                             │
│  AI / DICTIONARY                                            │
│  BYOK: direct fetch() to OpenAI/Anthropic/Gemini/Ollama    │
│  Dictionary: deep-link URLs (WordReference, Reverso, etc.) │
│                                                             │
│  TTS                                                        │
│  Web: window.speechSynthesis                               │
│  Android: @capacitor-community/text-to-speech              │
│                                                             │
│  TESTING                                                    │
│  Vitest + React Testing Library (unit/integration)         │
│  Playwright (E2E web)                                       │
│  Maestro (E2E Android)                                     │
│                                                             │
│  CI/CD                                                      │
│  GitHub Actions (lint → test → build)                      │
│  Android Studio / Capacitor CLI (Android build)            │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Trade-offs and Risks

### Risk 1 — Android File System Restrictions (Android 11+)
**Problem:** `@capacitor/filesystem` on Android 11+ cannot access arbitrary user folders — only the app's own Documents directory.
**Mitigation:** Use a system file picker to select the vault folder, then use content:// URIs for subsequent access. This is a known pattern in the Capacitor community. Alternatively, default the vault to the app's Documents directory (auto-created, no picker needed) with an option to use Android's "sync folder" (e.g., by pointing to a folder monitored by Syncthing/FolderSync).

### Risk 2 — Dictionary Iframe Blocking
**Problem:** Dictionary sites (WordReference, Reverso, Linguee) block iframe embedding.
**Mitigation:** Open in Capacitor InAppBrowser (Android) or new tab (web). The translation panel shows tab buttons; clicking a dictionary opens it externally. This is a safe, zero-maintenance approach.

### Risk 3 — PDF Word Extraction
**Problem:** PDF text extraction does not natively provide word boundaries with click positions.
**Mitigation:** Use PDF.js's text content layer to extract text, then overlay transparent word-level `<span>` elements using the extracted character positions. This is achievable but requires careful coordinate mapping. Alternatively, for MVP, convert PDF pages to images + extracted text displayed separately (simpler but less fluid).

### Risk 4 — Safari / Firefox File System API
**Problem:** File System Access API `showDirectoryPicker()` is not available in all browsers (Safari support is limited).
**Mitigation:** Use OPFS as a fallback (fully supported in Safari 16.4+). In OPFS mode, the vault is sandboxed but the user can import/export files manually. Full vault-to-folder sync remains a Chromium/Edge exclusive for now.

---

## Sources

- [Capacitor vs React Native — nextnative.dev](https://nextnative.dev/blog/capacitor-vs-react-native)
- [Capgo — Comparing React Native vs Capacitor](https://capgo.app/blog/comparing-react-native-vs-capacitor/)
- [foliate-js — GitHub](https://github.com/johnfactotum/foliate-js)
- [epub.js — GitHub](https://github.com/futurepress/epub.js)
- [Drizzle ORM — Expo SQLite](https://orm.drizzle.team/docs/connect-expo-sqlite)
- [Local-first with Expo SQLite and Drizzle](https://israataha.com/blog/build-local-first-app-with-expo-sqlite-and-drizzle/)
- [Expo Blog — Modern SQLite for React Native](https://expo.dev/blog/modern-sqlite-for-react-native-apps)
- [Offline-first frontend apps 2025 — LogRocket](https://blog.logrocket.com/offline-first-frontend-apps-2025-indexeddb-sqlite/)
- [MDN — File System API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API)
- [MDN — Origin Private File System](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system)
- [OPFS on Android — chromestatus.com](https://chromestatus.com/feature/5079634203377664)
- [Capacitor Filesystem API](https://capacitorjs.com/docs/v5/apis/filesystem)
- [Capawesome File Picker](https://capawesome.io/plugins/file-picker/)
- [capacitor-secure-storage — GitHub](https://github.com/aparajita/capacitor-secure-storage)
- [Maestro — React Native support](https://docs.maestro.dev/platform-support/react-native)
- [Expo + Maestro E2E docs](https://docs.expo.dev/eas/workflows/examples/e2e-tests/)
- [Using Capacitor with React — capacitorjs.com](https://capacitorjs.com/solution/react)
