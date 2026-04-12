import Epub from 'epubjs'
import { ok, err } from 'neverthrow'
import type { AsyncResult } from '@/shared/lib'

export interface ParsedBook {
  title: string
  sections: Array<{ title: string; text: string }>
}

export async function parseEpub(data: ArrayBuffer): AsyncResult<ParsedBook> {
  try {
    const book = Epub(data as never)
    await book.ready
    const meta = await book.loaded.metadata
    const sections: Array<{ title: string; text: string }> = []

    for (const spineItem of book.spine.items) {
      await spineItem.load(book.load.bind(book))
      const text = spineItem.document?.body?.textContent ?? ''
      sections.push({ title: spineItem.label ?? '', text })
      spineItem.unload()
    }

    return ok({ title: (meta as { title?: string }).title ?? 'Unknown', sections })
  } catch {
    return err('Failed to parse EPUB — file may be corrupted or invalid')
  }
}
