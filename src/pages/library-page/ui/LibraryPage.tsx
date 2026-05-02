import { useState, useEffect, useCallback, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { eq, sql } from 'drizzle-orm'
import { filePickerAdapter } from '@/shared/platform'
import { importBook } from '@/features/import-book'
import { deleteBook } from '@/features/delete-book'
import { useVaultStore, useReaderStore } from '@/shared/stores'
import { getDb, schema } from '@/shared/db'
import { SEED_LANGUAGES } from '@/shared/lib'
import { BookListItem } from '@/widgets/book-list-item'
import { ContinueReadingCard } from '@/widgets/continue-reading-card'
import type { BookEntity } from '@/entities'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface ProgressRow {
  bookId: string
  sectionId: string
  sectionIndex: number
  sectionTitle: string | null
  updatedAt: number
}

interface SectionCount {
  bookId: string
  count: number
}

interface LibraryState {
  progressRows: ProgressRow[]
  sectionCounts: SectionCount[]
  languageNames: Record<string, string>
}

function computeProgressPct(sectionIndex: number, totalSections: number): number {
  if (totalSections === 0) return 0
  return Math.round((sectionIndex / totalSections) * 100)
}

export function LibraryPage() {
  const books = useVaultStore((s) => s.books)
  const navigate = useNavigate()

  const [isImporting, setIsImporting] = useState(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [langDialogOpen, setLangDialogOpen] = useState(false)
  const [detectedLang, setDetectedLang] = useState<string | null>(null)
  const [selectedLang, setSelectedLang] = useState<string>('en')
  const langResolverRef = useRef<((code: string | null) => void) | null>(null)

  const [libraryState, setLibraryState] = useState<LibraryState>({
    progressRows: [],
    sectionCounts: [],
    languageNames: {},
  })

  const [activeLanguageFilter, setActiveLanguageFilter] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<{ book: BookEntity } | null>(null)

  const hydrateLibraryData = useCallback(async () => {
    if (books.length === 0) {
      setLibraryState({ progressRows: [], sectionCounts: [], languageNames: {} })
      return
    }
    try {
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

      const languageRows = await db.select().from(schema.languages)
      const languageNames: Record<string, string> = {}
      for (const row of languageRows) {
        languageNames[row.code] = row.name
      }

      setLibraryState({ progressRows, sectionCounts, languageNames })
    } catch {
      // DB not ready — stay with empty state
    }
  }, [books])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState fires after await; the rule cannot statically prove this is safe
    void hydrateLibraryData()
  }, [hydrateLibraryData])

  const resolveLanguage = (detected: string | null): Promise<string | null> =>
    new Promise((resolve) => {
      setDetectedLang(detected)
      setSelectedLang(detected ?? 'en')
      langResolverRef.current = resolve
      setLangDialogOpen(true)
    })

  const handleLangConfirm = () => {
    setLangDialogOpen(false)
    langResolverRef.current?.(selectedLang)
  }

  const handleLangCancel = () => {
    setLangDialogOpen(false)
    langResolverRef.current?.(null)
  }

  const handleImport = async () => {
    setImportError(null)
    const pickerResult = await filePickerAdapter.pickFile({ accept: ['.epub', '.pdf', '.txt'] })
    if (pickerResult.isErr()) return

    const { data, name } = pickerResult.value
    setIsImporting(true)
    const result = await importBook(data, name, resolveLanguage)
    setIsImporting(false)
    if (result.isErr()) {
      setImportError(result.error)
    }
  }

  const handleOpen = async (bookId: string) => {
    const db = getDb()
    const rows = await db
      .select()
      .from(schema.readingProgress)
      .where(eq(schema.readingProgress.bookId, bookId))
    const existing = rows[0]

    let sectionId: string
    let tokenIndex: number

    if (existing) {
      sectionId = existing.sectionId
      tokenIndex = existing.tokenIndex
      await db
        .update(schema.readingProgress)
        .set({ updatedAt: Math.floor(Date.now() / 1000) })
        .where(eq(schema.readingProgress.bookId, bookId))
    } else {
      const sections = await db
        .select()
        .from(schema.sections)
        .where(eq(schema.sections.bookId, bookId))
        .orderBy(schema.sections.index)
        .limit(1)
      const firstSection = sections[0]
      if (!firstSection) return
      sectionId = firstSection.id
      tokenIndex = 0
      await db
        .insert(schema.readingProgress)
        .values({ bookId, sectionId, tokenIndex: 0, updatedAt: Math.floor(Date.now() / 1000) })
        .onConflictDoNothing()
    }

    useReaderStore.getState().setPosition({ bookId, sectionId, tokenIndex })
    navigate(`/reader/${bookId}`)
  }

  const handleConfirmDelete = async () => {
    if (!pendingDelete) return
    const { book } = pendingDelete
    setPendingDelete(null)
    await deleteBook(book.id)
    await hydrateLibraryData()
  }

  // Derived display data
  const { progressRows, sectionCounts, languageNames } = libraryState

  const sectionCountMap: Record<string, number> = {}
  for (const sc of sectionCounts) {
    sectionCountMap[sc.bookId] = sc.count
  }

  const progressMap: Record<string, ProgressRow> = {}
  for (const row of progressRows) {
    progressMap[row.bookId] = row
  }

  const lastOpenedRow =
    progressRows.length > 0
      ? progressRows.reduce((max, r) => (r.updatedAt > max.updatedAt ? r : max))
      : null
  const lastOpenedBook = lastOpenedRow
    ? (books.find((b) => b.id === lastOpenedRow.bookId) ?? null)
    : null

  const distinctLanguages = [...new Set(books.map((b) => b.language))]
  const showLanguageFilter = distinctLanguages.length >= 2

  const filteredBooks = activeLanguageFilter
    ? books.filter((b) => b.language === activeLanguageFilter)
    : books

  const resolvedLanguageNames =
    Object.keys(languageNames).length > 0
      ? languageNames
      : Object.fromEntries(SEED_LANGUAGES.map((l) => [l.code, l.name]))

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Library</h1>
        <div className="flex items-center gap-2">
          <button onClick={handleImport} disabled={isImporting}>
            Import Book
          </button>
          <Link to="/settings">Settings</Link>
        </div>
      </div>

      {isImporting && <p>Importing…</p>}
      {importError && <p role="alert">{importError}</p>}

      {/* Language import dialog */}
      <Dialog open={langDialogOpen} onOpenChange={(open) => { if (!open) handleLangCancel() }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm language</DialogTitle>
          </DialogHeader>
          <p>Detected: {detectedLang ?? 'unknown'}</p>
          <select
            value={selectedLang}
            onChange={(e) => setSelectedLang(e.target.value)}
          >
            {SEED_LANGUAGES.map((l) => (
              <option key={l.code} value={l.code}>
                {l.name}
              </option>
            ))}
          </select>
          <DialogFooter>
            <button onClick={handleLangCancel}>Cancel</button>
            <button onClick={handleLangConfirm}>Confirm</button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation dialog */}
      {pendingDelete && (
        <Dialog open={true} onOpenChange={(open) => { if (!open) setPendingDelete(null) }}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Delete &quot;{pendingDelete.book.title}&quot;?</DialogTitle>
            </DialogHeader>
            <p>This cannot be undone.</p>
            <DialogFooter>
              <Button
                variant="outline"
                autoFocus
                onClick={() => setPendingDelete(null)}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={handleConfirmDelete}
              >
                Delete
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}

      {/* Empty state */}
      {books.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center text-muted-foreground">
          <p>No books yet — tap Import to add your first book</p>
        </div>
      )}

      {/* Continue Reading hero */}
      {books.length > 0 && lastOpenedBook && lastOpenedRow && (
        <ContinueReadingCard
          book={lastOpenedBook}
          chapterTitle={lastOpenedRow.sectionTitle ?? null}
          progressPct={computeProgressPct(
            lastOpenedRow.sectionIndex,
            sectionCountMap[lastOpenedBook.id] ?? 0,
          )}
          onResume={handleOpen}
        />
      )}

      {/* Language filter chips */}
      {showLanguageFilter && (
        <div className="flex gap-2 flex-wrap" role="group" aria-label="Filter by language">
          {distinctLanguages.map((code) => (
            <Button
              key={code}
              variant={activeLanguageFilter === code ? 'default' : 'outline'}
              size="sm"
              onClick={() =>
                setActiveLanguageFilter((prev) => (prev === code ? null : code))
              }
            >
              {resolvedLanguageNames[code] ?? code}
            </Button>
          ))}
        </div>
      )}

      {/* Empty filter state */}
      {books.length > 0 && filteredBooks.length === 0 && (
        <p className="text-sm text-muted-foreground text-center py-4">No books match this filter</p>
      )}

      {/* Book list */}
      {filteredBooks.length > 0 && (
        <div className="flex flex-col">
          {filteredBooks.map((book) => {
            const progressRow = progressMap[book.id]
            const totalSections = sectionCountMap[book.id] ?? 0
            const progressPct = progressRow
              ? computeProgressPct(progressRow.sectionIndex, totalSections)
              : 0

            return (
              <BookListItem
                key={book.id}
                book={book}
                progressPct={progressPct}
                languageName={resolvedLanguageNames[book.language] ?? book.language}
                onOpen={handleOpen}
                onRequestDelete={(b) => setPendingDelete({ book: b })}
              />
            )
          })}
        </div>
      )}
    </div>
  )
}
