export type TokenType = 'word' | 'punctuation' | 'whitespace'

export interface RawToken {
  type: TokenType
  text: string
  wordKey: string | null
}

export interface Tokenizer {
  tokenize(text: string): RawToken[]
}
