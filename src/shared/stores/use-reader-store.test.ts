import { describe, it, expect, beforeEach } from 'vitest'
import { useReaderStore } from './use-reader-store'
import type { SectionEntity } from '@/entities/section'
import type { TokenEntity } from '@/entities/token'

const section1: SectionEntity = { id: 's1', bookId: 'b1', index: 0, title: 'Chapter 1' }
const section2: SectionEntity = { id: 's2', bookId: 'b1', index: 1, title: null }

const token1: TokenEntity = { id: 't1', sectionId: 's1', index: 0, type: 'word', text: 'Hello', wordKey: 'hello' }
const token2: TokenEntity = { id: 't2', sectionId: 's1', index: 1, type: 'whitespace', text: ' ', wordKey: null }

describe('useReaderStore', () => {
  beforeEach(() => {
    useReaderStore.setState({
      bookId: null,
      currentSectionId: null,
      tokenIndex: 0,
      sections: [],
      tokens: [],
      isChromeVisible: true,
    })
  })

  it('has correct initial state', () => {
    const state = useReaderStore.getState()
    expect(state.bookId).toBeNull()
    expect(state.currentSectionId).toBeNull()
    expect(state.tokenIndex).toBe(0)
    expect(state.sections).toEqual([])
    expect(state.tokens).toEqual([])
    expect(state.isChromeVisible).toBe(true)
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
    useReaderStore.getState().setSections([section1])
    useReaderStore.getState().setTokens([token1])
    useReaderStore.getState().toggleChrome()
    useReaderStore.getState().clear()
    const state = useReaderStore.getState()
    expect(state.bookId).toBeNull()
    expect(state.currentSectionId).toBeNull()
    expect(state.tokenIndex).toBe(0)
    expect(state.sections).toEqual([])
    expect(state.tokens).toEqual([])
    expect(state.isChromeVisible).toBe(true)
  })

  it('setPosition can be called multiple times, overwriting previous position', () => {
    useReaderStore.getState().setPosition({ bookId: 'b1', sectionId: 's1', tokenIndex: 10 })
    useReaderStore.getState().setPosition({ bookId: 'b2', sectionId: 's5', tokenIndex: 99 })
    const state = useReaderStore.getState()
    expect(state.bookId).toBe('b2')
    expect(state.currentSectionId).toBe('s5')
    expect(state.tokenIndex).toBe(99)
  })

  it('setSections sets the sections array', () => {
    useReaderStore.getState().setSections([section1, section2])
    expect(useReaderStore.getState().sections).toEqual([section1, section2])
  })

  it('setSections replaces previous sections', () => {
    useReaderStore.getState().setSections([section1])
    useReaderStore.getState().setSections([section2])
    expect(useReaderStore.getState().sections).toEqual([section2])
  })

  it('setTokens sets the tokens array', () => {
    useReaderStore.getState().setTokens([token1, token2])
    expect(useReaderStore.getState().tokens).toEqual([token1, token2])
  })

  it('toggleChrome flips isChromeVisible from true to false', () => {
    expect(useReaderStore.getState().isChromeVisible).toBe(true)
    useReaderStore.getState().toggleChrome()
    expect(useReaderStore.getState().isChromeVisible).toBe(false)
  })

  it('toggleChrome flips isChromeVisible from false to true', () => {
    useReaderStore.getState().toggleChrome()
    useReaderStore.getState().toggleChrome()
    expect(useReaderStore.getState().isChromeVisible).toBe(true)
  })
})
