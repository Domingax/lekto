export type TokenType = 'word' | 'punctuation' | 'whitespace'

export interface TokenEntity {
  id: string
  sectionId: string
  index: number
  type: TokenType
  text: string
  wordKey: string | null
}
