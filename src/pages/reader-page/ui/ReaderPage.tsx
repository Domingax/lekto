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
