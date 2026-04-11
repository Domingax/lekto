import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { FilesystemAdapter } from './filesystem.interface'

vi.mock('@capacitor/filesystem', () => ({
  Filesystem: {
    readFile: vi.fn(),
    writeFile: vi.fn(),
    deleteFile: vi.fn(),
    mkdir: vi.fn(),
    readdir: vi.fn(),
    stat: vi.fn(),
    copy: vi.fn(),
  },
  Directory: {
    Documents: 'DOCUMENTS',
  },
  Encoding: {
    UTF8: 'utf8',
  },
}))

describe('FilesystemAdapter (android)', () => {
  let adapter: FilesystemAdapter

  beforeEach(async () => {
    vi.resetAllMocks()
    const { Filesystem } = await import('@capacitor/filesystem')
    vi.mocked(Filesystem.readFile).mockResolvedValue({ data: 'file content' } as never)
    vi.mocked(Filesystem.writeFile).mockResolvedValue({ uri: 'file://test.txt' } as never)
    vi.mocked(Filesystem.deleteFile).mockResolvedValue(undefined as never)
    vi.mocked(Filesystem.mkdir).mockResolvedValue({ uri: 'file://dir/' } as never)
    vi.mocked(Filesystem.readdir).mockResolvedValue({ files: [{ name: 'a.txt', type: 'file', size: 0, mtime: 0, uri: '', ctime: 0 }] } as never)
    vi.mocked(Filesystem.stat).mockResolvedValue({ size: 1, type: 'file', mtime: 0, uri: 'file://test.txt', ctime: 0 } as never)
    vi.mocked(Filesystem.copy).mockResolvedValue({ uri: 'file://dest.db' } as never)

    const { createAndroidFilesystemAdapter } = await import('./filesystem.android')
    adapter = createAndroidFilesystemAdapter()
  })

  it('readFile returns ok with file content', async () => {
    const result = await adapter.readFile('test.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe('file content')
  })

  it('readFile returns err when Capacitor throws', async () => {
    const { Filesystem } = await import('@capacitor/filesystem')
    vi.mocked(Filesystem.readFile).mockRejectedValue(new Error('file not found'))
    const result = await adapter.readFile('missing.txt')
    expect(result.isErr()).toBe(true)
  })

  it('writeFile returns ok on success', async () => {
    const result = await adapter.writeFile('test.txt', 'hello')
    expect(result.isOk()).toBe(true)
  })

  it('deleteFile returns ok on success', async () => {
    const result = await adapter.deleteFile('test.txt')
    expect(result.isOk()).toBe(true)
  })

  it('mkdir returns ok on success', async () => {
    const result = await adapter.mkdir('new-dir')
    expect(result.isOk()).toBe(true)
  })

  it('mkdir returns ok when directory already exists (OS-PLUG-FILE-0010)', async () => {
    const { Filesystem } = await import('@capacitor/filesystem')
    vi.mocked(Filesystem.mkdir).mockRejectedValue({ code: 'OS-PLUG-FILE-0010', message: 'already exists' })
    const result = await adapter.mkdir('existing-dir')
    expect(result.isOk()).toBe(true)
  })

  it('mkdir returns err on other errors', async () => {
    const { Filesystem } = await import('@capacitor/filesystem')
    vi.mocked(Filesystem.mkdir).mockRejectedValue(new Error('permission denied'))
    const result = await adapter.mkdir('new-dir')
    expect(result.isErr()).toBe(true)
  })

  it('readdir returns ok with file names', async () => {
    const result = await adapter.readdir('.')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toContain('a.txt')
  })

  it('exists returns ok(true) when stat succeeds', async () => {
    const result = await adapter.exists('test.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe(true)
  })

  it('exists returns ok(false) when stat throws a not-found error', async () => {
    const { Filesystem } = await import('@capacitor/filesystem')
    vi.mocked(Filesystem.stat).mockRejectedValue(new Error('File does not exist'))
    const result = await adapter.exists('missing.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe(false)
  })

  it('exists returns err when stat throws a genuine error (not file-not-found)', async () => {
    const { Filesystem } = await import('@capacitor/filesystem')
    vi.mocked(Filesystem.stat).mockRejectedValue(new Error('Permission denied'))
    const result = await adapter.exists('protected.txt')
    expect(result.isErr()).toBe(true)
  })

  it('copyFile returns ok on success', async () => {
    const result = await adapter.copyFile('/old/path/file.db', '/new/path/file.db')
    expect(result.isOk()).toBe(true)
  })

  it('copyFile returns err when Capacitor throws', async () => {
    const { Filesystem } = await import('@capacitor/filesystem')
    vi.mocked(Filesystem.copy).mockRejectedValue(new Error('permission denied'))
    const result = await adapter.copyFile('/old/path/file.db', '/new/path/file.db')
    expect(result.isErr()).toBe(true)
  })
})
