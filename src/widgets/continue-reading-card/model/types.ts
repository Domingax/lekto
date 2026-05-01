import type { BookEntity } from '@/entities'

export interface ContinueReadingCardProps {
  book: BookEntity
  chapterTitle: string | null
  progressPct: number
  onResume: (id: string) => void
}
