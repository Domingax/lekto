import { describe, it, expect } from 'vitest'
import { latinTokenizer } from './latin.tokenizer'

describe('latinTokenizer', () => {
  it('tokenizes "Hello, world!" into correct types', () => {
    const tokens = latinTokenizer.tokenize('Hello, world!')
    const words = tokens.filter((t) => t.type === 'word')
    const punct = tokens.filter((t) => t.type === 'punctuation')
    const ws = tokens.filter((t) => t.type === 'whitespace')
    expect(words).toHaveLength(2)
    expect(punct).toHaveLength(2) // ',' and '!'
    expect(ws).toHaveLength(1)
  })

  it('preserves diacritics in wordKey — café → café', () => {
    const tokens = latinTokenizer.tokenize('café')
    expect(tokens).toHaveLength(1)
    expect(tokens[0]).toMatchObject({ type: 'word', text: 'café', wordKey: 'café' })
  })

  it('lowercases wordKey — Café → café', () => {
    const tokens = latinTokenizer.tokenize('Café')
    expect(tokens[0]).toMatchObject({ wordKey: 'café' })
  })

  it('lowercases all word keys in "Café au lait"', () => {
    const tokens = latinTokenizer.tokenize('Café au lait')
    const words = tokens.filter((t) => t.type === 'word')
    expect(words.map((w) => w.wordKey)).toEqual(['café', 'au', 'lait'])
  })

  it('handles "Hello world" with correct whitespace token', () => {
    const tokens = latinTokenizer.tokenize('Hello world')
    expect(tokens).toHaveLength(3)
    expect(tokens[0]).toMatchObject({ type: 'word', text: 'Hello' })
    expect(tokens[1]).toMatchObject({ type: 'whitespace', wordKey: null })
    expect(tokens[2]).toMatchObject({ type: 'word', text: 'world' })
  })

  it('returns empty array for empty string', () => {
    expect(latinTokenizer.tokenize('')).toEqual([])
  })

  it('assigns null wordKey to punctuation and whitespace', () => {
    const tokens = latinTokenizer.tokenize('Hello, world!')
    tokens
      .filter((t) => t.type !== 'word')
      .forEach((t) => expect(t.wordKey).toBeNull())
  })
})
