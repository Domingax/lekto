import { ok, err } from 'neverthrow'
import type { AsyncResult } from '@/shared/lib'

export interface ParsedBook {
  title: string
  author: string | null
  sections: Array<{ title: string; text: string }>
}

interface SpineItem {
  load(fn: unknown): Promise<void>
  unload(): void
  label?: string
  document?: { body?: { textContent?: string } }
}

export async function parseEpub(data: ArrayBuffer): AsyncResult<ParsedBook> {
  try {
    const { default: Epub } = await import('epubjs')
    const book = Epub(data as never)
    await book.ready
    const meta = await book.loaded.metadata
    const sections: Array<{ title: string; text: string }> = []
    // spineItems (Section instances) — not spine.items (raw OPF metadata)
    const spineItems = (book.spine as unknown as { spineItems: SpineItem[] }).spineItems

    for (const spineItem of spineItems) {
      await spineItem.load(book.load.bind(book))
      const text = spineItem.document?.body?.textContent ?? ''
      sections.push({ title: spineItem.label ?? '', text })
      spineItem.unload()
    }

    const typedMeta = meta as { title?: string; creator?: string }
    const rawCreator = typedMeta.creator?.trim()
    const author = rawCreator || null
    return ok({ title: typedMeta.title ?? 'Unknown', author, sections })
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    return err(`Failed to parse EPUB — ${detail}`)
  }
}
