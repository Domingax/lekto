import { describe, it, expect, vi, afterEach } from 'vitest'
import { parseTxt } from './parse-txt'

function encodeText(text: string): ArrayBuffer {
  return new TextEncoder().encode(text).buffer as ArrayBuffer
}

describe('parseTxt', () => {
  it('returns ok with correct title, null author, and single section for valid UTF-8 text', async () => {
    const data = encodeText('Hello world, this is a test.')
    const result = await parseTxt(data, 'my-novel.txt')

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.title).toBe('my-novel')
      expect(result.value.author).toBeNull()
      expect(result.value.sections).toHaveLength(1)
      expect(result.value.sections[0]!.text).toBe('Hello world, this is a test.')
      expect(result.value.sections[0]!.title).toBe('')
    }
  })

  it('returns err for empty file', async () => {
    const data = encodeText('')
    const result = await parseTxt(data, 'empty.txt')

    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toBe('The text file is empty')
    }
  })

  it('returns err for whitespace-only file', async () => {
    const data = encodeText('   \n\t  \n  ')
    const result = await parseTxt(data, 'whitespace.txt')

    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toBe('The text file is empty')
    }
  })

  it('extracts title from filename without extension', async () => {
    const data = encodeText('Some content here.')
    const result = await parseTxt(data, 'my-novel.txt')

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.title).toBe('my-novel')
    }
  })

  it('handles filenames with multiple dots — strips only last extension', async () => {
    const data = encodeText('Content.')
    const result = await parseTxt(data, 'my.book.v2.txt')

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.title).toBe('my.book.v2')
    }
  })

  describe('when TextDecoder throws', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
    })

    it('returns err with the Error message', async () => {
      vi.stubGlobal(
        'TextDecoder',
        vi.fn().mockImplementation(() => ({
          decode: () => {
            throw new Error('decode failed')
          },
        })),
      )

      const result = await parseTxt(new ArrayBuffer(8), 'bad.txt')

      expect(result.isErr()).toBe(true)
      if (result.isErr()) {
        expect(result.error).toMatch(/Failed to read text file/)
        expect(result.error).toMatch(/decode failed/)
      }
    })

    it('returns err with String(e) for non-Error throws', async () => {
      vi.stubGlobal(
        'TextDecoder',
        vi.fn().mockImplementation(() => ({
          decode: () => {
            throw 'plain string failure'
          },
        })),
      )

      const result = await parseTxt(new ArrayBuffer(8), 'bad.txt')

      expect(result.isErr()).toBe(true)
      if (result.isErr()) {
        expect(result.error).toMatch(/Failed to read text file/)
        expect(result.error).toMatch(/plain string failure/)
      }
    })
  })
})
