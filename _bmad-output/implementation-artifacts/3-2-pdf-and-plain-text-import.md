# Story 3.2: PDF & Plain Text Import

Status: in-progress

## Story

As a reader,
I want to import a PDF or plain text file from my device,
So that I can read any of my documents in Lekto with vocabulary tracking.

## Acceptance Criteria

1. **Given** the user opens the file picker
   **When** the picker is displayed
   **Then** PDF (`.pdf`) and plain text (`.txt`) files are selectable in addition to EPUB

2. **Given** the user selects a valid PDF file with an embedded text layer
   **When** PDF.js processes the file
   **Then** the text content is extracted page by page, tokenized with the same Latin pipeline as EPUB, and stored in the `sections` and `tokens` SQLite tables — one section per PDF page

3. **Given** a PDF with embedded text is imported
   **When** tokenization runs
   **Then** word boundaries are correctly identified and each word receives a normalized `wordKey` (diacritics preserved, lowercase)

4. **Given** the user selects a plain text file
   **When** import runs
   **Then** the raw text is decoded as UTF-8, stored as a single section, tokenized with the same Latin regex pipeline as EPUB, and written to SQLite

5. **Given** a PDF that contains only scanned images (no embedded text layer)
   **When** PDF.js finds no text content in any page
   **Then** an inline error message informs the user that the PDF has no selectable text — the import is aborted cleanly with no data written

6. **Given** a PDF or TXT import completes
   **When** the language prompt appears
   **Then** the user confirms or changes the language, after which the book appears in the library with a placeholder cover and 0% progress

7. **Given** CJK content is detected in a PDF during import
   **When** tokenization runs
   **Then** the content is imported without crashing — CJK word boundaries are approximate at MVP (kuromoji deferred), the file is not rejected

8. **Given** all changes
   **Then** `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build` all pass with zero errors

## Dev Notes

### Context & What This Story Builds On

Story 3.1 established the complete import pipeline for EPUB. This story extends it by adding two new parsers that plug into the same orchestrator (`import-book.ts`). The `sections`/`tokens` schema and the tokenizer are unchanged — only new parsers and a dispatch step are added.

**Review follow-ups from 3.1 that are now resolved in code:**
- DB transaction wrapping: already implemented in `import-book.ts` (lines 68–79)
- `async/await` in LibraryPage: confirmed OK — `handleImport` uses `await` throughout

---

### New Packages to Install

```bash
npm install pdfjs-dist
```

- **`pdfjs-dist`** (v4.x, currently ~4.10+) — Mozilla's PDF.js compiled for npm. Extracts text layers from PDF files. Requires a web worker for the main rendering thread; in Vite use the `?url` import to bundle the worker automatically.

No new package for plain text — use the native `TextDecoder` API.

---

### Architecture: Format Dispatch in `import-book.ts`

`import-book.ts` currently hardcodes `parseEpub`. Replace the parse step with a format dispatch based on the file extension:

```typescript
const ext = fileName.split('.').pop()?.toLowerCase()
let parseResult: AsyncResult<ParsedBook>
if (ext === 'epub') {
  parseResult = await parseEpub(data)
} else if (ext === 'pdf') {
  parseResult = await parsePdf(data, fileName)
} else if (ext === 'txt') {
  parseResult = await parseTxt(data, fileName)
} else {
  return err(`Unsupported file format: .${ext ?? 'unknown'}`)
}
if (parseResult.isErr()) return err(parseResult.error)
const parsedBook = parseResult.value
```

The rest of the function (language detection, language dialog, vault write, DB inserts) is identical — no changes needed after the parse step.

---

### `parse-pdf.ts` — PDF.js Text Extraction

**File:** `src/features/import-book/api/parse-pdf.ts`

PDF.js v4 API:

```typescript
import * as pdfjsLib from 'pdfjs-dist'
import type { TextItem } from 'pdfjs-dist/types/src/display/api'
// Vite bundles the worker as a separate chunk and returns its URL:
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

export async function parsePdf(data: ArrayBuffer, fileName: string): AsyncResult<ParsedBook> {
  try {
    pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl
    const pdf = await pdfjsLib.getDocument({ data }).promise
    const sections: Array<{ title: string; text: string }> = []

    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i)
      const content = await page.getTextContent()
      const text = content.items
        .filter((item): item is TextItem => 'str' in item)
        .map((item) => item.str)
        .join(' ')
      sections.push({ title: `Page ${i}`, text })
    }

    // Scanned PDF: all pages have empty text
    const totalText = sections.map((s) => s.text.trim()).join('')
    if (totalText.length === 0) {
      return err('This PDF has no selectable text — it may be a scanned image')
    }

    const meta = await pdf.getMetadata().catch(() => null)
    const pdfTitle = (meta?.info as Record<string, unknown> | null)?.['Title']
    const title = typeof pdfTitle === 'string' && pdfTitle.trim()
      ? pdfTitle.trim()
      : fileName.replace(/\.[^.]+$/, '')

    return ok({ title, sections })
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    return err(`Failed to parse PDF — ${detail}`)
  }
}
```

**Key decisions:**
- Worker set once at the top of the function — idempotent if called multiple times
- Title: `pdf.getMetadata()` → `info.Title` → filename without extension
- Scanned PDF check: if all extracted text is empty/whitespace → `err` with user-friendly message
- Page sections: one section per PDF page (consistent with `sections` table semantics)
- `TextItem` filter: PDF.js `getTextContent` returns both `TextItem` (has `.str`) and `TextMarkedContent` (no `.str`) — filter for items with `'str' in item`

> **Mock `pdfjs-dist` entirely in unit tests** — do not attempt real PDF rendering in jsdom.

---

### `parse-txt.ts` — Plain Text Import

**File:** `src/features/import-book/api/parse-txt.ts`

```typescript
export async function parseTxt(data: ArrayBuffer, fileName: string): AsyncResult<ParsedBook> {
  try {
    const text = new TextDecoder('utf-8').decode(data)
    if (text.trim().length === 0) {
      return err('The text file is empty')
    }
    const title = fileName.replace(/\.[^.]+$/, '')
    return ok({ title, sections: [{ title: '', text }] })
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    return err(`Failed to read text file — ${detail}`)
  }
}
```

**Key decisions:**
- Single section for the entire file (no chapter detection at MVP)
- Section title is empty string (consistent with how epubjs returns sections without labels)
- Title: filename without extension (`'my-book.txt'` → `'my-book'`)
- TextDecoder throws on malformed UTF-8 → caught and returned as `err`

---

### Updated `LibraryPage.tsx` — File Picker Accept

Change **one line** in `handleImport`:

```typescript
// Before:
const pickerResult = await filePickerAdapter.pickFile({ accept: ['.epub'] })

// After:
const pickerResult = await filePickerAdapter.pickFile({ accept: ['.epub', '.pdf', '.txt'] })
```

Button label update: `Import Book` (instead of `Import EPUB`) to reflect multi-format support.

No other changes to LibraryPage — the `importBook` orchestrator handles format dispatch transparently.

---

### Updated `import-book.ts` — Imports and Dispatch

Add these imports at the top:

```typescript
import { parsePdf } from '../api/parse-pdf'
import { parseTxt } from '../api/parse-txt'
```

Replace the single `parseEpub` call (line 19) with the format dispatch block shown above. The `ParsedBook` type is already imported via `parse-epub.ts` — no type changes needed (all parsers return the same interface).

---

### Updated `index.ts` — Exports

No changes needed — `ParsedBook` is already re-exported from `parse-epub`. The new parsers are internal API details, not part of the feature's public interface.

---

### File Structure

#### New files

```
src/features/import-book/api/
├── parse-pdf.ts
├── parse-pdf.test.ts
├── parse-txt.ts
└── parse-txt.test.ts
```

#### Modified files

| File | Change |
|------|--------|
| `src/features/import-book/model/import-book.ts` | Add format dispatch (see above) |
| `src/pages/library-page/ui/LibraryPage.tsx` | Update `accept` + button label |
| `src/pages/library-page/ui/LibraryPage.test.tsx` | Add PDF/TXT import test cases |

---

### CJK / Unicode Handling

The existing `latinTokenizer` uses `/\p{L}+/u` which matches Unicode Letter characters, including CJK ideographs (Chinese, Japanese, Korean). CJK characters will be grouped into single multi-character tokens, which is incorrect for word boundaries but acceptable at MVP. No changes to the tokenizer are needed — this is documented behavior.

---

### Testing Strategy

#### `parse-pdf.test.ts`

Mock `pdfjs-dist` module entirely:

```typescript
vi.mock('pdfjs-dist', () => ({
  GlobalWorkerOptions: { workerSrc: '' },
  getDocument: vi.fn(),
}))
vi.mock('pdfjs-dist/build/pdf.worker.min.mjs?url', () => ({ default: 'worker.js' }))
```

| Test case | What to verify |
|-----------|---------------|
| Happy path: PDF with text | Returns `ok(ParsedBook)` with correct title and sections |
| Title from metadata | When `info.Title` is set, uses it as book title |
| Title fallback | When no metadata title, uses filename without extension |
| Scanned PDF | When all page texts are empty → returns `err('...no selectable text...')` |
| `getDocument` throws | Returns `err('Failed to parse PDF — ...')` |
| Multi-page PDF | Creates one section per page |

#### `parse-txt.test.ts`

No mocking needed — `TextDecoder` is available in jsdom.

| Test case | What to verify |
|-----------|---------------|
| UTF-8 text file | Returns `ok` with title from filename, single section |
| Empty file | Returns `err('The text file is empty')` |
| Title extraction | `'my-novel.txt'` → title `'my-novel'` |
| File with only whitespace | Treated as empty → returns `err` |

#### `import-book.test.ts` (additions)

Add three new test cases to the existing describe block:

```typescript
it('dispatches to parsePdf for .pdf files', ...)
it('dispatches to parseTxt for .txt files', ...)
it('returns err for unsupported file extension', ...)
```

Mock `../api/parse-pdf` and `../api/parse-txt` the same way `parse-epub` is mocked.

#### `LibraryPage.test.tsx` (update)

Update the existing "import button triggers picker" test to use `accept: ['.epub', '.pdf', '.txt']`.
Add one test: file picker called with correct accept array.

---

### Quality Gate

After all commits:
- `npm run lint` — zero warnings
- `npm run typecheck` — zero errors
- `npm run test` — all tests pass (new + existing 251+)
- `npm run build` — succeeds

---

## Tasks / Subtasks

### Commit 1: `feat(import-book): plain text parser`

- [x] Task 1: Create `src/features/import-book/api/parse-txt.ts`
  - [x] Use `new TextDecoder('utf-8').decode(data)` to read the file
  - [x] Return `err('The text file is empty')` if trimmed text is empty
  - [x] Title: `fileName.replace(/\.[^.]+$/, '')`
  - [x] Return `ok({ title, sections: [{ title: '', text }] })`
  - [x] Wrap in try/catch → `err('Failed to read text file — ...')`

- [x] Task 2: Create `src/features/import-book/api/parse-txt.test.ts`
  - [x] Happy path: returns `ok` with correct title and single section text
  - [x] Empty file → `err('The text file is empty')`
  - [x] Whitespace-only file → `err('The text file is empty')`
  - [x] Title extracted correctly from `'my-novel.txt'` → `'my-novel'`

- [x] Task 3: Quality gate — commit 1
  - [x] `npm run lint && npm run typecheck && npm run test`

---

### Commit 2: `feat(import-book): PDF parser using pdfjs-dist`

- [x] Task 4: Install `pdfjs-dist` — `npm install pdfjs-dist`

- [x] Task 5: Create `src/features/import-book/api/parse-pdf.ts`
  - [x] Set `pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl` where `workerUrl` is imported via `?url`
  - [x] Load PDF with `pdfjsLib.getDocument({ data }).promise`
  - [x] Extract text per page: `page.getTextContent()` → filter `'str' in item` → join with `' '`
  - [x] Scanned PDF check: all page texts empty → return `err('This PDF has no selectable text — it may be a scanned image')`
  - [x] Title: `pdf.getMetadata()` → `info.Title` → filename without extension
  - [x] Return `ok({ title, sections })` where sections is one entry per page
  - [x] Wrap entire function in try/catch → `err('Failed to parse PDF — ...')`

- [x] Task 6: Create `src/features/import-book/api/parse-pdf.test.ts`
  - [x] Mock `pdfjs-dist` and `pdfjs-dist/build/pdf.worker.min.mjs?url`
  - [x] Happy path: returns `ok(ParsedBook)` with pages as sections
  - [x] Metadata title used when available
  - [x] Filename fallback title when no metadata title
  - [x] All empty pages → `err` with scanned PDF message
  - [x] `getDocument` throws → `err('Failed to parse PDF — ...')`

- [x] Task 7: Quality gate — commit 2
  - [x] `npm run lint && npm run typecheck && npm run test`

---

### Commit 3: `feat(import-book): format dispatch in orchestrator`

- [x] Task 8: Update `src/features/import-book/model/import-book.ts`
  - [x] Add imports: `import { parsePdf } from '../api/parse-pdf'` and `import { parseTxt } from '../api/parse-txt'`
  - [x] Replace line `const parseResult = await parseEpub(data)` with format dispatch block (see Dev Notes)
  - [x] Unsupported extension → `return err('Unsupported file format: .${ext ?? "unknown"}')`
  - [x] All code after the parse step is unchanged

- [x] Task 9: Update `src/features/import-book/model/import-book.test.ts`
  - [x] Add `vi.mock('../api/parse-pdf', ...)` and `vi.mock('../api/parse-txt', ...)`
  - [x] Add test: `.pdf` file → `parsePdf` called, `parseEpub` not called
  - [x] Add test: `.txt` file → `parseTxt` called, `parseEpub` not called
  - [x] Add test: unsupported extension `.docx` → returns `err('Unsupported file format: .docx')`

- [x] Task 10: Quality gate — commit 3
  - [x] `npm run lint && npm run typecheck && npm run test`

---

### Commit 4: `feat(library): update import button for PDF and TXT`

- [x] Task 11: Update `src/pages/library-page/ui/LibraryPage.tsx`
  - [x] Change `pickFile({ accept: ['.epub'] })` to `pickFile({ accept: ['.epub', '.pdf', '.txt'] })`
  - [x] Change button label from `Import EPUB` to `Import Book`

- [x] Task 12: Update `src/pages/library-page/ui/LibraryPage.test.tsx`
  - [x] Update existing test that checks the accept array to expect `['.epub', '.pdf', '.txt']`
  - [x] Update button label assertion from `'Import EPUB'` to `'Import Book'`

- [x] Task 13: Final quality gate
  - [x] `npm run lint` — zero warnings (pre-existing lint warnings in generated Capacitor file only)
  - [x] `npm run typecheck` — zero errors
  - [x] `npm run test` — all tests pass (282 total)
  - [x] `npm run build` — succeeds

### Review Follow-ups (AI)

- [x] [AI-Review][MEDIUM] Fix user-visible error message: `import-book.ts:50` says `'Failed to save EPUB: ...'` — should say `'Failed to save file: ...'` since this step now handles PDF and TXT files too [src/features/import-book/model/import-book.ts:50]
- [x] [AI-Review][LOW] Update stale inline comment in `import-book.ts:43-44` — "save EPUB to vault" should read "save file to vault" [src/features/import-book/model/import-book.ts:43]
- [x] [AI-Review][LOW] Add test in `parse-pdf.test.ts` for `getMetadata()` throwing — verify title falls back to filename via the `.catch(() => null)` path [src/features/import-book/api/parse-pdf.test.ts]

---

## Dev Agent Record

### Implementation Notes

- `parse-txt.ts`: uses `TextDecoder('utf-8')` natively — no dependencies. Single section per file, empty title.
- `parse-pdf.ts`: uses **lazy dynamic import** (`await import('pdfjs-dist')`) instead of a static top-level import. This is required to avoid `DOMMatrix is not defined` errors in jsdom test environments — PDF.js v5 references browser-only globals at module evaluation time. Pattern follows `parse-epub.ts`'s lazy `import('epubjs')` approach.
- `import-book.ts`: format dispatch replaces the single `parseEpub` call; the rest of the function (language detection, vault write, DB insert) is unchanged.
- `LibraryPage.tsx`: two-line change — accept array extended, button label updated.

### Completion Notes

All 13 tasks completed + 3 review follow-ups resolved + 1 post-review bug fix. 284 tests pass. Zero typecheck errors. Build succeeds. pdfjs-dist v5.6.205 installed (story spec said v4.x; v5 API is identical for our usage — `getDocument`, `getTextContent`, `TextItem`).

**Post-review bug fix (2026-04-26):** PDF import failed on desktop with "Failed to parse PDF — undefined is not a function (near '...value of readableStream...')". Root cause: pdfjs-dist v5 uses `ReadableStream` async iteration, unsupported in Tauri's WebKitGTK webview. Fixed by passing `disableStream: true` to `getDocument()`.

---

## File List

### New Files
- `src/features/import-book/api/parse-txt.ts`
- `src/features/import-book/api/parse-txt.test.ts`
- `src/features/import-book/api/parse-pdf.ts`
- `src/features/import-book/api/parse-pdf.test.ts`

### Modified Files
- `src/features/import-book/model/import-book.ts`
- `src/features/import-book/model/import-book.test.ts`
- `src/pages/library-page/ui/LibraryPage.tsx`
- `src/pages/library-page/ui/LibraryPage.test.tsx`
- `package.json`
- `package-lock.json`

---

## Change Log

- Added plain text parser (`parse-txt.ts`) with UTF-8 decoding and empty-file guard (2026-04-22)
- Added PDF parser (`parse-pdf.ts`) using pdfjs-dist v5 with lazy import, per-page sections, scanned-PDF detection (2026-04-22)
- Updated `import-book.ts` orchestrator with format dispatch for epub/pdf/txt and unsupported-extension error (2026-04-22)
- Updated `LibraryPage.tsx`: accept array extended to `.epub`, `.pdf`, `.txt`; button label changed to "Import Book" (2026-04-22)
- Fixed PDF import on Tauri desktop: `disableStream: true` in `getDocument()` to avoid ReadableStream async iteration (unsupported in WebKitGTK) (2026-04-26)
- Resolved all 3 AI code review follow-ups: stale error message, stale comment, getMetadata-throws test (2026-04-26)

---

## References

- Acceptance criteria: `_bmad-output/planning-artifacts/epics.md#Story 3.2`
- Architecture — import pipeline: `_bmad-output/planning-artifacts/architecture.md` (lines ~839–846)
- Architecture — FSD structure: `_bmad-output/planning-artifacts/architecture.md` (lines ~651–663)
- Architecture — gap analysis: `_bmad-output/planning-artifacts/architecture.md` (lines ~970–975)
- Previous story (patterns): `_bmad-output/implementation-artifacts/3-1-epub-import-and-tokenization.md`
- Existing import orchestrator: `src/features/import-book/model/import-book.ts`
- Existing EPUB parser (pattern to follow): `src/features/import-book/api/parse-epub.ts`
- File picker adapter: `src/shared/platform/file-picker/file-picker.interface.ts`
- Library page (to update): `src/pages/library-page/ui/LibraryPage.tsx`
