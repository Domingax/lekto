import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('pdfjs-dist', () => ({
  GlobalWorkerOptions: { workerSrc: '' },
  getDocument: vi.fn(),
}))

vi.mock('pdfjs-dist/build/pdf.worker.min.mjs?url', () => ({ default: 'worker.js' }))

import * as pdfjsLib from 'pdfjs-dist'

function makeTextItems(strs: string[]) {
  return strs.map((str) => ({ str, dir: 'ltr', width: 10, height: 10, transform: [], fontName: 'F', hasEOL: false }))
}

function makeMockPdf(pages: string[][], titleMeta?: string) {
  const mockPages = pages.map((strs) => ({
    getTextContent: vi.fn().mockResolvedValue({ items: makeTextItems(strs) }),
  }))

  const mockPdf = {
    numPages: pages.length,
    getPage: vi.fn((i: number) => Promise.resolve(mockPages[i - 1])),
    getMetadata: vi.fn().mockResolvedValue(
      titleMeta ? { info: { Title: titleMeta } } : { info: {} },
    ),
  }

  vi.mocked(pdfjsLib.getDocument).mockReturnValue({ promise: Promise.resolve(mockPdf) } as never)

  return mockPdf
}

describe('parsePdf', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns ok with pages as sections (happy path)', async () => {
    makeMockPdf([['Hello world'], ['Second page text']])
    const { parsePdf } = await import('./parse-pdf')

    const data = new ArrayBuffer(8)
    const result = await parsePdf(data, 'my-book.pdf')

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.sections).toHaveLength(2)
      expect(result.value.sections[0]!.title).toBe('Page 1')
      expect(result.value.sections[0]!.text).toBe('Hello world')
      expect(result.value.sections[1]!.title).toBe('Page 2')
      expect(result.value.sections[1]!.text).toBe('Second page text')
    }
  })

  it('passes disableStream:true to getDocument to avoid ReadableStream issues in Tauri WebKitGTK', async () => {
    makeMockPdf([['Some text']])
    const { parsePdf } = await import('./parse-pdf')

    await parsePdf(new ArrayBuffer(8), 'test.pdf')

    expect(vi.mocked(pdfjsLib.getDocument)).toHaveBeenCalledWith(
      expect.objectContaining({ disableStream: true }),
    )
  })

  it('polyfills ReadableStream async iterator when Symbol.asyncIterator is missing (Tauri WebKitGTK)', async () => {
    makeMockPdf([['Content']])
    const original = Object.getOwnPropertyDescriptor(ReadableStream.prototype, Symbol.asyncIterator)
    delete (ReadableStream.prototype as unknown as Record<symbol, unknown>)[Symbol.asyncIterator]

    const { parsePdf } = await import('./parse-pdf')
    await parsePdf(new ArrayBuffer(8), 'test.pdf')

    expect((ReadableStream.prototype as unknown as Record<symbol, unknown>)[Symbol.asyncIterator]).toBeDefined()

    if (original) {
      Object.defineProperty(ReadableStream.prototype, Symbol.asyncIterator, original)
    } else {
      delete (ReadableStream.prototype as unknown as Record<symbol, unknown>)[Symbol.asyncIterator]
    }
  })

  describe('polyfilled ReadableStream async iterator', () => {
    let originalDescriptor: PropertyDescriptor | undefined

    beforeEach(async () => {
      makeMockPdf([['Content']])
      originalDescriptor = Object.getOwnPropertyDescriptor(ReadableStream.prototype, Symbol.asyncIterator)
      delete (ReadableStream.prototype as unknown as Record<symbol, unknown>)[Symbol.asyncIterator]
      const { parsePdf } = await import('./parse-pdf')
      await parsePdf(new ArrayBuffer(8), 'install.pdf')
    })

    afterEach(() => {
      if (originalDescriptor) {
        Object.defineProperty(ReadableStream.prototype, Symbol.asyncIterator, originalDescriptor)
      } else {
        delete (ReadableStream.prototype as unknown as Record<symbol, unknown>)[Symbol.asyncIterator]
      }
    })

    it('iterates all chunks then completes', async () => {
      const stream = new ReadableStream<number>({
        start(controller) {
          controller.enqueue(1)
          controller.enqueue(2)
          controller.enqueue(3)
          controller.close()
        },
      })

      const collected: number[] = []
      for await (const chunk of stream as unknown as AsyncIterable<number>) {
        collected.push(chunk)
      }

      expect(collected).toEqual([1, 2, 3])
    })

    it('propagates errors thrown by reader.read()', async () => {
      const stream = new ReadableStream<number>({
        start(controller) {
          controller.error(new Error('stream boom'))
        },
      })

      await expect(async () => {
        for await (const _chunk of stream as unknown as AsyncIterable<number>) {
          // unreachable
        }
      }).rejects.toThrow('stream boom')
    })

    it('cancels the reader when iteration breaks early (return path)', async () => {
      const stream = new ReadableStream<number>({
        start(controller) {
          controller.enqueue(1)
          controller.enqueue(2)
          controller.close()
        },
      })

      for await (const _chunk of stream as unknown as AsyncIterable<number>) {
        break
      }

      // After break, the iterator's return() ran — getReader() must succeed again
      // (would throw "ReadableStream is locked" if return() didn't releaseLock)
      expect(() => stream.getReader()).not.toThrow()
    })
  })

  it('uses metadata Title when available', async () => {
    makeMockPdf([['Content here']], 'My Great Novel')
    const { parsePdf } = await import('./parse-pdf')

    const result = await parsePdf(new ArrayBuffer(8), 'something.pdf')

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.title).toBe('My Great Novel')
    }
  })

  it('falls back to filename without extension when no metadata title', async () => {
    makeMockPdf([['Content here']])
    const { parsePdf } = await import('./parse-pdf')

    const result = await parsePdf(new ArrayBuffer(8), 'my-book.pdf')

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.title).toBe('my-book')
    }
  })

  it('returns err with scanned PDF message when all pages have empty text', async () => {
    makeMockPdf([['   '], [''], ['\t']])
    const { parsePdf } = await import('./parse-pdf')

    const result = await parsePdf(new ArrayBuffer(8), 'scanned.pdf')

    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toMatch(/no selectable text/)
    }
  })

  it('returns err when getDocument throws', async () => {
    vi.mocked(pdfjsLib.getDocument).mockReturnValue({
      promise: Promise.reject(new Error('invalid PDF structure')),
    } as never)
    const { parsePdf } = await import('./parse-pdf')

    const result = await parsePdf(new ArrayBuffer(8), 'bad.pdf')

    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toMatch(/Failed to parse PDF/)
      expect(result.error).toMatch(/invalid PDF structure/)
    }
  })

  it('returns err with String(e) when getDocument rejects with non-Error value', async () => {
    vi.mocked(pdfjsLib.getDocument).mockReturnValue({
      promise: Promise.reject('plain string failure'),
    } as never)
    const { parsePdf } = await import('./parse-pdf')

    const result = await parsePdf(new ArrayBuffer(8), 'bad.pdf')

    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toMatch(/Failed to parse PDF/)
      expect(result.error).toMatch(/plain string failure/)
    }
  })

  it('falls back to filename when getMetadata throws', async () => {
    const mockPdf = {
      numPages: 1,
      getPage: vi.fn().mockResolvedValue({
        getTextContent: vi.fn().mockResolvedValue({ items: makeTextItems(['Content here']) }),
      }),
      getMetadata: vi.fn().mockRejectedValue(new Error('metadata unavailable')),
    }
    vi.mocked(pdfjsLib.getDocument).mockReturnValue({ promise: Promise.resolve(mockPdf) } as never)
    const { parsePdf } = await import('./parse-pdf')

    const result = await parsePdf(new ArrayBuffer(8), 'fallback-book.pdf')

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.title).toBe('fallback-book')
    }
  })

  it('creates one section per PDF page', async () => {
    makeMockPdf([['Page 1'], ['Page 2'], ['Page 3'], ['Page 4']])
    const { parsePdf } = await import('./parse-pdf')

    const result = await parsePdf(new ArrayBuffer(8), 'book.pdf')

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.sections).toHaveLength(4)
    }
  })
})
