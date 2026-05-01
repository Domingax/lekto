import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ok, err } from 'neverthrow'
import type { BookEntity } from '@/entities'

const book: BookEntity = {
  id: 'b1',
  title: 'Test Book',
  author: null,
  fileName: 'test.epub',
  language: 'en',
  coverPath: null,
  createdAt: 1000,
}

vi.mock('@/shared/stores', () => ({
  useVaultStore: {
    getState: vi.fn(),
  },
}))

vi.mock('@/shared/platform', () => ({
  filesystemAdapter: {
    deleteFileInVault: vi.fn(),
  },
}))

vi.mock('@/shared/db', () => ({
  getDb: vi.fn(),
  schema: {
    books: 'books_table',
  },
}))

vi.mock('drizzle-orm', () => ({
  eq: vi.fn((_col: unknown, _val: unknown) => ({ _col, _val })),
}))

describe('deleteBook', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.resetModules()
  })

  it('happy path: DB delete → file delete → store updated', async () => {
    const { useVaultStore } = await import('@/shared/stores')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')
    const removeBook = vi.fn()

    vi.mocked(useVaultStore).getState.mockReturnValue({
      books: [book],
      vaultPath: '/vault',
      removeBook,
    } as never)

    const mockWhere = vi.fn().mockResolvedValue(undefined)
    const mockDelete = vi.fn().mockReturnValue({ where: mockWhere })
    vi.mocked(getDb).mockReturnValue({ delete: mockDelete } as never)
    vi.mocked(filesystemAdapter.deleteFileInVault).mockResolvedValue(ok(undefined))

    const { deleteBook } = await import('./delete-book')
    const result = await deleteBook('b1')

    expect(result.isOk()).toBe(true)
    expect(mockDelete).toHaveBeenCalled()
    expect(filesystemAdapter.deleteFileInVault).toHaveBeenCalledWith('/vault', 'books/test.epub')
    expect(removeBook).toHaveBeenCalledWith('b1')
  })

  it('returns err when book not found in store', async () => {
    const { useVaultStore } = await import('@/shared/stores')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')

    vi.mocked(useVaultStore).getState.mockReturnValue({
      books: [],
      vaultPath: '/vault',
      removeBook: vi.fn(),
    } as never)

    const { deleteBook } = await import('./delete-book')
    const result = await deleteBook('b1')

    expect(result.isErr()).toBe(true)
    if (result.isErr()) expect(result.error).toBe('Book not found')
    expect(getDb).not.toHaveBeenCalled()
    expect(filesystemAdapter.deleteFileInVault).not.toHaveBeenCalled()
  })

  it('returns err when vault not mounted', async () => {
    const { useVaultStore } = await import('@/shared/stores')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')

    vi.mocked(useVaultStore).getState.mockReturnValue({
      books: [book],
      vaultPath: null,
      removeBook: vi.fn(),
    } as never)

    const { deleteBook } = await import('./delete-book')
    const result = await deleteBook('b1')

    expect(result.isErr()).toBe(true)
    if (result.isErr()) expect(result.error).toBe('Vault not mounted')
    expect(getDb).not.toHaveBeenCalled()
    expect(filesystemAdapter.deleteFileInVault).not.toHaveBeenCalled()
  })

  it('returns err when DB throws', async () => {
    const { useVaultStore } = await import('@/shared/stores')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')
    const removeBook = vi.fn()

    vi.mocked(useVaultStore).getState.mockReturnValue({
      books: [book],
      vaultPath: '/vault',
      removeBook,
    } as never)

    const mockWhere = vi.fn().mockRejectedValue(new Error('disk full'))
    const mockDelete = vi.fn().mockReturnValue({ where: mockWhere })
    vi.mocked(getDb).mockReturnValue({ delete: mockDelete } as never)

    const { deleteBook } = await import('./delete-book')
    const result = await deleteBook('b1')

    expect(result.isErr()).toBe(true)
    if (result.isErr()) expect(result.error).toBe('disk full')
    expect(filesystemAdapter.deleteFileInVault).not.toHaveBeenCalled()
    expect(removeBook).not.toHaveBeenCalled()
  })

  it('file-not-found is non-fatal: DB delete + store update still succeed', async () => {
    const { useVaultStore } = await import('@/shared/stores')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')
    const removeBook = vi.fn()

    vi.mocked(useVaultStore).getState.mockReturnValue({
      books: [book],
      vaultPath: '/vault',
      removeBook,
    } as never)

    const mockWhere = vi.fn().mockResolvedValue(undefined)
    const mockDelete = vi.fn().mockReturnValue({ where: mockWhere })
    vi.mocked(getDb).mockReturnValue({ delete: mockDelete } as never)
    vi.mocked(filesystemAdapter.deleteFileInVault).mockResolvedValue(err('File not found'))

    const { deleteBook } = await import('./delete-book')
    const result = await deleteBook('b1')

    expect(result.isOk()).toBe(true)
    expect(removeBook).toHaveBeenCalledWith('b1')
  })

  it('generic FS error: logs warning but returns ok (DB already gone)', async () => {
    const { useVaultStore } = await import('@/shared/stores')
    const { filesystemAdapter } = await import('@/shared/platform')
    const { getDb } = await import('@/shared/db')
    const removeBook = vi.fn()
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    vi.mocked(useVaultStore).getState.mockReturnValue({
      books: [book],
      vaultPath: '/vault',
      removeBook,
    } as never)

    const mockWhere = vi.fn().mockResolvedValue(undefined)
    const mockDelete = vi.fn().mockReturnValue({ where: mockWhere })
    vi.mocked(getDb).mockReturnValue({ delete: mockDelete } as never)
    vi.mocked(filesystemAdapter.deleteFileInVault).mockResolvedValue(err('Permission denied'))

    const { deleteBook } = await import('./delete-book')
    const result = await deleteBook('b1')

    expect(result.isOk()).toBe(true)
    expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('[delete-book]'))
    expect(removeBook).toHaveBeenCalledWith('b1')
    warnSpy.mockRestore()
  })
})
