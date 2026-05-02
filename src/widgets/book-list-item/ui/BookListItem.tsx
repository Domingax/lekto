import { Trash2 } from 'lucide-react'
import type { BookEntity } from '@/entities'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

interface BookListItemProps {
  book: BookEntity
  progressPct: number
  languageName: string
  onOpen: (id: string) => void
  onRequestDelete: (book: BookEntity) => void
}

export function BookListItem({ book, progressPct, languageName, onOpen, onRequestDelete }: Readonly<BookListItemProps>) {
  return (
    <div
      role="button"
      tabIndex={0}
      aria-label={`Open ${book.title}`}
      className="flex items-center gap-3 min-h-[48px] px-3 py-2 cursor-pointer hover:bg-muted/50 rounded-md"
      onClick={() => onOpen(book.id)}
      onContextMenu={(e) => { e.preventDefault(); onRequestDelete(book) }}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpen(book.id) } }}
    >
      <div className="flex-shrink-0 w-10 h-14 bg-muted rounded flex items-center justify-center text-sm font-semibold text-muted-foreground select-none">
        {book.title.charAt(0).toUpperCase()}
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-base font-semibold leading-tight truncate">{book.title}</p>
        {book.author && (
          <p className="text-sm text-muted-foreground truncate">{book.author}</p>
        )}
      </div>

      <div className="flex items-center gap-2 flex-shrink-0">
        <Badge variant="secondary">{languageName}</Badge>
        <span className="text-sm text-muted-foreground w-10 text-right">{progressPct}%</span>
        <Button
          variant="ghost"
          size="icon"
          aria-label={`Delete ${book.title}`}
          onClick={(e) => {
            e.stopPropagation()
            onRequestDelete(book)
          }}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}
