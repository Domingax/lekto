import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { eq } from 'drizzle-orm'
import { useReaderStore, useVaultStore } from '@/shared/stores'
import { getDb, schema } from '@/shared/db'
import type { TokenEntity } from '@/entities/token'
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
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    if (!bookId) navigate('/library')
  }, [bookId, navigate])

  useEffect(() => {
    if (!bookId) return
    const id = bookId
    let cancelled = false
    async function loadSections() {
      const db = getDb()
      const sectionRows = await db
        .select()
        .from(schema.sections)
        .where(eq(schema.sections.bookId, id))
        .orderBy(schema.sections.index)
      if (cancelled) return
      setSections(sectionRows)
      // Restore position from DB when StrictMode's clear() wiped currentSectionId
      if (!useReaderStore.getState().currentSectionId) {
        const progressRows = await db
          .select()
          .from(schema.readingProgress)
          .where(eq(schema.readingProgress.bookId, id))
          .orderBy(schema.readingProgress.updatedAt)
        if (cancelled) return
        const progress = progressRows[0]
        const sectionId = progress?.sectionId ?? sectionRows[0]?.id
        if (sectionId) {
          useReaderStore.getState().setPosition({ bookId: id, sectionId, tokenIndex: progress?.tokenIndex ?? 0 })
        }
      }
    }
    loadSections()
    return () => { cancelled = true }
  }, [bookId, setSections])

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
    const sectionId = currentSectionId
    let cancelled = false
    async function loadTokens() {
      setIsLoading(true)
      const db = getDb()
      const rows = await db
        .select()
        .from(schema.tokens)
        .where(eq(schema.tokens.sectionId, sectionId))
        .orderBy(schema.tokens.index)
      if (!cancelled) {
        setTokens(rows as TokenEntity[])
        setIsLoading(false)
      }
    }
    loadTokens()
    savePosition()
    return () => { cancelled = true }
  }, [currentSectionId, setTokens, savePosition])

  useEffect(() => {
    function onVisibilityChange() {
      if (document.visibilityState === 'hidden') savePosition()
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [savePosition])

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
