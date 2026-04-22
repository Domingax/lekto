import { ok, err } from 'neverthrow'
import * as pdfjsLib from 'pdfjs-dist'
import type { TextItem } from 'pdfjs-dist/types/src/display/api'
import type { AsyncResult } from '@/shared/lib'
import type { ParsedBook } from './parse-epub'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

export async function parsePdf(data: ArrayBuffer, fileName: string): AsyncResult<ParsedBook> {
  try {
    pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl
    const pdf = await pdfjsLib.getDocument({ data }).promise
    const sections: Array<{ title: string; text: string }> = []

    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i)
      const content = await page.getTextContent()
      const text = content.items
        .filter((item): item is TextItem => 'str' in item)
        .map((item) => item.str)
        .join(' ')
      sections.push({ title: `Page ${i}`, text })
    }

    const totalText = sections.map((s) => s.text.trim()).join('')
    if (totalText.length === 0) {
      return err('This PDF has no selectable text — it may be a scanned image')
    }

    const meta = await pdf.getMetadata().catch(() => null)
    const pdfTitle = (meta?.info as Record<string, unknown> | null)?.['Title']
    const title =
      typeof pdfTitle === 'string' && pdfTitle.trim()
        ? pdfTitle.trim()
        : fileName.replace(/\.[^.]+$/, '')

    return ok({ title, sections })
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    return err(`Failed to parse PDF — ${detail}`)
  }
}
