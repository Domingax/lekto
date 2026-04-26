import { ok, err } from 'neverthrow'
import type { TextItem } from 'pdfjs-dist/types/src/display/api'
import type { AsyncResult } from '@/shared/lib'
import type { ParsedBook } from './parse-epub'

// pdfjs-dist 5.x uses `for await...of ReadableStream` internally in getTextContent().
// WebKitGTK (Tauri Linux webview) does not implement ReadableStream[Symbol.asyncIterator].
function polyfillReadableStreamAsyncIterator(): void {
  if (typeof ReadableStream !== 'undefined' && !(Symbol.asyncIterator in ReadableStream.prototype)) {
    (ReadableStream.prototype as unknown as Record<symbol, unknown>)[Symbol.asyncIterator] = function () {
      const reader = this.getReader()
      return {
        async next() {
          try {
            const { done, value } = await reader.read()
            if (done) {
              reader.releaseLock()
              return { done: true as const, value: undefined }
            }
            return { done: false as const, value }
          } catch (e) {
            reader.releaseLock()
            throw e
          }
        },
        async return() {
          await reader.cancel()
          reader.releaseLock()
          return { done: true as const, value: undefined }
        },
        [Symbol.asyncIterator]() {
          return this
        },
      }
    }
  }
}

export async function parsePdf(data: ArrayBuffer, fileName: string): AsyncResult<ParsedBook> {
  polyfillReadableStreamAsyncIterator()
  try {
    const pdfjsLib = await import('pdfjs-dist')
    const workerUrl = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default
    pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl

    // disableStream prevents PDF.js from using ReadableStream async iteration,
    // which is unsupported in Tauri's WebKitGTK webview.
    const pdf = await pdfjsLib.getDocument({ data, disableStream: true }).promise
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
