import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ok, err } from 'neverthrow'
import type { BookEntity } from '@/entities'

// --- Module mocks ---

vi.mock('@/shared/platform', () => ({
  filePickerAdapter: { pickFile: vi.fn() },
}))

vi.mock('@/features/import-book', () => ({
  importBook: vi.fn(),
}))

vi.mock('@/features/delete-book', () => ({
  deleteBook: vi.fn(),
}))

vi.mock('drizzle-orm', () => ({
  eq: vi.fn((_a: unknown, _b: unknown) => 'mock-eq'),
  sql: vi.fn(),
}))

vi.mock('@/shared/db', () => ({
  getDb: vi.fn(),
  schema: {
    readingProgress: 'readingProgress',
    sections: 'sections',
    languages: 'languages',
    books: 'books',
  },
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

// Stable state object to avoid new references on every render
let storeState = {
  books: [] as BookEntity[],
  vaultPath: '/vault' as string | null,
  isVaultReady: true,
  setVaultPath: vi.fn(),
  clearVault: vi.fn(),
  setBooks: vi.fn(),
  addBook: vi.fn(),
  removeBook: vi.fn(),
}

vi.mock('@/shared/stores', () => ({
  useVaultStore: vi.fn((selector: (s: unknown) => unknown) => selector(storeState)),
  useReaderStore: {
    getState: vi.fn(() => ({ setPosition: vi.fn() })),
  },
}))

import { LibraryPage } from './LibraryPage'
import { filePickerAdapter } from '@/shared/platform'
import { importBook } from '@/features/import-book'
import { deleteBook } from '@/features/delete-book'
import { useReaderStore } from '@/shared/stores'
import { getDb } from '@/shared/db'

const book1: BookEntity = { id: '1', title: 'Dune', author: 'Frank Herbert', fileName: 'dune.epub', language: 'en', coverPath: null, createdAt: 1000 }
const book2: BookEntity = { id: '2', title: 'Fondation', author: null, fileName: 'fondation.epub', language: 'fr', coverPath: null, createdAt: 2000 }
const book3: BookEntity = { id: '3', title: 'Neuromancer', author: 'William Gibson', fileName: 'neuromancer.epub', language: 'en', coverPath: null, createdAt: 3000 }

function makeDbMock(opts: {
  progressRows?: unknown[]
  sectionCounts?: unknown[]
  readingProgressForOpen?: unknown[]
  firstSection?: unknown[]
} = {}) {
  const progressRows = opts.progressRows ?? []
  const sectionCounts = opts.sectionCounts ?? []
  const languageRows = [{ code: 'en', name: 'English' }, { code: 'fr', name: 'French' }]
  const readingProgressForOpen = opts.readingProgressForOpen ?? []
  const firstSection = opts.firstSection ?? []

  const db = {
    select: vi.fn().mockReturnValue({
      from: vi.fn((table: string) => {
        if (table === 'languages') {
          return Promise.resolve(languageRows)
        }
        if (table === 'readingProgress') {
          return {
            innerJoin: vi.fn(() => Promise.resolve(progressRows)),
            where: vi.fn(() => Promise.resolve(readingProgressForOpen)),
          }
        }
        if (table === 'sections') {
          return {
            groupBy: vi.fn(() => Promise.resolve(sectionCounts)),
            where: vi.fn(() => ({
              orderBy: vi.fn(() => ({
                limit: vi.fn(() => Promise.resolve(firstSection)),
              })),
            })),
          }
        }
        return Promise.resolve([])
      }),
    }),
    insert: vi.fn().mockReturnValue({
      values: vi.fn().mockReturnValue({
        onConflictDoNothing: vi.fn().mockResolvedValue(undefined),
      }),
    }),
    delete: vi.fn().mockReturnValue({
      where: vi.fn().mockResolvedValue(undefined),
    }),
  }
  return db
}

function renderPage() {
  return render(
    <MemoryRouter>
      <LibraryPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  storeState = {
    books: [],
    vaultPath: '/vault',
    isVaultReady: true,
    setVaultPath: vi.fn(),
    clearVault: vi.fn(),
    setBooks: vi.fn(),
    addBook: vi.fn(),
    removeBook: vi.fn(),
  }
  vi.mocked(useReaderStore).getState.mockReturnValue({ setPosition: vi.fn() } as never)
})

describe('LibraryPage — import flow', () => {
  it('renders the import button', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /import book/i })).toBeInTheDocument()
  })

  it('calls file picker with correct accept array', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(err('cancelled'))
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    await waitFor(() => expect(filePickerAdapter.pickFile).toHaveBeenCalled())
    expect(filePickerAdapter.pickFile).toHaveBeenCalledWith({ accept: ['.epub', '.pdf', '.txt'] })
  })

  it('shows progress paragraph while importBook is pending', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(ok({ data: new ArrayBuffer(8), name: 'test.epub' }))
    vi.mocked(importBook).mockReturnValue(new Promise(() => {}))
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    await waitFor(() => expect(screen.getByText('Importing…')).toBeInTheDocument())
  })

  it('shows inline error when importBook returns err', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(ok({ data: new ArrayBuffer(8), name: 'test.epub' }))
    vi.mocked(importBook).mockResolvedValue(err('Failed to parse EPUB'))
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Failed to parse EPUB'))
  })

  it('language dialog opens when importBook calls resolveLanguage', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(ok({ data: new ArrayBuffer(8), name: 'test.epub' }))
    vi.mocked(importBook).mockImplementation((_data, _name, resolveLanguage) => {
      return new Promise((resolve) => {
        resolveLanguage('en').then((lang) => {
          if (lang) resolve(ok({ id: '1', title: 'T', author: null, fileName: 'test.epub', language: lang, coverPath: null, createdAt: 1 }))
          else resolve(err('Import cancelled'))
        })
      })
    })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument())
  })
})

describe('LibraryPage — empty state', () => {
  it('shows empty state message when no books', () => {
    renderPage()
    expect(screen.getByText(/No books yet/)).toBeInTheDocument()
  })

  it('does not show Continue Reading when no books', () => {
    renderPage()
    expect(screen.queryByText(/continue reading/i)).not.toBeInTheDocument()
  })
})

describe('LibraryPage — book list rendering', () => {
  it('renders books via BookListItem after hydration', async () => {
    storeState.books = [book1]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)
    renderPage()
    await waitFor(() => expect(screen.getByText('Dune')).toBeInTheDocument())
  })

  it('does not show empty state when books exist', async () => {
    storeState.books = [book1]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)
    renderPage()
    await waitFor(() => expect(screen.queryByText(/No books yet/)).not.toBeInTheDocument())
  })
})

describe('LibraryPage — Continue Reading hero', () => {
  it('shows Continue Reading when a reading_progress row exists', async () => {
    storeState.books = [book1]
    vi.mocked(getDb).mockReturnValue(makeDbMock({
      progressRows: [{ bookId: '1', sectionId: 's1', sectionIndex: 2, sectionTitle: 'Chapter 2', updatedAt: 5000 }],
      sectionCounts: [{ bookId: '1', count: 10 }],
    }) as never)

    renderPage()
    await waitFor(() => expect(screen.getByText(/continue reading/i)).toBeInTheDocument())
  })

  it('hides Continue Reading when no reading_progress rows exist', async () => {
    storeState.books = [book1]
    vi.mocked(getDb).mockReturnValue(makeDbMock({ progressRows: [] }) as never)

    renderPage()
    await waitFor(() => expect(screen.getByText('Dune')).toBeInTheDocument())
    expect(screen.queryByText(/continue reading/i)).not.toBeInTheDocument()
  })
})

describe('LibraryPage — language filter chips', () => {
  it('hides language chips when all books are same language', async () => {
    storeState.books = [book1, book3]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)

    renderPage()
    await waitFor(() => expect(screen.getByText('Dune')).toBeInTheDocument())
    expect(screen.queryByRole('group', { name: /filter by language/i })).not.toBeInTheDocument()
  })

  it('shows language chips when books span ≥2 languages', async () => {
    storeState.books = [book1, book2]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)

    renderPage()
    await waitFor(() => expect(screen.getByRole('group', { name: /filter by language/i })).toBeInTheDocument())
  })

  it('filters book list when a language chip is clicked', async () => {
    storeState.books = [book1, book2]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)

    renderPage()
    await waitFor(() => screen.getByRole('group', { name: /filter by language/i }))

    fireEvent.click(screen.getByRole('button', { name: 'French' }))

    await waitFor(() => {
      expect(screen.getByText('Fondation')).toBeInTheDocument()
      expect(screen.queryByText('Dune')).not.toBeInTheDocument()
    })
  })

  it('clears filter when active chip is clicked again', async () => {
    storeState.books = [book1, book2]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)

    renderPage()
    await waitFor(() => screen.getByRole('group', { name: /filter by language/i }))

    fireEvent.click(screen.getByRole('button', { name: 'French' }))
    await waitFor(() => expect(screen.queryByText('Dune')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'French' }))
    await waitFor(() => expect(screen.getByText('Dune')).toBeInTheDocument())
  })
})

describe('LibraryPage — delete flow', () => {
  it('opens delete dialog when trash button is clicked', async () => {
    storeState.books = [book1]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)

    renderPage()
    await waitFor(() => screen.getByText('Dune'))

    fireEvent.click(screen.getByRole('button', { name: /delete dune/i }))
    await waitFor(() => expect(screen.getByText(/Delete "Dune"/)).toBeInTheDocument())
  })

  it('calls deleteBook when Delete is confirmed', async () => {
    storeState.books = [book1]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)
    vi.mocked(deleteBook).mockResolvedValue(ok(undefined))

    renderPage()
    await waitFor(() => screen.getByText('Dune'))

    fireEvent.click(screen.getByRole('button', { name: /delete dune/i }))
    await waitFor(() => screen.getByText(/Delete "Dune"/))

    fireEvent.click(screen.getByRole('button', { name: /^delete$/i }))

    await waitFor(() => expect(deleteBook).toHaveBeenCalledWith('1'))
  })

  it('does NOT call deleteBook when Cancel is clicked', async () => {
    storeState.books = [book1]
    vi.mocked(getDb).mockReturnValue(makeDbMock() as never)

    renderPage()
    await waitFor(() => screen.getByText('Dune'))

    fireEvent.click(screen.getByRole('button', { name: /delete dune/i }))
    await waitFor(() => screen.getByText(/Delete "Dune"/))

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))

    await waitFor(() => expect(screen.queryByText(/Delete "Dune"/)).not.toBeInTheDocument())
    expect(deleteBook).not.toHaveBeenCalled()
  })
})

describe('LibraryPage — open book flow', () => {
  it('navigates to /reader/:bookId when book has existing progress', async () => {
    storeState.books = [book1]
    vi.mocked(getDb).mockReturnValue(makeDbMock({
      readingProgressForOpen: [{ sectionId: 's1', tokenIndex: 3 }],
    }) as never)
    const setPosition = vi.fn()
    vi.mocked(useReaderStore).getState.mockReturnValue({ setPosition } as never)

    renderPage()
    await waitFor(() => screen.getByText('Dune'))

    fireEvent.click(screen.getByText('Dune'))

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/reader/1'))
    expect(setPosition).toHaveBeenCalledWith({ bookId: '1', sectionId: 's1', tokenIndex: 3 })
  })

  it('inserts reading_progress and navigates when book has no prior progress', async () => {
    storeState.books = [book1]
    const db = makeDbMock({
      readingProgressForOpen: [],
      firstSection: [{ id: 's0', index: 0, title: 'Intro', bookId: '1' }],
    })
    vi.mocked(getDb).mockReturnValue(db as never)
    const setPosition = vi.fn()
    vi.mocked(useReaderStore).getState.mockReturnValue({ setPosition } as never)

    renderPage()
    await waitFor(() => screen.getByText('Dune'))

    fireEvent.click(screen.getByText('Dune'))

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/reader/1'))
    expect(setPosition).toHaveBeenCalledWith({ bookId: '1', sectionId: 's0', tokenIndex: 0 })
    expect(db.insert).toHaveBeenCalled()
  })
})
