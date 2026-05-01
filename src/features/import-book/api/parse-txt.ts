import { ok, err } from 'neverthrow'
import type { AsyncResult } from '@/shared/lib'
import type { ParsedBook } from './parse-epub'

export async function parseTxt(data: ArrayBuffer, fileName: string): AsyncResult<ParsedBook> {
  try {
    const text = new TextDecoder('utf-8').decode(data)
    if (text.trim().length === 0) {
      return err('The text file is empty')
    }
    const title = fileName.replace(/\.[^.]+$/, '')
    return ok({ title, author: null, sections: [{ title: '', text }] })
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    return err(`Failed to read text file — ${detail}`)
  }
}
