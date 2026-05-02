import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockSpineItem = (text: string, label: string) => ({
  load: vi.fn().mockResolvedValue(undefined),
  unload: vi.fn(),
  label,
  document: { body: { textContent: text } },
})

const mockBook = (title: string, spineItems: ReturnType<typeof mockSpineItem>[], creator?: string) => ({
  ready: Promise.resolve(),
  loaded: { metadata: Promise.resolve({ title, creator }) },
  spine: { spineItems },
  load: vi.fn(),
})

vi.mock('epubjs', () => ({
  default: vi.fn(),
}))

describe('parseEpub', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('returns ParsedBook with title and sections on success', async () => {
    const { default: Epub } = await import('epubjs')
    vi.mocked(Epub).mockReturnValue(
      mockBook('My Book', [
        mockSpineItem('Chapter one text', 'Chapter 1'),
        mockSpineItem('Chapter two text', 'Chapter 2'),
      ]) as never,
    )

    const { parseEpub } = await import('./parse-epub')
    const result = await parseEpub(new ArrayBuffer(8))

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.title).toBe('My Book')
      expect(result.value.author).toBeNull()
      expect(result.value.sections).toHaveLength(2)
      expect(result.value.sections[0]).toMatchObject({ title: 'Chapter 1', text: 'Chapter one text' })
      expect(result.value.sections[1]).toMatchObject({ title: 'Chapter 2', text: 'Chapter two text' })
    }
  })

  it('extracts author from metadata.creator', async () => {
    const { default: Epub } = await import('epubjs')
    vi.mocked(Epub).mockReturnValue(
      mockBook('My Book', [mockSpineItem('text', 'Ch1')], 'Jane Austen') as never,
    )

    const { parseEpub } = await import('./parse-epub')
    const result = await parseEpub(new ArrayBuffer(8))

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.author).toBe('Jane Austen')
    }
  })

  it('returns null author when creator is absent', async () => {
    const { default: Epub } = await import('epubjs')
    vi.mocked(Epub).mockReturnValue(mockBook('No Author', []) as never)

    const { parseEpub } = await import('./parse-epub')
    const result = await parseEpub(new ArrayBuffer(8))

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.author).toBeNull()
    }
  })

  it('returns null author when creator is empty string', async () => {
    const { default: Epub } = await import('epubjs')
    vi.mocked(Epub).mockReturnValue(mockBook('Title', [], '   ') as never)

    const { parseEpub } = await import('./parse-epub')
    const result = await parseEpub(new ArrayBuffer(8))

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.author).toBeNull()
    }
  })

  it('returns err when epubjs throws', async () => {
    const { default: Epub } = await import('epubjs')
    vi.mocked(Epub).mockImplementation(() => {
      throw new Error('invalid epub')
    })

    const { parseEpub } = await import('./parse-epub')
    const result = await parseEpub(new ArrayBuffer(8))

    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toContain('Failed to parse EPUB')
    }
  })

  it('returns ParsedBook with empty sections when spine is empty', async () => {
    const { default: Epub } = await import('epubjs')
    vi.mocked(Epub).mockReturnValue(mockBook('Empty Book', []) as never)

    const { parseEpub } = await import('./parse-epub')
    const result = await parseEpub(new ArrayBuffer(8))

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.sections).toHaveLength(0)
    }
  })
})
