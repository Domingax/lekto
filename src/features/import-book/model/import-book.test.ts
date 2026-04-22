import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ok, err } from 'neverthrow'
import type { BookEntity } from '@/entities'

vi.mock('../api/parse-epub', () => ({
  parseEpub: vi.fn(),
}))

vi.mock('./detect-language', () => ({
  detectLanguage: vi.fn(),
}))

vi.mock('@/shared/platform', () => ({
  filesystemAdapter: {
    mkdirInVault: vi.fn(),
    writeFileBinaryToVault: vi.fn(),
  },
}))

vi.mock('@/shared/stores', () => ({
  useVaultStore: {
    getState: vi.fn(() => ({
      vaultPath: '/vault',
      addBook: vi.fn(),
    })),
  },
}))

vi.mock('@/shared/db', () => ({
  getDb: vi.fn(),
  schema: {
    books: 'books',
    sections: 'sections',
    tokens: 'tokens',
  },
}))

describe('importBook', () => {
  const mockData = new ArrayBuffer(8)
  const mockFileName = 'test.epub'
  const mockResolveLanguage = vi.fn()

  const mockParsedBook = {
    title: 'Test Book',
    sections: [{ title: 'Chapter 1', text: 'Hello world' }],
  }

  beforeEach(() => {
    vi.resetAllMocks()
    vi.resetModules()
  })

  it('happy path returns ok(BookEntity) with correct title and language', async () => {
    const { parseEpub } = await import('../api/parse-epub')
    const { detectLanguage } = await import('./detect-language')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')

    vi.mocked(parseEpub).mockResolvedValue(ok(mockParsedBook))
    vi.mocked(detectLanguage).mockResolvedValue('en')
    mockResolveLanguage.mockResolvedValue('en')
    vi.mocked(filesystemAdapter.mkdirInVault).mockResolvedValue(ok(undefined))
    vi.mocked(filesystemAdapter.writeFileBinaryToVault).mockResolvedValue(ok(undefined))

    const mockInsert = vi.fn().mockReturnValue({
      values: vi.fn().mockResolvedValue(undefined),
    })
    const mockTransaction = vi.fn().mockImplementation(async (fn: (tx: unknown) => Promise<void>) => fn({ insert: mockInsert }))
    vi.mocked(getDb).mockReturnValue({ transaction: mockTransaction } as never)

    const { importBook } = await import('./import-book')
    const result = await importBook(mockData, mockFileName, mockResolveLanguage)

    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      const book = result.value as BookEntity
      expect(book.title).toBe('Test Book')
      expect(book.language).toBe('en')
      expect(book.fileName).toBe('test.epub')
    }
  })

  it('returns err when DB transaction throws', async () => {
    const { parseEpub } = await import('../api/parse-epub')
    const { detectLanguage } = await import('./detect-language')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')

    vi.mocked(parseEpub).mockResolvedValue(ok(mockParsedBook))
    vi.mocked(detectLanguage).mockResolvedValue('en')
    mockResolveLanguage.mockResolvedValue('en')
    vi.mocked(filesystemAdapter.mkdirInVault).mockResolvedValue(ok(undefined))
    vi.mocked(filesystemAdapter.writeFileBinaryToVault).mockResolvedValue(ok(undefined))

    const mockTransaction = vi.fn().mockRejectedValue(new Error('disk full'))
    vi.mocked(getDb).mockReturnValue({ transaction: mockTransaction } as never)

    const { importBook } = await import('./import-book')
    const result = await importBook(mockData, mockFileName, mockResolveLanguage)

    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toBe('disk full')
    }
  })

  it('returns err when parseEpub fails', async () => {
    const { parseEpub } = await import('../api/parse-epub')
    vi.mocked(parseEpub).mockResolvedValue(err('Failed to parse EPUB — file may be corrupted or invalid'))

    const { importBook } = await import('./import-book')
    const result = await importBook(mockData, mockFileName, mockResolveLanguage)

    expect(result.isErr()).toBe(true)
    expect(mockResolveLanguage).not.toHaveBeenCalled()
  })

  it('returns err when resolveLanguage returns null (user cancelled)', async () => {
    const { parseEpub } = await import('../api/parse-epub')
    const { detectLanguage } = await import('./detect-language')

    vi.mocked(parseEpub).mockResolvedValue(ok(mockParsedBook))
    vi.mocked(detectLanguage).mockResolvedValue('en')
    mockResolveLanguage.mockResolvedValue(null)

    const { importBook } = await import('./import-book')
    const result = await importBook(mockData, mockFileName, mockResolveLanguage)

    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toBe('Import cancelled')
    }
  })

  it('returns err when writeFileBinary fails — no DB writes', async () => {
    const { parseEpub } = await import('../api/parse-epub')
    const { detectLanguage } = await import('./detect-language')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')

    vi.mocked(parseEpub).mockResolvedValue(ok(mockParsedBook))
    vi.mocked(detectLanguage).mockResolvedValue('en')
    mockResolveLanguage.mockResolvedValue('en')
    vi.mocked(filesystemAdapter.mkdirInVault).mockResolvedValue(ok(undefined))
    vi.mocked(filesystemAdapter.writeFileBinaryToVault).mockResolvedValue(err('Failed to write binary file'))

    const { importBook } = await import('./import-book')
    const result = await importBook(mockData, mockFileName, mockResolveLanguage)

    expect(result.isErr()).toBe(true)
    expect(getDb).not.toHaveBeenCalled()
  })
})
