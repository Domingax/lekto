# Story 3.4: Basic Reader & In-Book Navigation

Status: in-progress

## Story

As a reader,
I want to read my imported books and navigate between pages and chapters,
So that I can read comfortably before vocabulary features are available.

## Acceptance Criteria

1. **Given** a book is opened in the reader
   **When** the reader renders the current section
   **Then** the tokenized words are displayed as plain text (no mastery coloring) with Georgia 18px, 1.7 line height, medium margins, light theme

2. **Given** the reader is open
   **When** the user swipes left
   **Then** the next page (section) is displayed with an animation completing within 100ms

3. **Given** the reader is open
   **When** the user swipes right
   **Then** the previous page (section) is displayed with an animation completing within 100ms

4. **Given** the reader is open
   **When** the user taps the navigation arrow controls or page indicator
   **Then** the same next/previous section behaviour is triggered as with swipe

5. **Given** the reader is on the last section of a book
   **When** the user swipes left
   **Then** no navigation occurs (end-of-book guard); the last section remains visible

6. **Given** the reader is on the first section of a book
   **When** the user swipes right
   **Then** no navigation occurs (start-of-book guard); the first section remains visible

7. **Given** the reader is open
   **When** the user taps the chapter navigation control (chapter name in bottom bar)
   **Then** a chapter list is displayed and tapping any chapter jumps directly to its first section

8. **Given** the user is reading and closes the app (or navigates away)
   **When** the app is reopened and the book is opened again
   **Then** the reader resumes at the exact section and token position where the user left off

9. **Given** the reader chrome is visible
   **When** the user taps outside the text area (on the margin/padding area)
   **Then** the top bar (back arrow + book title) and bottom bar (chapter name + page indicator) toggle visibility — text remains readable

10. **Given** all changes
    **Then** `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build` all pass with zero errors and zero warnings

---

## Dev Notes

### Context & What This Story Builds On

Story 3.3 shipped the stub infrastructure that this story fills in:

- **`ReaderPage` stub** — `src/pages/reader-page/ui/ReaderPage.tsx` renders book title + section/tokenIndex from `useReaderStore`. Replace this stub entirely in Commit 4.
- **`useReaderStore`** — `src/shared/stores/use-reader-store.ts` has `bookId`, `currentSectionId`, `tokenIndex`, `setPosition(...)`, `clear()`. Extend in Commit 2.
- **Route `/reader/:bookId`** — already registered in `src/app/router.tsx` with `libraryLoader`. No router change needed.
- **`LibraryPage.handleOpen(bookId)`** — already calls `useReaderStore.setPosition({ bookId, sectionId, tokenIndex })` before `navigate('/reader/:bookId')`. The reader receives a pre-set position.
- **`TokenEntity`** — already defined at `src/entities/token/model/types.ts` (`id`, `sectionId`, `index`, `type: 'word'|'punctuation'|'whitespace'`, `text`, `wordKey: string|null`).

`@tanstack/react-virtual` v3.13.22 is already installed — no new deps needed.

---

### Page Model (MVP Decision)

**One section = one page.** Swiping navigates between sections (chapters). If a section is long, the user scrolls vertically within it. This is the simplest correct model:

- The AC refers to "next chapter" as the cross-boundary case. EPUB sections are already chapter-level units.
- `tokenIndex` in `reading_progress` records the first *visible* token for restore. On section navigation, `tokenIndex = 0`.
- A sub-section token-level scroll position can be tracked via the scroll container's `scrollTop` mapped to the first visible token. Keep it simple: record `tokenIndex = 0` on section change; refine if users request mid-section restore in a later story.

The bottom bar shows: `"[section.title ?? 'Chapter N'] · [sectionIndex+1] / [totalSections]"`

---

### SectionEntity — New Entity Type

`entities/section/` does **not** currently exist (only `entities/book/` and `entities/token/` are present). Create it:

```
src/entities/section/
├── index.ts
└── model/
    └── types.ts
```

**`types.ts`:**

```typescript
export interface SectionEntity {
  id: string
  bookId: string
  index: number
  title: string | null
}
```

**`index.ts`:**

```typescript
export type { SectionEntity } from './model/types'
```

Add re-export to `src/entities/index.ts`:

```typescript
export type { SectionEntity } from './section'
```

---

### `useReaderStore` — Extended State

**File:** `src/shared/stores/use-reader-store.ts` — extend the existing store (do not replace).

Add three new state fields and three new actions:

```typescript
import { create } from 'zustand'
import type { SectionEntity } from '@/entities/section'
import type { TokenEntity } from '@/entities/token'

interface ReaderState {
  // Existing (from 3.3)
  bookId: string | null
  currentSectionId: string | null
  tokenIndex: number
  setPosition: (input: { bookId: string; sectionId: string; tokenIndex: number }) => void
  clear: () => void
  // New in 3.4
  sections: SectionEntity[]
  tokens: TokenEntity[]
  isChromeVisible: boolean
  setSections: (sections: SectionEntity[]) => void
  setTokens: (tokens: TokenEntity[]) => void
  toggleChrome: () => void
}

export const useReaderStore = create<ReaderState>((set) => ({
  bookId: null,
  currentSectionId: null,
  tokenIndex: 0,
  sections: [],
  tokens: [],
  isChromeVisible: true,
  setPosition: ({ bookId, sectionId, tokenIndex }) =>
    set({ bookId, currentSectionId: sectionId, tokenIndex }),
  setSections: (sections) => set({ sections }),
  setTokens: (tokens) => set({ tokens }),
  toggleChrome: () => set((s) => ({ isChromeVisible: !s.isChromeVisible })),
  clear: () =>
    set({ bookId: null, currentSectionId: null, tokenIndex: 0, sections: [], tokens: [], isChromeVisible: true }),
}))
```

Update `use-reader-store.test.ts` to cover `setSections`, `setTokens`, `toggleChrome`, and the `clear` extension (confirm it resets the new fields too).

---

### `widgets/reader-view/` — New Slice

Architecture mandates this widget handles: tokenized text rendering, gestures, chrome visibility, and section navigation (FR10, FR12–17 from architecture.md line 882–903).

```
src/widgets/reader-view/
├── index.ts
├── ui/
│   ├── ReaderView.tsx
│   └── WordToken.tsx
└── model/
    ├── use-reader-view.ts
    ├── use-reader-view.test.ts
    └── types.ts
```

---

#### `WordToken.tsx`

Renders a single tokenized unit. For Story 3.4, this is plain text only — mastery coloring arrives in Epic 4.

```typescript
import type { TokenEntity } from '@/entities/token'

interface WordTokenProps {
  token: TokenEntity
}

export function WordToken({ token }: WordTokenProps) {
  if (token.type === 'whitespace') {
    return <span>{token.text}</span>
  }
  if (token.type === 'punctuation') {
    return <span data-type="punctuation">{token.text}</span>
  }
  // word token — prepared for future tap interaction (Epic 4)
  return (
    <span
      data-type="word"
      data-word-key={token.wordKey ?? undefined}
      data-token-id={token.id}
    >
      {token.text}
    </span>
  )
}
```

> **Do not add** `role="button"` or click handlers to `WordToken` in this story — that belongs to Story 4.2. The `data-*` attributes are prep for Epic 4 without behaviour leaking in.

---

#### `use-reader-view.ts`

Handles swipe/gesture detection (pointer events, no external library) and section navigation logic.

```typescript
import { useRef } from 'react'
import { useReaderStore } from '@/shared/stores'

const SWIPE_THRESHOLD_PX = 50

export function useReaderView() {
  const pointerStartX = useRef<number | null>(null)

  function navigateForward() {
    const { sections, currentSectionId, bookId, setPosition } = useReaderStore.getState()
    const idx = sections.findIndex((s) => s.id === currentSectionId)
    const next = sections[idx + 1]
    if (next && bookId) setPosition({ bookId, sectionId: next.id, tokenIndex: 0 })
  }

  function navigatePrev() {
    const { sections, currentSectionId, bookId, setPosition } = useReaderStore.getState()
    const idx = sections.findIndex((s) => s.id === currentSectionId)
    const prev = sections[idx - 1]
    if (prev && bookId) setPosition({ bookId, sectionId: prev.id, tokenIndex: 0 })
  }

  function handlePointerDown(e: React.PointerEvent) {
    pointerStartX.current = e.clientX
  }

  function handlePointerUp(e: React.PointerEvent) {
    if (pointerStartX.current === null) return
    const deltaX = e.clientX - pointerStartX.current
    if (Math.abs(deltaX) >= SWIPE_THRESHOLD_PX) {
      if (deltaX < 0) navigateForward() // swipe left = next
      else navigatePrev()               // swipe right = prev
    }
    pointerStartX.current = null
  }

  return { navigateForward, navigatePrev, handlePointerDown, handlePointerUp }
}
```

**Keyboard support (desktop):** Add `useEffect` in `ReaderView` (not `use-reader-view`) that listens to `keydown`:
- `ArrowRight` or `PageDown` → `navigateForward()`
- `ArrowLeft` or `PageUp` → `navigatePrev()`

---

#### `ReaderView.tsx`

Main reader widget. Consumes `useReaderStore` directly (widget layer can read shared stores).

```typescript
import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeft, ChevronRight, List } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useReaderStore } from '@/shared/stores'
import { WordToken } from './WordToken'
import { useReaderView } from '../model/use-reader-view'

interface ReaderViewProps {
  onChapterListOpen: () => void
}

export function ReaderView({ onChapterListOpen }: ReaderViewProps) {
  const tokens = useReaderStore((s) => s.tokens)
  const sections = useReaderStore((s) => s.sections)
  const currentSectionId = useReaderStore((s) => s.currentSectionId)
  const isChromeVisible = useReaderStore((s) => s.isChromeVisible)
  const bookId = useReaderStore((s) => s.bookId)
  const book = /* passed via props or read from useVaultStore */ undefined // see ReaderPage note below
  const toggleChrome = useReaderStore((s) => s.toggleChrome)
  const { navigateForward, navigatePrev, handlePointerDown, handlePointerUp } = useReaderView()

  const currentSection = sections.find((s) => s.id === currentSectionId)
  const currentSectionIndex = sections.findIndex((s) => s.id === currentSectionId)
  const canGoNext = currentSectionIndex < sections.length - 1
  const canGoPrev = currentSectionIndex > 0

  // Keyboard navigation
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'ArrowRight' || e.key === 'PageDown') navigateForward()
      if (e.key === 'ArrowLeft' || e.key === 'PageUp') navigatePrev()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [navigateForward, navigatePrev])

  return (
    <div className="relative h-screen overflow-hidden bg-white flex flex-col">
      {/* Top bar */}
      <header
        className={[
          'flex items-center gap-2 px-4 py-3 border-b bg-white transition-opacity duration-100',
          isChromeVisible ? 'opacity-100' : 'opacity-0 pointer-events-none',
        ].join(' ')}
      >
        <Button variant="ghost" size="icon" asChild>
          <Link to="/library" aria-label="Back to library">
            <ChevronLeft className="h-5 w-5" />
          </Link>
        </Button>
        <span className="flex-1 truncate text-sm font-medium">{/* book title passed via prop */}</span>
      </header>

      {/* Text content + gesture layer */}
      <main
        className="flex-1 overflow-y-auto relative"
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onClick={toggleChrome}
      >
        <div
          className="px-6 py-8"
          style={{ fontFamily: 'Georgia, serif', fontSize: '18px', lineHeight: 1.7 }}
        >
          {tokens.map((token) => (
            <WordToken key={token.id} token={token} />
          ))}
        </div>
      </main>

      {/* Bottom bar */}
      <footer
        className={[
          'flex items-center justify-between px-4 py-3 border-t bg-white transition-opacity duration-100',
          isChromeVisible ? 'opacity-100' : 'opacity-0 pointer-events-none',
        ].join(' ')}
      >
        <Button variant="ghost" size="icon" onClick={navigatePrev} disabled={!canGoPrev} aria-label="Previous section">
          <ChevronLeft className="h-5 w-5" />
        </Button>

        <Button variant="ghost" className="flex-1 truncate text-sm" onClick={onChapterListOpen}>
          <List className="h-4 w-4 mr-2" />
          {currentSection?.title ?? `Chapter ${currentSectionIndex + 1}`}
          {' · '}
          {currentSectionIndex + 1} / {sections.length}
        </Button>

        <Button variant="ghost" size="icon" onClick={navigateForward} disabled={!canGoNext} aria-label="Next section">
          <ChevronRight className="h-5 w-5" />
        </Button>
      </footer>
    </div>
  )
}
```

> **Note on `bookTitle`:** `ReaderView` does not import `useVaultStore` directly (widget layer should not cross into other domain stores). Pass `bookTitle: string` as a prop from `ReaderPage`, which already reads from `useVaultStore`.

Finalize `ReaderView.tsx` with `bookTitle: string` in `ReaderViewProps`. The sketch above is directional — adjust naming accordingly.

---

#### `model/types.ts`

```typescript
// Re-export entities used within the reader-view slice internals
export type { TokenEntity } from '@/entities/token'
export type { SectionEntity } from '@/entities/section'
```

---

#### `index.ts`

```typescript
export { ReaderView } from './ui/ReaderView'
export { WordToken } from './ui/WordToken'
```

---

### `ReaderPage.tsx` — Full Replacement

Replace the stub entirely. The page's responsibilities:
1. Read `bookId` from URL params; look up book in `useVaultStore`
2. On mount: load all sections for `bookId` → `setSections()`
3. On `currentSectionId` change: load tokens for that section → `setTokens()`; save `reading_progress` to SQLite
4. Add `visibilitychange` listener to flush position on background
5. Render `ReaderView` + chapter list `Dialog`
6. Clean up store on unmount (call `clear()`)

```typescript
import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { eq } from 'drizzle-orm'
import { useReaderStore, useVaultStore } from '@/shared/stores'
import { getDb, schema } from '@/shared/db'
import { ReaderView } from '@/widgets/reader-view'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

export function ReaderPage() {
  const { bookId } = useParams<{ bookId: string }>()
  const navigate = useNavigate()

  const book = useVaultStore((s) => s.books.find((b) => b.id === bookId)) ?? null
  const currentSectionId = useReaderStore((s) => s.currentSectionId)
  const sections = useReaderStore((s) => s.sections)
  const setSections = useReaderStore((s) => s.setSections)
  const setTokens = useReaderStore((s) => s.setTokens)
  const clear = useReaderStore((s) => s.clear)

  const [isChapterListOpen, setIsChapterListOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(true)

  // Guard: if no bookId param, redirect
  useEffect(() => {
    if (!bookId) navigate('/library')
  }, [bookId, navigate])

  // Load all sections for this book (once on mount)
  useEffect(() => {
    if (!bookId) return
    async function loadSections() {
      const db = getDb()
      const rows = await db
        .select()
        .from(schema.sections)
        .where(eq(schema.sections.bookId, bookId!))
        .orderBy(schema.sections.index)
      setSections(rows)
    }
    loadSections()
  }, [bookId, setSections])

  // Load tokens for current section + save position
  const savePosition = useCallback(async () => {
    const { bookId: storedBookId, currentSectionId: sid, tokenIndex } = useReaderStore.getState()
    if (!storedBookId || !sid) return
    const db = getDb()
    await db
      .insert(schema.readingProgress)
      .values({ bookId: storedBookId, sectionId: sid, tokenIndex, updatedAt: Math.floor(Date.now() / 1000) })
      .onConflictDoUpdate({
        target: schema.readingProgress.bookId,
        set: { sectionId: sid, tokenIndex, updatedAt: Math.floor(Date.now() / 1000) },
      })
  }, [])

  useEffect(() => {
    if (!currentSectionId) return
    async function loadTokens() {
      setIsLoading(true)
      const db = getDb()
      const rows = await db
        .select()
        .from(schema.tokens)
        .where(eq(schema.tokens.sectionId, currentSectionId!))
        .orderBy(schema.tokens.index)
      setTokens(rows)
      setIsLoading(false)
    }
    loadTokens()
    savePosition()
  }, [currentSectionId, setTokens, savePosition])

  // Flush position when app is backgrounded
  useEffect(() => {
    function onVisibilityChange() {
      if (document.visibilityState === 'hidden') savePosition()
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [savePosition])

  // Clean up store on unmount
  useEffect(() => {
    return () => clear()
  }, [clear])

  if (!book) {
    return (
      <div className="flex flex-col items-center justify-center h-screen gap-4">
        <p role="alert">Book not found</p>
        <Button variant="outline" onClick={() => navigate('/library')}>Back to library</Button>
      </div>
    )
  }

  return (
    <>
      {isLoading && (
        <div className="flex items-center justify-center h-screen" aria-live="polite">
          Loading…
        </div>
      )}
      {!isLoading && (
        <ReaderView
          bookTitle={book.title}
          onChapterListOpen={() => setIsChapterListOpen(true)}
        />
      )}

      {/* Chapter list dialog */}
      <Dialog open={isChapterListOpen} onOpenChange={setIsChapterListOpen}>
        <DialogContent>
          <DialogTitle>Chapters</DialogTitle>
          <ul className="flex flex-col gap-1 max-h-[60vh] overflow-y-auto">
            {sections.map((section, i) => (
              <li key={section.id}>
                <Button
                  variant="ghost"
                  className="w-full justify-start"
                  onClick={() => {
                    const { bookId: bid, setPosition } = useReaderStore.getState()
                    if (bid) setPosition({ bookId: bid, sectionId: section.id, tokenIndex: 0 })
                    setIsChapterListOpen(false)
                  }}
                >
                  {section.title ?? `Chapter ${i + 1}`}
                </Button>
              </li>
            ))}
          </ul>
        </DialogContent>
      </Dialog>
    </>
  )
}
```

> **`onConflictDoUpdate`:** Story 3.3 used `onConflictDoNothing()` for the initial insert of `reading_progress`. Here we use `onConflictDoUpdate` to update the position on every section change. The conflict target is the `bookId` primary key.

> **`clear()` on unmount:** Resets store so a subsequent book open starts clean. Do not call `clear()` on navigation within the reader — only on unmount.

> **Loading state:** Local `useState<boolean>` — not Zustand (loading is UI-local per AGENTS.md).

---

### Page Transition Animation

The `<100ms` animation AC is met with a CSS opacity + translate transition. Add to the text content `<main>` element:

```css
/* In the ReaderView's main container: */
transition: opacity 80ms ease, transform 80ms ease;
```

On section change, briefly animate `opacity: 0 → 1`. Implement with a `useEffect` watching `currentSectionId` and toggling a `data-animating` attribute or a local `isAnimating` boolean that adds/removes a CSS class.

Simple implementation in `ReaderView.tsx`:

```typescript
const [isAnimating, setIsAnimating] = useState(false)

// Trigger on section change
useEffect(() => {
  setIsAnimating(true)
  const id = setTimeout(() => setIsAnimating(false), 80)
  return () => clearTimeout(id)
}, [currentSectionId])
```

Add `opacity-0` when `isAnimating` is true (for the first 80ms), then `opacity-100`. This gives a fade-in on section change.

---

### index.css Fix (Deferred Item)

From `deferred-work.md`: `@layer utilities` is declared before `@import "tailwindcss"` — may be silently ignored in Tailwind v4. Since this story adds reader-specific styles, fix the ordering when touching `src/index.css`:

- Move any `@layer utilities { ... }` blocks to **after** the `@import "tailwindcss"` line.
- Verify the file opens with `@import "tailwindcss"` as the first non-comment line.

---

### Virtualization Note (Architecture Requirement)

The architecture mandates `@tanstack/react-virtual` for large sections. For this MVP story, **do not implement virtualization** — render all tokens in the section inline. Most EPUB chapters are < 3000 tokens; inline rendering is acceptable.

Add a `TODO` comment in `ReaderView.tsx`:

```typescript
// TODO: virtualize token rendering with @tanstack/react-virtual for sections > 3000 tokens
// See architecture.md "Gap Analysis — Virtualization" and widgets/reader-view/model/use-reader-view.ts
```

Full virtualization is a later optimization (Epic 4+). The architecture decision is acknowledged; the implementation is deferred.

---

### File Structure

#### New files

```
src/entities/section/
├── index.ts
└── model/
    └── types.ts

src/widgets/reader-view/
├── index.ts
├── ui/
│   ├── ReaderView.tsx
│   ├── ReaderView.test.tsx
│   └── WordToken.tsx
│   └── WordToken.test.tsx
└── model/
    ├── use-reader-view.ts
    ├── use-reader-view.test.ts
    └── types.ts
```

#### Modified files

| File | Change |
|------|--------|
| `src/entities/index.ts` | Add `SectionEntity` re-export |
| `src/shared/stores/use-reader-store.ts` | Add `sections`, `tokens`, `isChromeVisible`, `setSections`, `setTokens`, `toggleChrome`; extend `clear` |
| `src/shared/stores/use-reader-store.test.ts` | Cover new actions; verify `clear` resets new fields |
| `src/pages/reader-page/ui/ReaderPage.tsx` | Full replacement of stub |
| `src/pages/reader-page/ui/ReaderPage.test.tsx` | Full replacement of stub tests |
| `src/index.css` | Fix `@layer utilities` ordering (deferred item) |

---

### State & Data Flow

**Section load (once per book):**

```
ReaderPage mounts
  → useEffect([bookId])
  → SELECT sections WHERE bookId ORDER BY index
  → setSections(rows)
```

**Token load (on every section change):**

```
currentSectionId changes (setPosition called)
  → useEffect([currentSectionId])
  → SELECT tokens WHERE sectionId ORDER BY index
  → setTokens(rows)
  → savePosition() → UPSERT reading_progress
```

**Swipe/keyboard navigation:**

```
User swipes left / presses ArrowRight
  → use-reader-view.navigateForward()
  → sections[currentIndex + 1]
  → setPosition({ bookId, sectionId: next.id, tokenIndex: 0 })
  → triggers useEffect([currentSectionId]) → loads new tokens + saves position
```

**Chrome toggle:**

```
User taps margin area
  → ReaderView main onClick → toggleChrome()
  → isChromeVisible flips → top/bottom bars fade
```

**Unmount cleanup:**

```
Navigate to /library (back button)
  → ReaderPage unmount
  → useEffect cleanup: clear()
  → store reset (bookId: null, sections: [], tokens: [], ...)
```

---

### Testing Strategy

#### `use-reader-store.test.ts` (updates to existing)

| Test | Verifies |
|---|---|
| `setSections` | Sets sections array |
| `setTokens` | Sets tokens array |
| `toggleChrome` | Flips `isChromeVisible` false → true → false |
| `clear` (extended) | Resets `sections: []`, `tokens: []`, `isChromeVisible: true` |

#### `use-reader-view.test.ts` (new)

Mock `useReaderStore`. Provide `sections: [s1, s2, s3]` and `currentSectionId = s2.id`.

| Test | Verifies |
|---|---|
| `navigateForward` — middle section | `setPosition` called with `s3.id`, `tokenIndex: 0` |
| `navigateForward` — last section | `setPosition` NOT called (no next section) |
| `navigatePrev` — middle section | `setPosition` called with `s1.id`, `tokenIndex: 0` |
| `navigatePrev` — first section | `setPosition` NOT called |
| `handlePointerDown + handlePointerUp` — swipe left (deltaX = -60) | `navigateForward` triggered |
| `handlePointerDown + handlePointerUp` — swipe right (deltaX = +60) | `navigatePrev` triggered |
| `handlePointerDown + handlePointerUp` — tap (deltaX = 10) | No navigation |

#### `WordToken.test.tsx` (new)

| Test | Verifies |
|---|---|
| Word token | Renders `text`, has `data-type="word"`, `data-word-key` |
| Word token with null wordKey | Renders without crashing, no `data-word-key` attribute |
| Punctuation token | Has `data-type="punctuation"` |
| Whitespace token | Renders without crashing |

#### `ReaderView.test.tsx` (new)

Mock `useReaderStore`. Provide tokens and sections.

| Test | Verifies |
|---|---|
| Renders all word tokens | Each token text visible |
| Chrome visible by default | Top bar and bottom bar in DOM and visible |
| Chrome toggle | After `onClick` on main, `toggleChrome` called |
| Section count in bottom bar | "1 / 3" for 3 sections at index 0 |
| Prev button disabled at first section | `disabled` attribute present |
| Next button disabled at last section | `disabled` attribute present |
| Keyboard ArrowRight | `navigateForward` called |
| Keyboard ArrowLeft | `navigatePrev` called |

#### `ReaderPage.test.tsx` (full replacement)

Mock `useReaderStore`, `useVaultStore`, `getDb`.

| Test | Verifies |
|---|---|
| Book not found | Shows alert + back button |
| Shows loading state initially | `Loading…` text present before tokens resolve |
| Loads sections on mount | `setSections` called with DB result |
| Loads tokens on `currentSectionId` | `setTokens` called with DB result |
| Saves position after token load | `db.insert(readingProgress).onConflictDoUpdate` called |
| Flushes position on `visibilitychange` hidden | `savePosition` triggered when document hides |
| Clears store on unmount | `clear()` called |
| Chapter list dialog opens | Click chapter button → `DialogContent` visible with section titles |
| Jump to chapter | Click section → `setPosition` called with that sectionId |

---

### Quality Gate

After all commits:

- `npm run lint` — zero warnings
- `npm run typecheck` — zero errors
- `npm run test` — all tests pass (existing + new)
- `npm run build` — succeeds
- Desktop validation via `npm run tauri:dev` + Tauri MCP Bridge:
  - `driver_session` `start` → session connected (com.lekto.app)
  - `webview_screenshot` — known limitation: times out on Linux/WebKitGTK (see Story 3.3 completion notes). Document this in the PR description.
  - Manual smoke test: import a book, open it, swipe to next chapter, verify chapter indicator updates, open chapter list, jump to a chapter, close app and reopen — verify resume at correct position.

---

### Out of Scope (deferred)

- **Mastery word coloring** — `WordToken` carries `data-word-key` but no color. Epic 4 (Story 4.1).
- **`WordToken` tap → translation panel** — Story 4.2 / Epic 5.
- **`@tanstack/react-virtual` token virtualization** — noted with TODO; deferred to Epic 4+.
- **Sepia / Dark theme** — Story 4.4.
- **Font/size/margin reader settings** — Story 4.4.
- **TTS** — Epic 6.
- **Sub-section (token-level) scroll position restore** — `tokenIndex` is always 0 on section change; fine for MVP.
- **Delete dialog close animation** (`deferred-work.md`) — affects `LibraryPage` only, not touched here.

---

## Tasks / Subtasks

### Commit 1: `feat(entities): add SectionEntity type`

- [x] Task 1: Create `src/entities/section/model/types.ts` — `SectionEntity` interface
- [x] Task 2: Create `src/entities/section/index.ts` — re-export `SectionEntity`
- [x] Task 3: Add `SectionEntity` re-export to `src/entities/index.ts`
- [x] Task 4: Quality gate — `npm run lint && npm run typecheck && npm run test && npm run build`

### Commit 2: `feat(stores): extend useReaderStore with sections, tokens, chrome state`

- [x] Task 5: Extend `src/shared/stores/use-reader-store.ts` — add `sections`, `tokens`, `isChromeVisible`, `setSections`, `setTokens`, `toggleChrome`; extend `clear`
- [x] Task 6: Update `src/shared/stores/use-reader-store.test.ts` — cover new actions and extended `clear`
- [x] Task 7: Quality gate

### Commit 3: `feat(widgets): reader-view slice — WordToken, ReaderView, use-reader-view`

- [x] Task 8: Create `src/widgets/reader-view/model/types.ts`
- [x] Task 9: Create `src/widgets/reader-view/model/use-reader-view.ts` — gesture detection and section navigation
- [x] Task 10: Create `src/widgets/reader-view/model/use-reader-view.test.ts` — cover all gesture + navigation cases
- [x] Task 11: Create `src/widgets/reader-view/ui/WordToken.tsx`
- [x] Task 12: Create `src/widgets/reader-view/ui/WordToken.test.tsx`
- [x] Task 13: Create `src/widgets/reader-view/ui/ReaderView.tsx` — token render, chrome, keyboard, animation, chapter list callback
- [x] Task 14: Create `src/widgets/reader-view/ui/ReaderView.test.tsx`
- [x] Task 15: Create `src/widgets/reader-view/index.ts`
- [x] Task 16: Quality gate

### Commit 4: `feat(reader): full ReaderPage — section load, token load, position save, chapter dialog`

- [x] Task 17: Replace `src/pages/reader-page/ui/ReaderPage.tsx` — section load, token load, `savePosition`, `visibilitychange`, `clear` on unmount, `ReaderView` + chapter list `Dialog`
- [x] Task 18: Replace `src/pages/reader-page/ui/ReaderPage.test.tsx` — full test coverage per Testing Strategy above
- [x] Task 19: Fix `src/index.css` `@layer utilities` ordering (deferred item from `deferred-work.md`)
- [x] Task 20: Quality gate

### Commit 5: `chore: final quality gate + desktop validation`

- [x] Task 21: `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` — all green
- [x] Task 22: Desktop validation — `npm run tauri:dev` not running; `driver_session start` timed out. Known limitation on Linux/WebKitGTK (documented in Story 3.3 completion notes). Manual smoke test deferred to user.

### Review Follow-ups (AI)

- [ ] [AI-Review][Low] Stabilize `useReaderView()` callbacks with `useCallback` so `ReaderView`'s keyboard `useEffect` no longer adds/removes the `keydown` listener on every render [src/widgets/reader-view/model/use-reader-view.ts:9-21, src/widgets/reader-view/ui/ReaderView.tsx:30-37]
- [ ] [AI-Review][Low] Scope chrome-toggle to non-text taps (margin/padding) per AC9; current `onClick={toggleChrome}` on `<main>` also fires on text — refine when Story 4.2 wires WordToken interactions [src/widgets/reader-view/ui/ReaderView.tsx:55-60]
- [ ] [AI-Review][Low] Guard against eternal "Loading…" when `ReaderPage` mounts without a `currentSectionId` (deep-link / hard refresh) — either reset `isLoading=false` when no section is set or pre-load `reading_progress` inside `ReaderPage` [src/pages/reader-page/ui/ReaderPage.tsx:23,56-71]
- [ ] [AI-Review][Low] Add stale-result handling (ignore flag or AbortController) to `loadSections` and `loadTokens` so rapid section changes can't resolve out of order [src/pages/reader-page/ui/ReaderPage.tsx:29-41,56-71]
- [ ] [AI-Review][Low] Drop the `bookId!` and `currentSectionId!` non-null assertions — the early returns already narrow them [src/pages/reader-page/ui/ReaderPage.tsx:36,64]
- [ ] [Sonar][Major] `typescript:S7721` — Move `navigateForward` and `navigatePrev` out of `useReaderView`'s body (or stabilize with `useCallback`) so they aren't redeclared on every render [src/widgets/reader-view/model/use-reader-view.ts:9,16]
- [ ] [Sonar][Major] `typescript:S6847` — `<main>` is a non-interactive element with mouse + keyboard event handlers; add `role="button"`/`tabIndex={0}` or move the chrome-toggle handler to a dedicated interactive padding area (also addresses Phase 1 LOW #2) [src/widgets/reader-view/ui/ReaderView.tsx:55-60]
- [ ] [Sonar][Minor] `typescript:S1082` — Click handler on `<main>` lacks a paired keyboard handler; resolves alongside S6847 [src/widgets/reader-view/ui/ReaderView.tsx:55-60]
- [ ] [Sonar][Minor] `typescript:S6759` — Mark `ReaderViewProps` and `WordTokenProps` as `Readonly<…>` (or use `readonly` on each field) [src/widgets/reader-view/ui/ReaderView.tsx:17, src/widgets/reader-view/ui/WordToken.tsx:7]
- [ ] [Sonar][Minor] `typescript:S7764` — Replace `window.addEventListener` / `window.removeEventListener` with `globalThis.…` [src/widgets/reader-view/ui/ReaderView.tsx:35-36]
- [ ] [Sonar][Info] `typescript:S1135` — The `TODO: virtualize token rendering` is fine for now; track via a real story before resolving [src/widgets/reader-view/ui/ReaderView.tsx:9]

---

## References

- Acceptance criteria: `_bmad-output/planning-artifacts/epics.md` — Story 3.4 section
- Architecture — `widgets/reader-view/` structure: `_bmad-output/planning-artifacts/architecture.md` lines 595–650
- Architecture — Read flow: `_bmad-output/planning-artifacts/architecture.md` lines 839–878
- Architecture — Virtualization gap: `_bmad-output/planning-artifacts/architecture.md` lines 1029–1033
- UX — Reader chrome, typography, swipe: `_bmad-output/planning-artifacts/ux-design-specification.md` lines 274–335, 379–498, 501–596
- UX — Sepia/Dark theme (out of scope here): `_bmad-output/planning-artifacts/ux-design-specification.md` lines 338–376
- Previous story (stub + patterns): `_bmad-output/implementation-artifacts/3-3-library-view-and-book-management.md`
- Existing stub: `src/pages/reader-page/ui/ReaderPage.tsx`
- Existing store: `src/shared/stores/use-reader-store.ts`
- Existing `TokenEntity`: `src/entities/token/model/types.ts`
- DB schema: `src/shared/db/schema.ts`
- Deferred work items: `_bmad-output/implementation-artifacts/deferred-work.md`
- AGENTS.md (project conventions, mandatory)

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None.

### Completion Notes List

- Implemented `SectionEntity` type in `src/entities/section/` and re-exported from `src/entities/index.ts`
- Extended `useReaderStore` with `sections`, `tokens`, `isChromeVisible`, `setSections`, `setTokens`, `toggleChrome`; `clear` now resets all new fields
- Created `src/widgets/reader-view/` slice: `WordToken`, `ReaderView`, `use-reader-view` (gesture detection + section nav)
- Animation fix: story notes suggested `setState` inside `useEffect` for fade-in; replaced with CSS `@keyframes readerFadeIn` + `key={currentSectionId}` on the content div to satisfy the `react-hooks/set-state-in-effect` ESLint rule
- `ReaderPage` fully replaces the stub: loads sections on mount, loads tokens on section change, `onConflictDoUpdate` for position save, `visibilitychange` flush, `clear` on unmount, chapter list Dialog
- Drizzle returns `type: string` for token rows; cast to `TokenEntity[]` in `ReaderPage` since the DB only contains valid `TokenType` values
- `@layer utilities` ordering in `index.css` was already correct (after `@import "tailwindcss"`); added `@keyframes readerFadeIn` to the same file
- Desktop MCP validation: `npm run tauri:dev` not started; `driver_session` timed out. Known Linux/WebKitGTK limitation (see Story 3.3). Manual smoke test deferred to user
- Final gate: 370 tests passing, 0 lint errors, 0 type errors, build succeeds

### File List

- `src/entities/section/model/types.ts` (new)
- `src/entities/section/index.ts` (new)
- `src/entities/index.ts` (modified — added SectionEntity re-export)
- `src/shared/stores/use-reader-store.ts` (modified — extended with sections/tokens/chrome)
- `src/shared/stores/use-reader-store.test.ts` (modified — added new action tests)
- `src/widgets/reader-view/model/types.ts` (new)
- `src/widgets/reader-view/model/use-reader-view.ts` (new)
- `src/widgets/reader-view/model/use-reader-view.test.ts` (new)
- `src/widgets/reader-view/ui/WordToken.tsx` (new)
- `src/widgets/reader-view/ui/WordToken.test.tsx` (new)
- `src/widgets/reader-view/ui/ReaderView.tsx` (new)
- `src/widgets/reader-view/ui/ReaderView.test.tsx` (new)
- `src/widgets/reader-view/index.ts` (new)
- `src/pages/reader-page/ui/ReaderPage.tsx` (modified — full replacement)
- `src/pages/reader-page/ui/ReaderPage.test.tsx` (modified — full replacement)
- `src/index.css` (modified — added @keyframes readerFadeIn)
