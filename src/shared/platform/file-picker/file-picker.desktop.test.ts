import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { FilePickerAdapter } from './file-picker.interface'

vi.mock('@tauri-apps/plugin-dialog', () => ({
  open: vi.fn(),
}))

vi.mock('@tauri-apps/plugin-fs', () => ({
  readFile: vi.fn(),
}))

describe('FilePickerAdapter (desktop)', () => {
  let adapter: FilePickerAdapter

  beforeEach(async () => {
    vi.resetAllMocks()
    const { open } = await import('@tauri-apps/plugin-dialog')
    const { readFile } = await import('@tauri-apps/plugin-fs')
    vi.mocked(open).mockResolvedValue('/home/user/books/book.epub')
    vi.mocked(readFile).mockResolvedValue(new Uint8Array([1, 2, 3]) as never)

    const { createDesktopFilePickerAdapter } = await import('./file-picker.desktop')
    adapter = createDesktopFilePickerAdapter()
  })

  it('pickFile returns ok with file name and data', async () => {
    const result = await adapter.pickFile({ accept: ['.epub'] })
    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.name).toBe('book.epub')
      expect(result.value.data).toBeInstanceOf(ArrayBuffer)
    }
  })

  it('pickFile passes extensions filter when accept is provided', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    await adapter.pickFile({ accept: ['.epub', '.pdf'] })
    expect(vi.mocked(open)).toHaveBeenCalledWith({
      multiple: false,
      filters: [{ name: 'Book files', extensions: ['epub', 'pdf'] }],
    })
  })

  it('pickFile passes no filter when accept is empty', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    await adapter.pickFile({})
    expect(vi.mocked(open)).toHaveBeenCalledWith({ multiple: false })
  })

  it('pickFile handles array result from open()', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    vi.mocked(open).mockResolvedValue(['/home/user/books/novel.epub'] as never)
    const result = await adapter.pickFile({})
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value.name).toBe('novel.epub')
  })

  it('pickFile returns err when user cancels (null)', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    vi.mocked(open).mockResolvedValue(null)
    const result = await adapter.pickFile({})
    expect(result.isErr()).toBe(true)
  })

  it('pickFile returns err when plugin throws', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    vi.mocked(open).mockRejectedValue(new Error('plugin error'))
    const result = await adapter.pickFile({})
    expect(result.isErr()).toBe(true)
  })

  it('pickFile returns err when readFile throws', async () => {
    const { readFile } = await import('@tauri-apps/plugin-fs')
    vi.mocked(readFile).mockRejectedValue(new Error('read error'))
    const result = await adapter.pickFile({})
    expect(result.isErr()).toBe(true)
  })

  it('pickDirectory returns ok with selected path', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    vi.mocked(open).mockResolvedValue('/home/user/vault')
    const result = await adapter.pickDirectory()
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe('/home/user/vault')
  })

  it('pickDirectory handles array result from open()', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    vi.mocked(open).mockResolvedValue(['/home/user/vault'] as never)
    const result = await adapter.pickDirectory()
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe('/home/user/vault')
  })

  it('pickDirectory returns err("cancelled") when user cancels (null)', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    vi.mocked(open).mockResolvedValue(null)
    const result = await adapter.pickDirectory()
    expect(result.isErr()).toBe(true)
    if (result.isErr()) expect(result.error).toBe('cancelled')
  })

  it('pickDirectory returns a non-cancelled err when plugin throws', async () => {
    const { open } = await import('@tauri-apps/plugin-dialog')
    vi.mocked(open).mockRejectedValue(new Error('plugin error'))
    const result = await adapter.pickDirectory()
    expect(result.isErr()).toBe(true)
    if (result.isErr()) {
      expect(result.error).toBe('plugin error')
      expect(result.error).not.toBe('cancelled')
    }
  })
})
