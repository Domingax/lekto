import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { FilesystemAdapter } from './filesystem.interface'

// We import the web implementation to test it directly.
// The mock replaces the underlying OPFS/FileSystem Access API.
// Tests import the implementation module after mocking globals.

describe('FilesystemAdapter (web)', () => {
  let adapter: FilesystemAdapter

  beforeEach(async () => {
    // Mock OPFS: navigator.storage.getDirectory
    const mockFileHandle = {
      getFile: vi.fn().mockResolvedValue({
        text: vi.fn().mockResolvedValue('file content'),
      }),
      createWritable: vi.fn().mockResolvedValue({
        write: vi.fn().mockResolvedValue(undefined),
        close: vi.fn().mockResolvedValue(undefined),
      }),
    }

    const mockDirHandle = {
      getFileHandle: vi.fn().mockResolvedValue(mockFileHandle),
      getDirectoryHandle: vi.fn().mockResolvedValue({} as FileSystemDirectoryHandle),
      removeEntry: vi.fn().mockResolvedValue(undefined),
      entries: vi.fn().mockReturnValue(
        (async function* () {
          yield ['file.txt', { kind: 'file' }]
        })()
      ),
    }

    vi.stubGlobal('navigator', {
      storage: {
        getDirectory: vi.fn().mockResolvedValue(mockDirHandle),
      },
    })

    const { createWebFilesystemAdapter } = await import('./filesystem.web')
    adapter = createWebFilesystemAdapter()
  })

  it('readFile returns ok with file content', async () => {
    const result = await adapter.readFile('test.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe('file content')
  })

  it('readFile returns err when read fails', async () => {
    vi.stubGlobal('navigator', {
      storage: {
        getDirectory: vi.fn().mockRejectedValue(new Error('not found')),
      },
    })
    const { createWebFilesystemAdapter } = await import('./filesystem.web')
    const failAdapter = createWebFilesystemAdapter()
    const result = await failAdapter.readFile('missing.txt')
    expect(result.isErr()).toBe(true)
  })

  it('writeFile returns ok on success', async () => {
    const result = await adapter.writeFile('test.txt', 'hello')
    expect(result.isOk()).toBe(true)
  })

  it('exists returns ok(true) when file is accessible', async () => {
    const result = await adapter.exists('test.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe(true)
  })

  it('readdir returns ok with list of entry names', async () => {
    const result = await adapter.readdir('.')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toContain('file.txt')
  })

  it('deleteFile returns ok on success', async () => {
    const result = await adapter.deleteFile('test.txt')
    expect(result.isOk()).toBe(true)
  })

  it('deleteFile returns err when removal fails', async () => {
    vi.stubGlobal('navigator', {
      storage: {
        getDirectory: vi.fn().mockResolvedValue({
          removeEntry: vi.fn().mockRejectedValue(new Error('not found')),
        }),
      },
    })
    const { createWebFilesystemAdapter } = await import('./filesystem.web')
    const failAdapter = createWebFilesystemAdapter()
    const result = await failAdapter.deleteFile('missing.txt')
    expect(result.isErr()).toBe(true)
  })

  it('mkdir returns ok on success', async () => {
    const result = await adapter.mkdir('new-dir')
    expect(result.isOk()).toBe(true)
  })

  it('mkdir returns err when directory creation fails', async () => {
    vi.stubGlobal('navigator', {
      storage: {
        getDirectory: vi.fn().mockResolvedValue({
          getDirectoryHandle: vi.fn().mockRejectedValue(new Error('permission denied')),
        }),
      },
    })
    const { createWebFilesystemAdapter } = await import('./filesystem.web')
    const failAdapter = createWebFilesystemAdapter()
    const result = await failAdapter.mkdir('forbidden-dir')
    expect(result.isErr()).toBe(true)
  })
})

describe('setWebFilesystemRoot', () => {
  afterEach(async () => {
    // Reset native root to null after each test
    const { setWebFilesystemRoot } = await import('./filesystem.web')
    setWebFilesystemRoot(null)
  })

  it('when set to a handle, adapter reads from the native handle (not OPFS)', async () => {
    // Stub OPFS to return different content so we can distinguish
    vi.stubGlobal('navigator', {
      storage: {
        getDirectory: vi.fn().mockResolvedValue({
          getFileHandle: vi.fn().mockResolvedValue({
            getFile: vi.fn().mockResolvedValue({ text: vi.fn().mockResolvedValue('opfs content') }),
          }),
        }),
      },
    })

    const mockNativeHandle = {
      getFileHandle: vi.fn().mockResolvedValue({
        getFile: vi.fn().mockResolvedValue({ text: vi.fn().mockResolvedValue('native content') }),
      }),
      getDirectoryHandle: vi.fn(),
    } as unknown as FileSystemDirectoryHandle

    const { setWebFilesystemRoot, createWebFilesystemAdapter } = await import('./filesystem.web')
    setWebFilesystemRoot(mockNativeHandle)

    const adapter = createWebFilesystemAdapter()
    const result = await adapter.readFile('test.txt')
    expect(result.isOk()).toBe(true)
    // Result comes from native handle, not OPFS
    expect(result._unsafeUnwrap()).toBe('native content')
  })

  it('when reset to null, adapter falls back to OPFS', async () => {
    const mockGetDirectory = vi.fn().mockResolvedValue({
      getFileHandle: vi.fn().mockResolvedValue({
        getFile: vi.fn().mockResolvedValue({ text: vi.fn().mockResolvedValue('opfs content') }),
      }),
    })
    vi.stubGlobal('navigator', { storage: { getDirectory: mockGetDirectory } })

    const { setWebFilesystemRoot, createWebFilesystemAdapter } = await import('./filesystem.web')
    setWebFilesystemRoot(null)

    const adapter = createWebFilesystemAdapter()
    await adapter.readFile('test.txt')
    expect(mockGetDirectory).toHaveBeenCalled()
  })
})
