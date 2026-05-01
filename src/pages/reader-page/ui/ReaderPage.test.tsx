import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { BookEntity } from '@/entities'

const book: BookEntity = {
  id: 'b1',
  title: 'Moby Dick',
  author: 'Herman Melville',
  fileName: 'moby-dick.epub',
  language: 'en',
  coverPath: null,
  createdAt: 1000,
}

vi.mock('@/shared/stores', () => ({
  useVaultStore: vi.fn(),
  useReaderStore: vi.fn(),
}))

import { ReaderPage } from './ReaderPage'
import { useVaultStore, useReaderStore } from '@/shared/stores'

function renderAtRoute(bookId: string) {
  return render(
    <MemoryRouter initialEntries={[`/reader/${bookId}`]}>
      <Routes>
        <Route path="/reader/:bookId" element={<ReaderPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ReaderPage', () => {
  it('renders the book title from the store', () => {
    vi.mocked(useVaultStore).mockImplementation((selector: (s: unknown) => unknown) =>
      selector({ books: [book] }) as never,
    )
    vi.mocked(useReaderStore).mockImplementation((selector: (s: unknown) => unknown) =>
      selector({ currentSectionId: 's1', tokenIndex: 5 }) as never,
    )

    renderAtRoute('b1')

    expect(screen.getByText('Moby Dick')).toBeInTheDocument()
    expect(screen.getByTestId('reader-stub')).toBeInTheDocument()
  })

  it('shows alert and back link when book is not found', () => {
    vi.mocked(useVaultStore).mockImplementation((selector: (s: unknown) => unknown) =>
      selector({ books: [] }) as never,
    )
    vi.mocked(useReaderStore).mockImplementation((selector: (s: unknown) => unknown) =>
      selector({ currentSectionId: null, tokenIndex: 0 }) as never,
    )

    renderAtRoute('unknown-id')

    expect(screen.getByRole('alert')).toHaveTextContent('Book not found')
    expect(screen.getByText('Back to library')).toBeInTheDocument()
  })
})
