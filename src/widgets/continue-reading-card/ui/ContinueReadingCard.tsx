import { Button } from '@/components/ui/button'
import type { ContinueReadingCardProps } from '../model/types'

export function ContinueReadingCard({ book, chapterTitle, progressPct, onResume }: ContinueReadingCardProps) {
  return (
    <div
      className="flex items-center gap-4 w-full min-h-[96px] px-4 py-3 bg-amber-50 border border-amber-200 rounded-lg cursor-pointer"
      onClick={() => onResume(book.id)}
    >
      <div className="flex-shrink-0 w-16 h-22 bg-amber-200 rounded flex items-center justify-center text-xl font-bold text-amber-800 select-none">
        {book.title.charAt(0).toUpperCase()}
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-xs uppercase tracking-wide text-amber-700 font-medium">Continue reading</p>
        <p className="text-lg font-semibold truncate">{book.title}</p>
        {chapterTitle && (
          <p className="text-sm text-muted-foreground truncate">{chapterTitle}</p>
        )}
        <p className="text-sm text-muted-foreground">{progressPct}%</p>
      </div>

      <Button
        variant="default"
        size="sm"
        onClick={(e) => {
          e.stopPropagation()
          onResume(book.id)
        }}
      >
        Resume
      </Button>
    </div>
  )
}
