import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { FilesystemAdapter } from './filesystem.interface'

vi.mock('@tauri-apps/plugin-fs', () => ({
  readTextFile: vi.fn(),
  writeTextFile: vi.fn(),
  remove: vi.fn(),
  mkdir: vi.fn(),
  readDir: vi.fn(),
  exists: vi.fn(),
  copyFile: vi.fn(),
  writeFile: vi.fn(),
}))

describe('FilesystemAdapter (desktop)', () => {
  let adapter: FilesystemAdapter

  beforeEach(async () => {
    vi.resetAllMocks()
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.readTextFile).mockResolvedValue('file content')
    vi.mocked(fs.writeTextFile).mockResolvedValue(undefined)
    vi.mocked(fs.remove).mockResolvedValue(undefined)
    vi.mocked(fs.mkdir).mockResolvedValue(undefined)
    vi.mocked(fs.readDir).mockResolvedValue([{ name: 'a.txt' }, { name: 'b.txt' }] as never)
    vi.mocked(fs.exists).mockResolvedValue(true)
    vi.mocked(fs.copyFile).mockResolvedValue(undefined)
    vi.mocked(fs.writeFile).mockResolvedValue(undefined)

    const { createDesktopFilesystemAdapter } = await import('./filesystem.desktop')
    adapter = createDesktopFilesystemAdapter()
  })

  it('readFile returns ok with file content', async () => {
    const result = await adapter.readFile('test.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe('file content')
  })

  it('readFile returns err when plugin throws', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.readTextFile).mockRejectedValue(new Error('file not found'))
    const result = await adapter.readFile('missing.txt')
    expect(result.isErr()).toBe(true)
  })

  it('writeFile returns ok on success', async () => {
    const result = await adapter.writeFile('test.txt', 'hello')
    expect(result.isOk()).toBe(true)
  })

  it('writeFile returns err when plugin throws', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.writeTextFile).mockRejectedValue(new Error('permission denied'))
    const result = await adapter.writeFile('test.txt', 'hello')
    expect(result.isErr()).toBe(true)
  })

  it('deleteFile returns ok on success', async () => {
    const result = await adapter.deleteFile('test.txt')
    expect(result.isOk()).toBe(true)
  })

  it('deleteFile returns err when plugin throws', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.remove).mockRejectedValue(new Error('not found'))
    const result = await adapter.deleteFile('test.txt')
    expect(result.isErr()).toBe(true)
  })

  it('mkdir returns ok on success', async () => {
    const result = await adapter.mkdir('new-dir')
    expect(result.isOk()).toBe(true)
  })

  it('mkdir returns err when plugin throws', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.mkdir).mockRejectedValue(new Error('permission denied'))
    const result = await adapter.mkdir('new-dir')
    expect(result.isErr()).toBe(true)
  })

  it('readdir returns ok with file names', async () => {
    const result = await adapter.readdir('.')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value).toContain('a.txt')
      expect(result.value).toContain('b.txt')
    }
  })

  it('readdir returns ok with empty string for entries without name', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.readDir).mockResolvedValue([{ name: undefined }] as never)
    const result = await adapter.readdir('.')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toEqual([''])
  })

  it('readdir returns err when plugin throws', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.readDir).mockRejectedValue(new Error('not a directory'))
    const result = await adapter.readdir('.')
    expect(result.isErr()).toBe(true)
  })

  it('exists returns ok(true) when file exists', async () => {
    const result = await adapter.exists('test.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe(true)
  })

  it('exists returns ok(false) when file does not exist', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.exists).mockResolvedValue(false)
    const result = await adapter.exists('missing.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe(false)
  })

  it('exists returns ok(false) when plugin throws', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.exists).mockRejectedValue(new Error('access denied'))
    const result = await adapter.exists('test.txt')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe(false)
  })

  it('copyFile returns ok on success', async () => {
    const result = await adapter.copyFile('/old/path/file.db', '/new/path/file.db')
    expect(result.isOk()).toBe(true)
  })

  it('copyFile returns err when plugin throws', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.copyFile).mockRejectedValue(new Error('permission denied'))
    const result = await adapter.copyFile('/old/path/file.db', '/new/path/file.db')
    expect(result.isErr()).toBe(true)
  })

  it('writeFileBinary returns ok on success', async () => {
    const result = await adapter.writeFileBinary('/vault/books/test.epub', new Uint8Array([1, 2, 3]))
    expect(result.isOk()).toBe(true)
  })

  it('writeFileBinary returns err when plugin throws', async () => {
    const fs = await import('@tauri-apps/plugin-fs')
    vi.mocked(fs.writeFile).mockRejectedValue(new Error('permission denied'))
    const result = await adapter.writeFileBinary('/vault/books/test.epub', new Uint8Array([1, 2, 3]))
    expect(result.isErr()).toBe(true)
  })
})
