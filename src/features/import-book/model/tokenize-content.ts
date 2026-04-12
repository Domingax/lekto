import { latinTokenizer } from '@/shared/lib'
import type { TokenEntity } from '@/entities'

export function tokenizeSection(text: string, sectionId: string): TokenEntity[] {
  const rawTokens = latinTokenizer.tokenize(text).filter((t) => t.text.length > 0)
  return rawTokens.map((raw, index) => ({
    id: `${sectionId}_${index}`,
    sectionId,
    index,
    type: raw.type,
    text: raw.text,
    wordKey: raw.wordKey,
  }))
}
