import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeft, ChevronRight, List } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useReaderStore } from '@/shared/stores'
import { WordToken } from './WordToken'
import { useReaderView } from '../model/use-reader-view'

// TODO: virtualize token rendering with @tanstack/react-virtual for sections > 3000 tokens
// See architecture.md "Gap Analysis — Virtualization" and widgets/reader-view/model/use-reader-view.ts

interface ReaderViewProps {
  bookTitle: string
  onChapterListOpen: () => void
}

export function ReaderView({ bookTitle, onChapterListOpen }: ReaderViewProps) {
  const tokens = useReaderStore((s) => s.tokens)
  const sections = useReaderStore((s) => s.sections)
  const currentSectionId = useReaderStore((s) => s.currentSectionId)
  const isChromeVisible = useReaderStore((s) => s.isChromeVisible)
  const toggleChrome = useReaderStore((s) => s.toggleChrome)
  const { navigateForward, navigatePrev, handlePointerDown, handlePointerUp } = useReaderView()

  const currentSectionIndex = sections.findIndex((s) => s.id === currentSectionId)
  const currentSection = sections[currentSectionIndex]
  const canGoNext = currentSectionIndex < sections.length - 1
  const canGoPrev = currentSectionIndex > 0

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
        <span className="flex-1 truncate text-sm font-medium">{bookTitle}</span>
      </header>

      <main
        className="flex-1 overflow-y-auto relative"
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onClick={toggleChrome}
      >
        <div
          key={currentSectionId ?? 'initial'}
          className="px-6 py-8"
          style={{ fontFamily: 'Georgia, serif', fontSize: '18px', lineHeight: 1.7, animation: 'readerFadeIn 80ms ease' }}
        >
          {tokens.map((token) => (
            <WordToken key={token.id} token={token} />
          ))}
        </div>
      </main>

      <footer
        className={[
          'flex items-center justify-between px-4 py-3 border-t bg-white transition-opacity duration-100',
          isChromeVisible ? 'opacity-100' : 'opacity-0 pointer-events-none',
        ].join(' ')}
      >
        <Button
          variant="ghost"
          size="icon"
          onClick={navigatePrev}
          disabled={!canGoPrev}
          aria-label="Previous section"
        >
          <ChevronLeft className="h-5 w-5" />
        </Button>

        <Button variant="ghost" className="flex-1 truncate text-sm" onClick={onChapterListOpen}>
          <List className="h-4 w-4 mr-2" />
          {currentSection?.title ?? `Chapter ${currentSectionIndex + 1}`}
          {' · '}
          {currentSectionIndex + 1} / {sections.length}
        </Button>

        <Button
          variant="ghost"
          size="icon"
          onClick={navigateForward}
          disabled={!canGoNext}
          aria-label="Next section"
        >
          <ChevronRight className="h-5 w-5" />
        </Button>
      </footer>
    </div>
  )
}
