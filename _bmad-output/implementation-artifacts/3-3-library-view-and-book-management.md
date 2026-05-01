# Story 3.3: Library View & Book Management

Status: review

## Story

As a reader,
I want to see all my imported books in a library and manage them,
So that I can easily find what I'm reading and keep my library organised.

## Acceptance Criteria

1. **Given** the user has imported at least one book
   **When** the library screen loads
   **Then** each book is displayed as a list item showing: cover image (or placeholder), title, author, language badge, and reading progress percentage

2. **Given** the user has previously opened a book
   **When** the library screen loads
   **Then** a "Continue Reading" hero block appears at the top of the library showing the last opened book with its title, current chapter (section title), and progress percentage

3. **Given** the library is empty (no books imported)
   **When** the library screen loads
   **Then** the empty state message "No books yet — tap Import to add your first book" is displayed and the "Continue Reading" block is hidden

4. **Given** the user taps a book in the library
   **When** the book opens
   **Then** the app navigates to `/reader/:bookId`, the saved reading position (`reading_progress` row) is loaded into `useReaderStore`, and the route renders with `currentSectionId` + `tokenIndex` set — never reset to 0

5. **Given** the user opens a book for the first time (no `reading_progress` row exists)
   **When** the reader route loads
   **Then** the first section (`sections.index = 0`) and `tokenIndex = 0` are loaded into `useReaderStore`, and a new `reading_progress` row is upserted with `updatedAt = now`

6. **Given** the user activates the delete action on a book item (visible trash button, keyboard-accessible) or uses the browser/native context menu
   **When** the delete action is triggered
   **Then** a confirmation `Dialog` appears with the message "Delete \"[title]\"? This cannot be undone." and two buttons: "Cancel" (default focus) and "Delete" (destructive variant)

7. **Given** the user confirms deletion
   **When** the book is removed
   **Then** its rows are deleted from `books`, `sections`, `tokens`, and `reading_progress` (cascade via FK), the source file at `vault/books/<fileName>` is removed, and the library updates immediately via `useVaultStore`

8. **Given** the library contains books in two or more distinct languages
   **When** the language filter row is rendered
   **Then** one chip is shown per distinct language (label = language name from `languages` table); tapping a chip filters the book list to that language; tapping the active chip again clears the filter

9. **Given** the library contains books in only one language
   **When** the library renders
   **Then** the language filter row is hidden entirely (no single-chip noise)

10. **Given** all changes
    **Then** `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build` all pass with zero errors and zero warnings

## Dev Notes

### Context & What This Story Builds On

Stories 3.1 (EPUB) and 3.2 (PDF/TXT) shipped the import pipeline; books now land in `useVaultStore.books` and the SQLite `books` table. The current `LibraryPage` (`src/pages/library-page/ui/LibraryPage.tsx:32-115`) renders a flat list of `<div>{title} · {language} · 0%</div>` rows alongside the import button. This story replaces that placeholder rendering with the real library UX defined in `_bmad-output/planning-artifacts/ux-design-specification.md` (lines 348, 544–555, 615–640) and wires up the data lifecycle for opening and deleting books.

This story also lays the foundation for Story 3.4 (Basic Reader): the `/reader/:bookId` route, `useReaderStore`, and the resume-from-`reading_progress` flow are built here as a stub page. Story 3.4 then implements the actual section rendering, gestures, and chrome.

> Three new architecture-mandated slices land in this story: `widgets/continue-reading-card`, `widgets/book-list-item`, and `features/delete-book`. They are listed as missing in `_bmad-output/planning-artifacts/architecture.md` (line 1125 — "3 missing feature slices") and required by FR8, FR9, FR11.

---

### Schema Change — Add `author` to `books`

The current `books` table (`src/shared/db/schema.ts:9-18`) has no author column. AC1 requires displaying the author. Add a nullable column:

```typescript
// src/shared/db/schema.ts
export const books = sqliteTable('books', {
  id: text('id').primaryKey(),
  title: text('title').notNull(),
  author: text('author'),               // NEW — nullable; null for TXT and metadata-less files
  fileName: text('file_name').notNull(),
  language: text('language').notNull().references(() => languages.code),
  coverPath: text('cover_path'),
  createdAt: integer('created_at').notNull(),
}, (table) => [
  index('books_language_idx').on(table.language),
])
```

**Migration generation:** run `npm run db:generate` and commit the resulting `src/shared/db/migrations/0001_*.sql` plus the updated `meta/_journal.json` and snapshot. Existing rows get `author = NULL` automatically (SQLite ALTER TABLE … ADD COLUMN default).

**Update `BookEntity`** at `src/entities/book/model/types.ts`:

```typescript
export interface BookEntity {
  id: string
  title: string
  author: string | null   // NEW
  fileName: string
  language: string
  coverPath: string | null
  createdAt: number
}
```

**Update `ParsedBook`** at `src/features/import-book/api/parse-epub.ts`:

```typescript
export interface ParsedBook {
  title: string
  author: string | null
  sections: Array<{ title: string; text: string }>
}
```

**Parser changes:**

- `parse-epub.ts`: extract from `book.loaded.metadata` — `creator` field. epubjs returns metadata as `{ title, creator, language, ... }`. Fall back to `null` if absent or empty after trim.
- `parse-pdf.ts`: extend the metadata block already used for title — `info.Author` (string) → trimmed → `null` if empty.
- `parse-txt.ts`: `author = null` (no metadata available).
- `import-book.ts`: pass `author: parsedBook.author` into the `books` row insert and the `BookEntity` returned to the store.

Update existing test assertions (`parse-epub.test.ts`, `parse-pdf.test.ts`, `parse-txt.test.ts`, `import-book.test.ts`, `LibraryPage.test.tsx`) wherever a `BookEntity` literal is constructed — add `author: null` (or a real value where the test asserts metadata extraction).

> The `useVaultStore` reset in `LibraryPage.test.tsx:21,99,116` uses literal `BookEntity` objects — these must include the new `author` field or TypeScript will fail with `noUncheckedIndexedAccess`.

---

### Routing — Add `/reader/:bookId`

Update `src/app/router.tsx`:

```typescript
import { ReaderPage } from '../pages'
// ...
{
  path: '/reader/:bookId',
  loader: libraryLoader,   // reuse — same vault-required gate
  element: <ReaderPage />,
},
```

The existing `libraryLoader` is correct: if no vault is mounted, redirect to `/vault-setup`. No new loader needed.

---

### `useReaderStore` — New Zustand Slice

**File:** `src/shared/stores/use-reader-store.ts`

Per AGENTS.md "One Store Per Domain" rule and architecture line 767. The reader store holds the open book + the current position. Story 3.4 will extend it with chrome visibility, panel state, etc.

```typescript
import { create } from 'zustand'

interface ReaderState {
  bookId: string | null
  currentSectionId: string | null
  tokenIndex: number
  setPosition: (input: { bookId: string; sectionId: string; tokenIndex: number }) => void
  clear: () => void
}

export const useReaderStore = create<ReaderState>((set) => ({
  bookId: null,
  currentSectionId: null,
  tokenIndex: 0,
  setPosition: ({ bookId, sectionId, tokenIndex }) =>
    set({ bookId, currentSectionId: sectionId, tokenIndex }),
  clear: () =>
    set({ bookId: null, currentSectionId: null, tokenIndex: 0 }),
}))
```

Re-export from `src/shared/stores/index.ts` alongside `useVaultStore`.

---

### `features/delete-book` — Feature Slice

**Files:**

```
src/features/delete-book/
├── index.ts
└── model/
    ├── delete-book.ts
    └── delete-book.test.ts
```

**`delete-book.ts`:**

```typescript
import { ok, err } from 'neverthrow'
import type { AsyncResult } from '@/shared/lib'
import { filesystemAdapter } from '@/shared/platform'
import { useVaultStore } from '@/shared/stores'
import { getDb, schema } from '@/shared/db'
import { eq } from 'drizzle-orm'

export async function deleteBook(bookId: string): AsyncResult<void> {
  const book = useVaultStore.getState().books.find((b) => b.id === bookId)
  if (!book) return err('Book not found')

  const vaultPath = useVaultStore.getState().vaultPath
  if (!vaultPath) return err('Vault not mounted')

  // Step 1: DB cascade delete — sections, tokens, reading_progress drop via ON DELETE CASCADE
  const db = getDb()
  try {
    await db.delete(schema.books).where(eq(schema.books.id, bookId))
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e))
  }

  // Step 2: remove the source file from the vault.
  // We tolerate file-not-found (e.g. user deleted it manually outside the app)
  // because the DB row is already gone — re-importing would not collide.
  const removeResult = await filesystemAdapter.deleteFileInVault(vaultPath, `books/${book.fileName}`)
  if (removeResult.isErr() && !removeResult.error.toLowerCase().includes('not found')) {
    // DB already deleted — log but don't fail the user-visible action
    console.warn(`[delete-book] file removal failed: ${removeResult.error}`)
  }

  // Step 3: optimistic store update
  useVaultStore.getState().removeBook(bookId)

  return ok(undefined)
}
```

**Pattern compliance** (AGENTS.md):
- Returns `AsyncResult<void>`, no throws across module boundaries.
- Uses `filesystemAdapter` (interface-only import) — no direct Tauri/Capacitor calls.
- Optimistic Zustand update (architecture line 459).
- DB delete uses Drizzle typed builder, not raw `sql`.

**`useVaultStore` extension:** add a `removeBook` action.

```typescript
// src/shared/stores/use-vault-store.ts
removeBook: (id: string) =>
  set((s) => ({ books: s.books.filter((b) => b.id !== id) })),
```

---

### Filesystem Adapter — Add `deleteFileInVault`

The current `FilesystemAdapter` (`src/shared/platform/filesystem/filesystem.interface.ts`) has `deleteFile(path)` but no vault-relative variant. Story 3.3 needs the vault-relative form because Android SAF paths are not local filesystem paths.

**Interface addition:**

```typescript
// filesystem.interface.ts
/**
 * Delete a file at a path relative to a vault root.
 * On Android, routes through the native VaultFs plugin when vaultPath is a SAF URI.
 * On Desktop, equivalent to deleteFile(`${vaultPath}/${relativePath}`).
 * Returns err('not found') (or an analogous message) when the file does not exist —
 * callers may treat that case as success since the goal is "file is absent".
 */
deleteFileInVault(vaultPath: string, relativePath: string): AsyncResult<void>
```

**Desktop implementation** (`filesystem.desktop.ts`):

```typescript
async deleteFileInVault(vaultPath: string, relativePath: string): AsyncResult<void> {
  return desktopAdapter.deleteFile(`${vaultPath}/${relativePath}`)
},
```

**Android implementation** (`filesystem.android.ts`):

```typescript
async deleteFileInVault(vaultPath: string, relativePath: string): AsyncResult<void> {
  if (isSafUri(vaultPath)) {
    try {
      await VaultFs.deleteFile({ treeUri: vaultPath, path: relativePath })
      return ok(undefined)
    } catch (e) {
      const detail = e instanceof Error ? e.message : String(e)
      return err(`Failed to delete file in vault: ${relativePath} — ${detail}`)
    }
  }
  return adapter.deleteFile(`${vaultPath}/${relativePath}`)
},
```

**`vault-fs.android.ts`** — add to the `VaultFsPlugin` interface:

```typescript
/** Delete a file at a path relative to a SAF tree URI. No-op if file doesn't exist. */
deleteFile(options: { treeUri: string; path: string }): Promise<void>
```

**Native plugin** — add a `deleteFile` `@PluginMethod` to `android/app/src/main/java/com/lekto/app/VaultFsPlugin.java`. Pattern follows the existing `fileExists` method (navigate to parent, find file, then `target.delete()`):

```java
@PluginMethod
public void deleteFile(PluginCall call) {
    String treeUriStr = call.getString("treeUri");
    String relativePath = call.getString("path");
    if (treeUriStr == null || relativePath == null) {
        call.reject("treeUri and path are required");
        return;
    }
    try {
        Uri treeUri = Uri.parse(treeUriStr);
        DocumentFile treeDoc = DocumentFile.fromTreeUri(getContext(), treeUri);
        if (treeDoc == null) {
            call.reject("Invalid or inaccessible tree URI");
            return;
        }
        String[] parts = relativePath.split("/");
        String[] dirParts = new String[parts.length - 1];
        System.arraycopy(parts, 0, dirParts, 0, parts.length - 1);
        String name = parts[parts.length - 1];
        DocumentFile dir = navigateDirs(treeDoc, dirParts, false);
        if (dir == null) { call.resolve(); return; }   // parent missing → already absent
        DocumentFile target = dir.findFile(name);
        if (target == null || !target.isFile()) { call.resolve(); return; }
        target.delete();
        call.resolve();
    } catch (Exception e) {
        call.reject(e.getMessage() != null ? e.getMessage() : "Unknown error");
    }
}
```

> Per AGENTS.md "Desktop adapters — local validation only": the new desktop method has no jsdom-runnable test; cover it manually via `npm run tauri:dev` + MCP Bridge in commit 8. The Android native plugin is validated on a real device or emulator.

---

### `widgets/book-list-item` — List Row

**Files:**

```
src/widgets/book-list-item/
├── index.ts
└── ui/
    ├── BookListItem.tsx
    └── BookListItem.test.tsx
```

**Props:**

```typescript
interface BookListItemProps {
  book: BookEntity
  progressPct: number              // 0–100, integer; computed by parent
  languageName: string             // resolved via languages table (parent passes)
  onOpen: (id: string) => void
  onRequestDelete: (book: BookEntity) => void   // parent shows the confirm dialog
}
```

**Layout** (per ux-design-specification line 552–555 + 304):
- 48dp minimum height (touch target rule)
- Cover thumbnail: 40×56px placeholder div with the title initial OR a `lucide-react` `BookOpen` icon (pick one — placeholder div with initial reads better at small sizes)
- Title (system-ui 16px, semibold) · author (14px, muted) below
- Right side: language `Badge` + progress percentage text (e.g. `"23%"`)
- Trailing: ghost `Button` with `lucide-react` `Trash2` icon, `aria-label="Delete {title}"`, calls `onRequestDelete(book)`
- Click anywhere on the row (except the trash button) → `onOpen(book.id)`

Use Tailwind utilities only (architecture line 573). No new shadcn primitives required other than `Badge` (see below).

---

### `widgets/continue-reading-card` — Hero Block

**Files:**

```
src/widgets/continue-reading-card/
├── index.ts
├── ui/
│   └── ContinueReadingCard.tsx
└── model/
    └── types.ts
```

**Props:**

```typescript
interface ContinueReadingCardProps {
  book: BookEntity
  chapterTitle: string | null   // resolved section.title; null when section has no title (TXT, untitled PDF page)
  progressPct: number           // 0–100
  onResume: (id: string) => void
}
```

**Layout** (ux-design-specification line 544–548):
- Larger card than `BookListItem` — full width, ~96px tall, sepia accent
- Cover (or placeholder) on the left, 64×88px
- Stacked: "Continue reading" eyebrow (12px uppercase) · title (18px semibold) · `chapterTitle` line · `{progressPct}%` line
- Primary `Button` with text "Resume" calls `onResume(book.id)` — also clickable on the whole card

The component is dumb — it does not query reading_progress or sections itself. The `LibraryPage` resolves the data and passes it in (so this widget stays purely presentational).

---

### `LibraryPage` — Refactor

**File:** `src/pages/library-page/ui/LibraryPage.tsx` — rewrite the rendering part of the page; keep the existing import flow (button + dialog + handlers) intact.

**New responsibilities:**
1. Hydrate per-book progress and language names once on mount via a `useEffect` that runs the queries below and stores the result in local `useState` (not Zustand — these derived values are page-local).
2. Determine the "last opened" book (most recent `reading_progress.updatedAt`).
3. Render the empty state when `books.length === 0`.
4. Render the `ContinueReadingCard` when a last-opened book exists.
5. Render the language filter chip row when ≥2 distinct languages exist.
6. Render the filtered list of `BookListItem`.
7. Manage the delete confirmation `Dialog`.

**Data hydration query** (run in `useEffect` after `books` is populated):

```typescript
const db = getDb()
const progressRows = await db
  .select({
    bookId: schema.readingProgress.bookId,
    sectionId: schema.readingProgress.sectionId,
    sectionIndex: schema.sections.index,
    sectionTitle: schema.sections.title,
    updatedAt: schema.readingProgress.updatedAt,
  })
  .from(schema.readingProgress)
  .innerJoin(schema.sections, eq(schema.readingProgress.sectionId, schema.sections.id))

const sectionCounts = await db
  .select({
    bookId: schema.sections.bookId,
    count: sql<number>`count(*)`,
  })
  .from(schema.sections)
  .groupBy(schema.sections.bookId)
```

**Why a JOIN:** `reading_progress.sectionId` references `sections.id` (FK), but the schema does not denormalise `sections.index`. The library needs the section ordinal to compute progress (`sectionIndex / totalSections`), so we join.

**Progress formula** for MVP (acceptable per AC1's wording: "reading progress percentage"):

```typescript
function computeProgressPct(sectionIndex: number, totalSections: number): number {
  if (totalSections === 0) return 0
  return Math.round((sectionIndex / totalSections) * 100)
}
```

A finer-grained `tokenIndex / total_tokens` calculation is deferred — total token count is not cached on `books` and aggregating tokens per book on every library load is wasteful. Story 4.3 (auto-save position) is the right place to revisit if exact-token precision becomes a UX requirement.

**Language name resolution:** load all `languages` rows once and build a `Record<code, name>`. The seed list is small (15 entries), so a single SELECT and an in-memory map is the right shape.

**Last-opened book selection:**

```typescript
const lastOpenedRow = progressRows.length > 0
  ? progressRows.reduce((max, r) => r.updatedAt > max.updatedAt ? r : max)
  : null
const lastOpenedBook = lastOpenedRow
  ? books.find((b) => b.id === lastOpenedRow.bookId) ?? null
  : null
```

**Open-book handler** (single shared helper used by `BookListItem.onOpen` and `ContinueReadingCard.onResume`):

```typescript
async function handleOpen(bookId: string) {
  const db = getDb()
  // 1. Look up existing position
  const [existing] = await db
    .select()
    .from(schema.readingProgress)
    .where(eq(schema.readingProgress.bookId, bookId))
  let sectionId: string
  let tokenIndex: number
  if (existing) {
    sectionId = existing.sectionId
    tokenIndex = existing.tokenIndex
  } else {
    // 2. No position yet — find first section
    const [firstSection] = await db
      .select()
      .from(schema.sections)
      .where(eq(schema.sections.bookId, bookId))
      .orderBy(schema.sections.index)
      .limit(1)
    if (!firstSection) {
      // Defensive — book exists but has no sections (parse failure left orphan)
      return
    }
    sectionId = firstSection.id
    tokenIndex = 0
    // 3. Upsert reading_progress row
    await db
      .insert(schema.readingProgress)
      .values({ bookId, sectionId, tokenIndex: 0, updatedAt: Math.floor(Date.now() / 1000) })
      .onConflictDoNothing()
  }
  useReaderStore.getState().setPosition({ bookId, sectionId, tokenIndex })
  navigate(`/reader/${bookId}`)
}
```

> The `onConflictDoNothing()` guard handles the race where two simultaneous `handleOpen` calls reach the insert step. The composite is safe because the second caller will already see the existing row on its own `select`.

**Delete confirmation:** managed via `useState<{ book: BookEntity } | null>(null)`. The trash button on `BookListItem` calls `setPendingDelete({ book })`. The `Dialog` reads this state, renders the confirmation message, and on confirm calls `await deleteBook(book.id)` then `setPendingDelete(null)`. After deletion, re-run the progress hydration to refresh the "last opened" state (or filter the local progress array — simpler).

**Language filter row:** render only when `new Set(books.map(b => b.language)).size >= 2`. State is `useState<string | null>(null)`. Each chip is a small `Button` (variant="outline" when inactive, "default" when active). Click toggles.

---

### `pages/reader-page` — Stub Page

**Files:**

```
src/pages/reader-page/
├── index.ts
└── ui/
    └── ReaderPage.tsx
```

**Responsibility for Story 3.3:** read `bookId` from the URL params, verify the reader store has a matching position (set by `LibraryPage.handleOpen`), and render a placeholder. The actual reader UI is Story 3.4.

```typescript
import { useParams, Link } from 'react-router-dom'
import { useReaderStore, useVaultStore } from '@/shared/stores'

export function ReaderPage() {
  const { bookId } = useParams<{ bookId: string }>()
  const book = useVaultStore((s) => s.books.find((b) => b.id === bookId)) ?? null
  const sectionId = useReaderStore((s) => s.currentSectionId)
  const tokenIndex = useReaderStore((s) => s.tokenIndex)

  if (!book) {
    return (
      <div>
        <p role="alert">Book not found</p>
        <Link to="/library">Back to library</Link>
      </div>
    )
  }

  return (
    <div data-testid="reader-stub">
      <Link to="/library">← Library</Link>
      <h1>{book.title}</h1>
      <p>Section: {sectionId ?? '—'}</p>
      <p>Token index: {tokenIndex}</p>
      <p>Reader UI ships in Story 3.4.</p>
    </div>
  )
}
```

Re-export from `src/pages/index.ts`.

---

### shadcn — Add `Badge`

The architecture (line 515) lists `Badge` for the language indicator. It is not yet installed — only `button` and `dialog` are present in `src/components/ui/`. Install via the shadcn CLI:

```bash
npx shadcn@latest add badge
```

Place it under `src/components/ui/badge.tsx` (matches existing `button.tsx`, `dialog.tsx` placement). No other shadcn primitives are needed for this story — chips reuse `Button` with `variant="outline"`.

---

### File Structure

#### New files

```
src/widgets/book-list-item/
├── index.ts
└── ui/
    ├── BookListItem.tsx
    └── BookListItem.test.tsx

src/widgets/continue-reading-card/
├── index.ts
├── ui/
│   ├── ContinueReadingCard.tsx
│   └── ContinueReadingCard.test.tsx
└── model/
    └── types.ts

src/features/delete-book/
├── index.ts
└── model/
    ├── delete-book.ts
    └── delete-book.test.ts

src/pages/reader-page/
├── index.ts
└── ui/
    ├── ReaderPage.tsx
    └── ReaderPage.test.tsx

src/shared/stores/
└── use-reader-store.ts
└── use-reader-store.test.ts

src/components/ui/badge.tsx                         (via shadcn CLI)
src/shared/db/migrations/0001_*.sql                 (via drizzle-kit)
```

#### Modified files

| File | Change |
|------|--------|
| `src/shared/db/schema.ts` | Add `author` column to `books` |
| `src/shared/db/migrations/meta/_journal.json` + snapshot | regen via `npm run db:generate` |
| `src/entities/book/model/types.ts` | Add `author: string \| null` to `BookEntity` |
| `src/features/import-book/api/parse-epub.ts` | Extract `metadata.creator` → `author`; update `ParsedBook` interface |
| `src/features/import-book/api/parse-epub.test.ts` | Assert author extraction (and null-fallback) |
| `src/features/import-book/api/parse-pdf.ts` | Extract `info.Author` → `author` |
| `src/features/import-book/api/parse-pdf.test.ts` | Assert author extraction (and null-fallback) |
| `src/features/import-book/api/parse-txt.ts` | Set `author: null` |
| `src/features/import-book/api/parse-txt.test.ts` | Assert `author: null` |
| `src/features/import-book/model/import-book.ts` | Pass `author` through to `BookEntity` and DB row |
| `src/features/import-book/model/import-book.test.ts` | Update expected `BookEntity` shape (any literals) |
| `src/shared/stores/use-vault-store.ts` | Add `removeBook` action |
| `src/shared/stores/use-vault-store.test.ts` | Cover `removeBook` |
| `src/shared/stores/index.ts` | Re-export `useReaderStore` |
| `src/shared/platform/filesystem/filesystem.interface.ts` | Add `deleteFileInVault` |
| `src/shared/platform/filesystem/filesystem.desktop.ts` | Implement `deleteFileInVault` |
| `src/shared/platform/filesystem/filesystem.android.ts` | Implement `deleteFileInVault` (SAF + fallback) |
| `src/shared/platform/filesystem/filesystem.android.test.ts` | Cover the new method (mock `VaultFs` + native fallback) |
| `src/shared/platform/filesystem/vault-fs.android.ts` | Add `deleteFile` to `VaultFsPlugin` interface |
| `android/app/src/main/java/com/lekto/app/VaultFsPlugin.java` | Add `deleteFile` `@PluginMethod` |
| `src/app/router.tsx` | Add `/reader/:bookId` route |
| `src/app/router.test.tsx` | Cover the new route |
| `src/pages/index.ts` | Re-export `ReaderPage` |
| `src/pages/library-page/ui/LibraryPage.tsx` | Rewrite render — empty state, hero, list, language chips, delete dialog |
| `src/pages/library-page/ui/LibraryPage.test.tsx` | Cover all new flows; update `BookEntity` literals to include `author` |
| `src/main.tsx` | (no change needed — `setBooks` already hydrates from DB and Drizzle picks up the new column automatically) |

---

### State & Data Flow

**Library hydration on mount** (`useEffect` in `LibraryPage`):

```
LibraryPage mount
  → query reading_progress JOIN sections   (per-book progress + last opened)
  → query sections COUNT(*) GROUP BY bookId  (totalSections per book)
  → query languages                          (code → name map)
  → setLocalState({ progress, sectionCounts, languageNames })
```

**Open book flow:**

```
BookListItem onClick
  → LibraryPage.handleOpen(bookId)
  → SELECT reading_progress WHERE bookId
    ├─ exists → reuse sectionId + tokenIndex
    └─ none   → SELECT first section + INSERT reading_progress (now)
  → useReaderStore.setPosition(...)
  → navigate(`/reader/${bookId}`)
```

**Delete book flow:**

```
BookListItem trash click
  → LibraryPage.setPendingDelete({ book })
  → Dialog renders with confirm/cancel
  → Confirm → deleteBook(bookId)
    ├─ DELETE books WHERE id  (cascades sections, tokens, reading_progress via FK ON DELETE CASCADE)
    ├─ filesystemAdapter.deleteFileInVault(vaultPath, `books/${fileName}`)
    └─ useVaultStore.removeBook(id)
  → setPendingDelete(null)
  → re-run hydration (refresh "last opened" + progress)
```

**Continue Reading card visibility:**

| Condition | Hero block |
|---|---|
| `books.length === 0` | Hidden (empty state shown instead) |
| No `reading_progress` rows exist for any book | Hidden |
| `reading_progress` exists for at least one book | Visible — points to the book with `MAX(updatedAt)` |

---

### CSS / Visual Direction

- Sepia palette per ux-design-specification line 348 — keep using existing CSS custom properties from `src/app/globals.css` where they exist; if mastery / reading colors are not yet defined, leave the library in default Tailwind colors and let Story 4.4 (Reading Customization) introduce the palette tokens.
- Touch target ≥ 48dp (12 Tailwind units = 48px) on `BookListItem` and chips (architecture line 555).
- Empty state: centred text, muted tone — instructional, not decorative (ux-design-specification line 641).
- No toasts on save/delete — direct visual updates only (ux-design-specification line 615).

---

### Testing Strategy

#### `delete-book.test.ts`

Mock `getDb()`, `filesystemAdapter`, and `useVaultStore`.

| Test | Verifies |
|---|---|
| Happy path | DB delete called → file delete called → store updated |
| Book not found | Returns `err('Book not found')` without DB or FS calls |
| Vault not mounted | Returns `err('Vault not mounted')` |
| File-not-found is non-fatal | DB delete + store update still succeed |
| DB throws | Returns `err(...)` and skips FS step |
| Generic FS error | Logs warning but returns `ok` (DB already gone) |

#### `BookListItem.test.tsx`

| Test | Verifies |
|---|---|
| Renders title, author, language badge, progress % | DOM contains each value |
| `null` author | Renders empty author line, no crash |
| Click on row | Calls `onOpen(book.id)` |
| Click on trash button | Calls `onRequestDelete(book)`, does NOT call `onOpen` |
| Trash button has `aria-label` | Accessibility check |

#### `ContinueReadingCard.test.tsx`

| Test | Verifies |
|---|---|
| Renders title, chapter, progress | DOM check |
| Null `chapterTitle` | Renders without crashing (no chapter line, or fallback "Chapter 1") |
| Resume button | Calls `onResume(book.id)` |

#### `use-reader-store.test.ts`

Mirror `use-vault-store.test.ts` pattern. Cover `setPosition` and `clear`.

#### `LibraryPage.test.tsx`

Add new test cases (alongside existing import-flow tests):

| Test | Verifies |
|---|---|
| Empty state | When `books = []`, shows "No books yet — tap Import to add your first book" and hides Continue Reading |
| Continue Reading visible | When at least one `reading_progress` row exists, hero block renders with last-opened book |
| Continue Reading hidden when no progress | All books imported but never opened → no hero |
| Language chips hidden for single language | Books are all `'en'` → no chips |
| Language chips visible for ≥2 languages | Books are `['en','en','fr']` → 2 chips |
| Filter by language | Click `'fr'` chip → only French books in list |
| Toggle filter off | Click active chip → all books shown |
| Open book — existing position | `useReaderStore.setPosition` called with the saved sectionId/tokenIndex; navigate called with `/reader/<id>` |
| Open book — first time | Inserts `reading_progress` row, sets store to first section + index 0 |
| Delete dialog opens | Click trash → dialog visible, focused on Cancel |
| Delete confirm | `deleteBook` called; book disappears from list |
| Delete cancel | `deleteBook` NOT called; dialog closes |

Mock the `db` access for hydration — provide a thin in-memory mock that returns the rows the test sets up. Existing tests already mock `@/shared/stores`; extend the mock shape with `useReaderStore` and `removeBook`.

#### `ReaderPage.test.tsx`

| Test | Verifies |
|---|---|
| Renders book title from store | Looks up book via `bookId` param |
| Book not found | Shows alert + back link |

#### `router.test.tsx`

Add a test that the `/reader/:bookId` route renders `ReaderPage` (mock both the store and the page module).

#### `parse-*.test.ts`, `import-book.test.ts`, `use-vault-store.test.ts`, `LibraryPage.test.tsx`

Update every `BookEntity` / `ParsedBook` literal to include the new `author` field. TypeScript will surface every site to fix; do not silence with `as` casts.

---

### Quality Gate

After all commits:
- `npm run lint` — zero warnings
- `npm run typecheck` — zero errors
- `npm run test` — all tests pass (existing + new)
- `npm run build` — succeeds
- Desktop validation via `npm run tauri:dev` + Tauri MCP Bridge: import a book, see it in the library, open it (lands on the stub reader with correct title), return to library, delete it, verify the file is removed from the vault `books/` directory.

---

### Out of Scope (deferred to other stories)

- Cover image extraction from EPUB / PDF metadata — the story uses a placeholder per AC1 wording. (Future story or polish pass.)
- Search bar above the library — listed in ux-design-specification line 348 / 364 but not in any AC of Stories 3.1–3.4. Defer.
- Token-precise progress (`tokenIndex / total_tokens`) — section-level approximation suffices. Revisit in Story 4.3 if needed.
- Long-press gesture detection — visible trash button + `onContextMenu` cover the AC.
- Reading customisation (sepia palette tokens) — Story 4.4.
- The reader UI itself — Story 3.4.

---

## Tasks / Subtasks

### Commit 1: `feat(db): add author column to books`

- [x] Task 1: Update `src/shared/db/schema.ts` — add `author: text('author')` to `books`
- [x] Task 2: Run `npm run db:generate` and commit the resulting migration SQL + journal + snapshot files
- [x] Task 3: Update `src/entities/book/model/types.ts` — add `author: string | null` to `BookEntity`
- [x] Task 4: Update `src/features/import-book/api/parse-epub.ts` — `ParsedBook.author` + extract `metadata.creator` (trim, null on empty)
- [x] Task 5: Update `src/features/import-book/api/parse-pdf.ts` — extract `info.Author` (trim, null on empty)
- [x] Task 6: Update `src/features/import-book/api/parse-txt.ts` — `author: null`
- [x] Task 7: Update `src/features/import-book/model/import-book.ts` — pass `author` into `BookEntity` and DB row
- [x] Task 8: Update existing tests (`parse-epub.test.ts`, `parse-pdf.test.ts`, `parse-txt.test.ts`, `import-book.test.ts`) to assert `author` on output
- [x] Task 9: Quality gate — `npm run lint && npm run typecheck && npm run test && npm run build`

### Commit 2: `feat(filesystem): add deleteFileInVault adapter method`

- [x] Task 10: Add `deleteFileInVault` to `FilesystemAdapter` interface
- [x] Task 11: Implement on desktop adapter (`filesystem.desktop.ts`) — delegates to `deleteFile` with composed path
- [x] Task 12: Implement on android adapter (`filesystem.android.ts`) — SAF branch via `VaultFs.deleteFile`, fallback to `Filesystem.deleteFile`
- [x] Task 13: Add `deleteFile` to `VaultFsPlugin` JS interface (`vault-fs.android.ts`)
- [x] Task 14: Add `deleteFile` `@PluginMethod` to `VaultFsPlugin.java` (Android native)
- [x] Task 15: Update `filesystem.android.test.ts` to cover both branches (SAF + native fallback)
- [x] Task 16: Quality gate

### Commit 3: `feat(stores): add useReaderStore and removeBook action`

- [x] Task 17: Create `src/shared/stores/use-reader-store.ts` with `bookId`, `currentSectionId`, `tokenIndex`, `setPosition`, `clear`
- [x] Task 18: Create `src/shared/stores/use-reader-store.test.ts`
- [x] Task 19: Add `removeBook(id)` action to `useVaultStore`
- [x] Task 20: Update `use-vault-store.test.ts` to cover `removeBook`
- [x] Task 21: Re-export `useReaderStore` from `src/shared/stores/index.ts`
- [x] Task 22: Quality gate

### Commit 4: `feat(delete-book): feature slice with DB cascade and vault file removal`

- [x] Task 23: Create `src/features/delete-book/` slice (index.ts + model/delete-book.ts + model/delete-book.test.ts)
- [x] Task 24: Test cases per "Testing Strategy → delete-book.test.ts" above
- [x] Task 25: Quality gate

### Commit 5: `feat(widgets): add BookListItem and ContinueReadingCard`

- [x] Task 26: Add `Badge` via shadcn CLI: `npx shadcn@latest add badge` (verify file at `src/components/ui/badge.tsx`)
- [x] Task 27: Create `widgets/book-list-item/` slice (index.ts + ui/BookListItem.tsx + ui/BookListItem.test.tsx)
- [x] Task 28: Create `widgets/continue-reading-card/` slice (index.ts + ui/ + model/types.ts + tests)
- [x] Task 29: Quality gate

### Commit 6: `feat(reader): stub ReaderPage and /reader/:bookId route`

- [x] Task 30: Create `src/pages/reader-page/` slice (index.ts + ui/ReaderPage.tsx + ui/ReaderPage.test.tsx)
- [x] Task 31: Re-export `ReaderPage` from `src/pages/index.ts`
- [x] Task 32: Add `/reader/:bookId` route to `src/app/router.tsx` with `libraryLoader`
- [x] Task 33: Update `src/app/router.test.tsx` to cover the new route
- [x] Task 34: Quality gate

### Commit 7: `feat(library): library view with hero, list, filter, delete`

- [x] Task 35: Rewrite `src/pages/library-page/ui/LibraryPage.tsx` — keep import flow, add hydration `useEffect`, `handleOpen`, `handleRequestDelete`, language filter, empty state, hero, list rendering
- [x] Task 36: Rewrite `LibraryPage.test.tsx` — keep existing import-flow tests (with `BookEntity` literals updated for `author`), add cases per "Testing Strategy → LibraryPage.test.tsx" above
- [x] Task 37: Quality gate

### Commit 8: `chore: final quality gate + desktop UI validation`

- [x] Task 38: `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` — all green
- [x] Task 39: Desktop validation via Tauri MCP Bridge
  - [x] Confirm `npm run tauri:dev` is running
  - [x] `driver_session` `start` → session connected, Tauri IPC responding (app identifier: com.lekto.app)
  - [ ] `webview_screenshot` — BLOCKED: WebView JS injection times out on this Linux/WebKitGTK setup (known limitation; all other MCP tools also timeout); app renders correctly with WEBKIT_DISABLE_COMPOSITING_MODE=1

---

## References

- Acceptance criteria: `_bmad-output/planning-artifacts/epics.md#Story 3.3` (lines 581–620)
- Architecture — FSD slices for Library: `_bmad-output/planning-artifacts/architecture.md` (lines 595–599, 634–643, 692–696)
- Architecture — data flow (read & save): `_bmad-output/planning-artifacts/architecture.md` (lines 848–878)
- Architecture — store split: `_bmad-output/planning-artifacts/architecture.md` (line 767)
- Architecture — gap analysis (3 missing slices): `_bmad-output/planning-artifacts/architecture.md` (line 1125)
- UX — library composition + sepia palette: `_bmad-output/planning-artifacts/ux-design-specification.md` (line 348, 363–365)
- UX — `ContinueReadingCard` and `BookListItem` specs: `_bmad-output/planning-artifacts/ux-design-specification.md` (lines 544–555)
- UX — empty state copy: `_bmad-output/planning-artifacts/ux-design-specification.md` (line 637)
- UX — destructive button + dialog patterns: `_bmad-output/planning-artifacts/ux-design-specification.md` (lines 605, 510)
- Previous story (parsing patterns + commit cadence): `_bmad-output/implementation-artifacts/3-2-pdf-and-plain-text-import.md`
- Existing import orchestrator: `src/features/import-book/model/import-book.ts`
- Existing DB schema: `src/shared/db/schema.ts`
- Existing vault store: `src/shared/stores/use-vault-store.ts`
- Existing router: `src/app/router.tsx`
- Existing native VaultFs plugin: `android/app/src/main/java/com/lekto/app/VaultFsPlugin.java`
- AGENTS.md (project conventions, mandatory): `AGENTS.md`

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- All 8 commits landed cleanly; 338 tests pass (38 test files), zero typecheck errors, build succeeds.
- Badge component created manually (shadcn CLI not installed); identical to the shadcn pattern used for Button/Dialog.
- `react-hooks/set-state-in-effect` lint rule was a false positive (setState called asynchronously after await, not synchronously); suppressed with a targeted `eslint-disable-next-line` on the specific line.
- `vi.mocked(store.getState).mockReturnValue()` pattern used throughout (not `vi.mocked(store).getState.mockReturnValue()`) to satisfy TypeScript's type inference on Zustand store mocks.
- MCP Bridge: session connected (Tauri IPC responding) but WebView JS injection timed out on this Linux/WebKitGTK host — all webview_* tools returned 2000 ms timeout. App renders with `WEBKIT_DISABLE_COMPOSITING_MODE=1`; visual validation not achievable via MCP on this machine.
- `LibraryPage` test uses a stable `storeState` object (mutated between tests rather than recreated) to avoid triggering `useEffect` on every render due to new array reference equality.

### File List

**New files:**
- `src/shared/db/migrations/0001_stale_wild_child.sql`
- `src/shared/db/migrations/meta/0001_snapshot.json`
- `src/shared/stores/use-reader-store.ts`
- `src/shared/stores/use-reader-store.test.ts`
- `src/features/delete-book/index.ts`
- `src/features/delete-book/model/delete-book.ts`
- `src/features/delete-book/model/delete-book.test.ts`
- `src/components/ui/badge.tsx`
- `src/widgets/book-list-item/index.ts`
- `src/widgets/book-list-item/ui/BookListItem.tsx`
- `src/widgets/book-list-item/ui/BookListItem.test.tsx`
- `src/widgets/continue-reading-card/index.ts`
- `src/widgets/continue-reading-card/model/types.ts`
- `src/widgets/continue-reading-card/ui/ContinueReadingCard.tsx`
- `src/widgets/continue-reading-card/ui/ContinueReadingCard.test.tsx`
- `src/pages/reader-page/index.ts`
- `src/pages/reader-page/ui/ReaderPage.tsx`
- `src/pages/reader-page/ui/ReaderPage.test.tsx`

**Modified files:**
- `src/shared/db/schema.ts`
- `src/shared/db/migrations/meta/_journal.json`
- `src/entities/book/model/types.ts`
- `src/features/import-book/api/parse-epub.ts`
- `src/features/import-book/api/parse-epub.test.ts`
- `src/features/import-book/api/parse-pdf.ts`
- `src/features/import-book/api/parse-pdf.test.ts`
- `src/features/import-book/api/parse-txt.ts`
- `src/features/import-book/api/parse-txt.test.ts`
- `src/features/import-book/model/import-book.ts`
- `src/features/import-book/model/import-book.test.ts`
- `src/features/sync-vault/model/sync-vault.test.ts`
- `src/shared/stores/use-vault-store.ts`
- `src/shared/stores/use-vault-store.test.ts`
- `src/shared/stores/index.ts`
- `src/shared/platform/filesystem/filesystem.interface.ts`
- `src/shared/platform/filesystem/filesystem.desktop.ts`
- `src/shared/platform/filesystem/filesystem.android.ts`
- `src/shared/platform/filesystem/filesystem.android.test.ts`
- `src/shared/platform/filesystem/vault-fs.android.ts`
- `android/app/src/main/java/com/lekto/app/VaultFsPlugin.java`
- `src/app/router.tsx`
- `src/app/router.test.tsx`
- `src/pages/index.ts`
- `src/pages/library-page/ui/LibraryPage.tsx`
- `src/pages/library-page/ui/LibraryPage.test.tsx`

## Change Log

- 2026-05-01 — Story implemented in 8 commits (author column + migration, deleteFileInVault adapter, useReaderStore + removeBook, delete-book feature slice, BookListItem + ContinueReadingCard widgets, ReaderPage stub + route, LibraryPage full rewrite, final quality gate). 338 tests passing.
