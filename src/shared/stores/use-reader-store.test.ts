import { describe, it, expect, beforeEach } from 'vitest'
import { useReaderStore } from './use-reader-store'

describe('useReaderStore', () => {
  beforeEach(() => {
    useReaderStore.setState({
      bookId: null,
      currentSectionId: null,
      tokenIndex: 0,
    })
  })

  it('has correct initial state', () => {
    const state = useReaderStore.getState()
    expect(state.bookId).toBeNull()
    expect(state.currentSectionId).toBeNull()
    expect(state.tokenIndex).toBe(0)
  })

  it('setPosition updates bookId, currentSectionId, and tokenIndex', () => {
    useReaderStore.getState().setPosition({ bookId: 'b1', sectionId: 's1', tokenIndex: 42 })
    const state = useReaderStore.getState()
    expect(state.bookId).toBe('b1')
    expect(state.currentSectionId).toBe('s1')
    expect(state.tokenIndex).toBe(42)
  })

  it('clear resets all state to initial values', () => {
    useReaderStore.getState().setPosition({ bookId: 'b1', sectionId: 's1', tokenIndex: 5 })
    useReaderStore.getState().clear()
    const state = useReaderStore.getState()
    expect(state.bookId).toBeNull()
    expect(state.currentSectionId).toBeNull()
    expect(state.tokenIndex).toBe(0)
  })

  it('setPosition can be called multiple times, overwriting previous position', () => {
    useReaderStore.getState().setPosition({ bookId: 'b1', sectionId: 's1', tokenIndex: 10 })
    useReaderStore.getState().setPosition({ bookId: 'b2', sectionId: 's5', tokenIndex: 99 })
    const state = useReaderStore.getState()
    expect(state.bookId).toBe('b2')
    expect(state.currentSectionId).toBe('s5')
    expect(state.tokenIndex).toBe(99)
  })
})
