import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import type { SectionEntity } from '@/entities/section'

const s1: SectionEntity = { id: 's1', bookId: 'b1', index: 0, title: 'Chapter 1' }
const s2: SectionEntity = { id: 's2', bookId: 'b1', index: 1, title: 'Chapter 2' }
const s3: SectionEntity = { id: 's3', bookId: 'b1', index: 2, title: 'Chapter 3' }

const mockSetPosition = vi.fn()

vi.mock('@/shared/stores', () => ({
  useReaderStore: Object.assign(
    vi.fn(),
    {
      getState: vi.fn(),
    }
  ),
}))

import { useReaderStore } from '@/shared/stores'
import { useReaderView } from './use-reader-view'

function setupStore(currentSectionId: string) {
  vi.mocked(useReaderStore.getState).mockReturnValue({
    sections: [s1, s2, s3],
    currentSectionId,
    bookId: 'b1',
    setPosition: mockSetPosition,
    tokenIndex: 0,
    tokens: [],
    isChromeVisible: true,
    setSections: vi.fn(),
    setTokens: vi.fn(),
    toggleChrome: vi.fn(),
    clear: vi.fn(),
  })
}

describe('useReaderView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('navigateForward — middle section calls setPosition with next section', () => {
    setupStore('s2')
    const { result } = renderHook(() => useReaderView())
    act(() => result.current.navigateForward())
    expect(mockSetPosition).toHaveBeenCalledWith({ bookId: 'b1', sectionId: 's3', tokenIndex: 0 })
  })

  it('navigateForward — last section does NOT call setPosition', () => {
    setupStore('s3')
    const { result } = renderHook(() => useReaderView())
    act(() => result.current.navigateForward())
    expect(mockSetPosition).not.toHaveBeenCalled()
  })

  it('navigatePrev — middle section calls setPosition with previous section', () => {
    setupStore('s2')
    const { result } = renderHook(() => useReaderView())
    act(() => result.current.navigatePrev())
    expect(mockSetPosition).toHaveBeenCalledWith({ bookId: 'b1', sectionId: 's1', tokenIndex: 0 })
  })

  it('navigatePrev — first section does NOT call setPosition', () => {
    setupStore('s1')
    const { result } = renderHook(() => useReaderView())
    act(() => result.current.navigatePrev())
    expect(mockSetPosition).not.toHaveBeenCalled()
  })

  it('swipe left (deltaX = -60) triggers navigateForward', () => {
    setupStore('s2')
    const { result } = renderHook(() => useReaderView())
    act(() => {
      result.current.handlePointerDown({ clientX: 300 } as React.PointerEvent)
      result.current.handlePointerUp({ clientX: 240 } as React.PointerEvent)
    })
    expect(mockSetPosition).toHaveBeenCalledWith({ bookId: 'b1', sectionId: 's3', tokenIndex: 0 })
  })

  it('swipe right (deltaX = +60) triggers navigatePrev', () => {
    setupStore('s2')
    const { result } = renderHook(() => useReaderView())
    act(() => {
      result.current.handlePointerDown({ clientX: 200 } as React.PointerEvent)
      result.current.handlePointerUp({ clientX: 260 } as React.PointerEvent)
    })
    expect(mockSetPosition).toHaveBeenCalledWith({ bookId: 'b1', sectionId: 's1', tokenIndex: 0 })
  })

  it('tap (deltaX = 10) does not trigger navigation', () => {
    setupStore('s2')
    const { result } = renderHook(() => useReaderView())
    act(() => {
      result.current.handlePointerDown({ clientX: 200 } as React.PointerEvent)
      result.current.handlePointerUp({ clientX: 210 } as React.PointerEvent)
    })
    expect(mockSetPosition).not.toHaveBeenCalled()
  })
})
