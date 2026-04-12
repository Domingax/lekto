import type { RawToken, Tokenizer } from './tokenizer.interface'

const WORD_RE = /\p{L}+/u
const WS_RE = /\s+/

function tokenize(text: string): RawToken[] {
  if (text.length === 0) return []
  const segments = text.split(/(\p{L}+|\s+)/u).filter((s) => s.length > 0)
  return segments.map((seg) => {
    if (WORD_RE.test(seg)) {
      return { type: 'word', text: seg, wordKey: seg.toLowerCase() }
    }
    if (WS_RE.test(seg)) {
      return { type: 'whitespace', text: seg, wordKey: null }
    }
    return { type: 'punctuation', text: seg, wordKey: null }
  })
}

export const latinTokenizer: Tokenizer = { tokenize }
