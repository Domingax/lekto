import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { SectionEntity } from '@/entities/section'
import type { TokenEntity } from '@/entities/token'

const s1: SectionEntity = { id: 's1', bookId: 'b1', index: 0, title: 'Chapter 1' }
const s2: SectionEntity = { id: 's2', bookId: 'b1', index: 1, title: 'Chapter 2' }
const s3: SectionEntity = { id: 's3', bookId: 'b1', index: 2, title: 'Chapter 3' }

const tokens: TokenEntity[] = [
  { id: 't1', sectionId: 's2', index: 0, type: 'word', text: 'Hello', wordKey: 'hello' },
  { id: 't2', sectionId: 's2', index: 1, type: 'whitespace', text: ' ', wordKey: null },
  { id: 't3', sectionId: 's2', index: 2, type: 'word', text: 'World', wordKey: 'world' },
]

const mockToggleChrome = vi.fn()
const mockNavigateForward = vi.fn()
const mockNavigatePrev = vi.fn()

vi.mock('@/shared/stores', () => ({
  useReaderStore: vi.fn(),
}))

vi.mock('../model/use-reader-view', () => ({
  useReaderView: () => ({
    navigateForward: mockNavigateForward,
    navigatePrev: mockNavigatePrev,
    handlePointerDown: vi.fn(),
    handlePointerUp: vi.fn(),
  }),
}))

import { useReaderStore } from '@/shared/stores'
import { ReaderView } from './ReaderView'

function setupStore(overrides: Partial<{
  tokens: TokenEntity[]
  sections: SectionEntity[]
  currentSectionId: string
  isChromeVisible: boolean
  bookId: string
}> = {}) {
  const defaults = {
    tokens,
    sections: [s1, s2, s3],
    currentSectionId: 's2',
    isChromeVisible: true,
    bookId: 'b1',
    toggleChrome: mockToggleChrome,
  }
  vi.mocked(useReaderStore).mockImplementation(
    ((selector: (s: unknown) => unknown) => selector({ ...defaults, ...overrides })) as never,
  )
}

function renderReaderView(props: { bookTitle?: string } = {}) {
  return render(
    <MemoryRouter>
      <ReaderView bookTitle={props.bookTitle ?? 'Test Book'} onChapterListOpen={vi.fn()} />
    </MemoryRouter>,
  )
}

describe('ReaderView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders all word tokens', () => {
    setupStore()
    renderReaderView()
    expect(screen.getByText('Hello')).toBeInTheDocument()
    expect(screen.getByText('World')).toBeInTheDocument()
  })

  it('shows chrome (top and bottom bars) when isChromeVisible is true', () => {
    setupStore({ isChromeVisible: true })
    renderReaderView()
    const header = screen.getByRole('banner')
    expect(header).toHaveClass('opacity-100')
  })

  it('hides chrome when isChromeVisible is false', () => {
    setupStore({ isChromeVisible: false })
    renderReaderView()
    const header = screen.getByRole('banner')
    expect(header).toHaveClass('opacity-0')
  })

  it('calls toggleChrome when main area is clicked', () => {
    setupStore()
    renderReaderView()
    fireEvent.click(screen.getByRole('main'))
    expect(mockToggleChrome).toHaveBeenCalled()
  })

  it('displays section count in bottom bar: "2 / 3" for index 1 of 3', () => {
    setupStore({ currentSectionId: 's2', sections: [s1, s2, s3] })
    renderReaderView()
    expect(screen.getByText(/2 \/ 3/)).toBeInTheDocument()
  })

  it('prev button is disabled at first section', () => {
    setupStore({ currentSectionId: 's1', sections: [s1, s2, s3] })
    renderReaderView()
    expect(screen.getByLabelText('Previous section')).toBeDisabled()
  })

  it('next button is disabled at last section', () => {
    setupStore({ currentSectionId: 's3', sections: [s1, s2, s3] })
    renderReaderView()
    expect(screen.getByLabelText('Next section')).toBeDisabled()
  })

  it('ArrowRight key triggers navigateForward', () => {
    setupStore()
    renderReaderView()
    fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(mockNavigateForward).toHaveBeenCalled()
  })

  it('ArrowLeft key triggers navigatePrev', () => {
    setupStore()
    renderReaderView()
    fireEvent.keyDown(window, { key: 'ArrowLeft' })
    expect(mockNavigatePrev).toHaveBeenCalled()
  })
})
