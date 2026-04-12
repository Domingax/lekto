import { describe, it, expect } from 'vitest'
import { tokenizeSection } from './tokenize-content'

describe('tokenizeSection', () => {
  it('produces correct tokens for "Hello, world!" with expected IDs', () => {
    const tokens = tokenizeSection('Hello, world!', 'book1_0')
    expect(tokens[0]).toMatchObject({ id: 'book1_0_0', type: 'word', text: 'Hello', wordKey: 'hello' })
    expect(tokens[1]).toMatchObject({ id: 'book1_0_1', type: 'punctuation', text: ',', wordKey: null })
    expect(tokens[2]).toMatchObject({ id: 'book1_0_2', type: 'whitespace', wordKey: null })
    expect(tokens[3]).toMatchObject({ id: 'book1_0_3', type: 'word', text: 'world', wordKey: 'world' })
    expect(tokens[4]).toMatchObject({ id: 'book1_0_4', type: 'punctuation', text: '!', wordKey: null })
  })

  it('indices are sequential with no gaps', () => {
    const tokens = tokenizeSection('Hello, world!', 'book1_0')
    tokens.forEach((t, i) => {
      expect(t.index).toBe(i)
    })
  })

  it('sectionId is set on all tokens', () => {
    const tokens = tokenizeSection('Hello!', 'mySection')
    tokens.forEach((t) => expect(t.sectionId).toBe('mySection'))
  })

  it('filters out empty text tokens', () => {
    const tokens = tokenizeSection('', 'book1_0')
    expect(tokens).toHaveLength(0)
  })
})
