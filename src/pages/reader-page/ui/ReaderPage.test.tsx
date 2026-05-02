import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { BookEntity } from '@/entities'
import type { SectionEntity } from '@/entities/section'
import type { TokenEntity } from '@/entities/token'

const book: BookEntity = {
  id: 'b1',
  title: 'Moby Dick',
  author: 'Herman Melville',
  fileName: 'moby-dick.epub',
  language: 'en',
  coverPath: null,
  createdAt: 1000,
}

const sections: SectionEntity[] = [
  { id: 's1', bookId: 'b1', index: 0, title: 'Chapter 1' },
  { id: 's2', bookId: 'b1', index: 1, title: 'Chapter 2' },
]

const tokens: TokenEntity[] = [
  { id: 't1', sectionId: 's1', index: 0, type: 'word', text: 'Hello', wordKey: 'hello' },
]

const mockSetSections = vi.fn()
const mockSetTokens = vi.fn()
const mockClear = vi.fn()
const mockSetPosition = vi.fn()
const mockOnConflictDoUpdate = vi.fn()
const mockValues = vi.fn()
const mockInsert = vi.fn()
const mockOrderBy = vi.fn()
const mockWhere = vi.fn()
const mockFrom = vi.fn()
const mockSelect = vi.fn()

vi.mock('@/shared/stores', () => ({
  useVaultStore: vi.fn(),
  useReaderStore: vi.fn(),
}))

vi.mock('@/shared/db', () => ({
  getDb: vi.fn(),
  schema: {
    sections: 'sections_table',
    tokens: 'tokens_table',
    readingProgress: 'reading_progress_table',
  },
}))

vi.mock('@/widgets/reader-view', () => ({
  ReaderView: ({ bookTitle, onChapterListOpen }: { bookTitle: string; onChapterListOpen: () => void }) => (
    <div data-testid="reader-view">
      <span>{bookTitle}</span>
      <button onClick={onChapterListOpen}>Open chapters</button>
    </div>
  ),
}))

import { ReaderPage } from './ReaderPage'
import { useVaultStore, useReaderStore } from '@/shared/stores'
import { getDb } from '@/shared/db'

type StoreState = {
  bookId: string | null
  currentSectionId: string | null
  tokenIndex: number
  sections: SectionEntity[]
  tokens: TokenEntity[]
  isChromeVisible: boolean
  setSections: typeof mockSetSections
  setTokens: typeof mockSetTokens
  setPosition: typeof mockSetPosition
  toggleChrome: ReturnType<typeof vi.fn>
  clear: typeof mockClear
}

function buildStoreState(overrides: Partial<StoreState> = {}): StoreState {
  return {
    bookId: 'b1',
    currentSectionId: null,
    tokenIndex: 0,
    sections: [],
    tokens: [],
    isChromeVisible: true,
    setSections: mockSetSections,
    setTokens: mockSetTokens,
    setPosition: mockSetPosition,
    toggleChrome: vi.fn(),
    clear: mockClear,
    ...overrides,
  }
}

function setupDb() {
  mockOnConflictDoUpdate.mockResolvedValue(undefined)
  mockValues.mockReturnValue({ onConflictDoUpdate: mockOnConflictDoUpdate })
  mockInsert.mockReturnValue({ values: mockValues })
  mockOrderBy.mockResolvedValue([])
  mockWhere.mockReturnValue({ orderBy: mockOrderBy })
  mockFrom.mockReturnValue({ where: mockWhere })
  mockSelect.mockReturnValue({ from: mockFrom })
  vi.mocked(getDb).mockReturnValue({ select: mockSelect, insert: mockInsert } as never)
}

function setupStores(storeOverrides: Partial<StoreState> = {}) {
  const state = buildStoreState(storeOverrides)
  vi.mocked(useVaultStore).mockImplementation(
    ((selector: (s: unknown) => unknown) => selector({ books: [book] })) as never,
  )
  vi.mocked(useReaderStore).mockImplementation(
    ((selector: (s: unknown) => unknown) => selector(state)) as never,
  )
  ;(vi.mocked(useReaderStore) as unknown as { getState: () => StoreState }).getState = vi.fn().mockReturnValue(state)
  return state
}

function renderAtRoute(bookId = 'b1') {
  return render(
    <MemoryRouter initialEntries={[`/reader/${bookId}`]}>
      <Routes>
        <Route path="/reader/:bookId" element={<ReaderPage />} />
        <Route path="/library" element={<div>library</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ReaderPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupDb()
  })

  it('shows "Book not found" alert when book is not in vault', async () => {
    setupStores({ currentSectionId: null })
    vi.mocked(useVaultStore).mockImplementation(
      ((selector: (s: unknown) => unknown) => selector({ books: [] })) as never,
    )

    await act(async () => { renderAtRoute('unknown-id') })
    expect(screen.getByRole('alert')).toHaveTextContent('Book not found')
    expect(screen.getByText('Back to library')).toBeInTheDocument()
  })

  it('shows loading state initially before tokens resolve', async () => {
    let resolveTokens!: (v: TokenEntity[]) => void
    const tokenPromise = new Promise<TokenEntity[]>((res) => { resolveTokens = res })

    setupStores({ currentSectionId: 's1' })
    mockOrderBy
      .mockResolvedValueOnce(sections)
      .mockReturnValueOnce(tokenPromise)

    const { unmount } = render(
      <MemoryRouter initialEntries={['/reader/b1']}>
        <Routes>
          <Route path="/reader/:bookId" element={<ReaderPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Loading…')).toBeInTheDocument()

    await act(async () => { resolveTokens(tokens) })
    unmount()
  })

  it('loads sections on mount via setSections', async () => {
    setupStores({ currentSectionId: null })
    mockOrderBy.mockResolvedValue(sections)

    await act(async () => { renderAtRoute() })
    expect(mockSetSections).toHaveBeenCalledWith(sections)
  })

  it('loads tokens when currentSectionId is set via setTokens', async () => {
    setupStores({ currentSectionId: 's1' })
    mockOrderBy.mockResolvedValueOnce(sections).mockResolvedValueOnce(tokens)

    await act(async () => { renderAtRoute() })
    expect(mockSetTokens).toHaveBeenCalledWith(tokens)
  })

  it('saves position after token load (onConflictDoUpdate called)', async () => {
    setupStores({ currentSectionId: 's1' })
    mockOrderBy.mockResolvedValueOnce(sections).mockResolvedValueOnce(tokens)

    await act(async () => { renderAtRoute() })
    expect(mockOnConflictDoUpdate).toHaveBeenCalled()
  })

  it('calls clear on unmount', async () => {
    setupStores({ currentSectionId: null })
    mockOrderBy.mockResolvedValue([])

    const { unmount } = await act(async () => renderAtRoute())
    unmount()
    expect(mockClear).toHaveBeenCalled()
  })

  it('flushes position on visibilitychange hidden', async () => {
    setupStores({ currentSectionId: 's1' })
    mockOrderBy.mockResolvedValue([])

    await act(async () => { renderAtRoute() })

    mockOnConflictDoUpdate.mockClear()

    await act(async () => {
      Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
      document.dispatchEvent(new Event('visibilitychange'))
    })

    expect(mockOnConflictDoUpdate).toHaveBeenCalled()

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  it('chapter list dialog opens and shows sections', async () => {
    const storeState = buildStoreState({ sections, currentSectionId: 's1' })
    vi.mocked(useReaderStore).mockImplementation(
      ((selector: (s: unknown) => unknown) => selector(storeState)) as never,
    )
    ;(vi.mocked(useReaderStore) as unknown as { getState: () => StoreState }).getState = vi.fn().mockReturnValue(storeState)
    vi.mocked(useVaultStore).mockImplementation(
      ((selector: (s: unknown) => unknown) => selector({ books: [book] })) as never,
    )
    mockOrderBy.mockResolvedValueOnce(sections).mockResolvedValueOnce(tokens)

    await act(async () => { renderAtRoute() })

    fireEvent.click(screen.getByText('Open chapters'))

    expect(screen.getByText('Chapters')).toBeInTheDocument()
    expect(screen.getByText('Chapter 1')).toBeInTheDocument()
    expect(screen.getByText('Chapter 2')).toBeInTheDocument()
  })

  it('jumping to chapter calls setPosition with that sectionId', async () => {
    const storeState = buildStoreState({ sections, currentSectionId: 's1' })
    vi.mocked(useReaderStore).mockImplementation(
      ((selector: (s: unknown) => unknown) => selector(storeState)) as never,
    )
    ;(vi.mocked(useReaderStore) as unknown as { getState: () => StoreState }).getState = vi.fn().mockReturnValue(storeState)
    vi.mocked(useVaultStore).mockImplementation(
      ((selector: (s: unknown) => unknown) => selector({ books: [book] })) as never,
    )
    mockOrderBy.mockResolvedValueOnce(sections).mockResolvedValueOnce(tokens)

    await act(async () => { renderAtRoute() })
    fireEvent.click(screen.getByText('Open chapters'))

    const chapter2Btn = screen.getByRole('button', { name: 'Chapter 2' })
    fireEvent.click(chapter2Btn)
    expect(mockSetPosition).toHaveBeenCalledWith({ bookId: 'b1', sectionId: 's2', tokenIndex: 0 })
  })
})
