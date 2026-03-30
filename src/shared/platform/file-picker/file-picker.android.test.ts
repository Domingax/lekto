import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { FilePickerAdapter } from './file-picker.interface'

vi.mock('@capawesome/capacitor-file-picker', () => ({
  FilePicker: {
    pickFiles: vi.fn(),
    pickDirectory: vi.fn(),
  },
}))

describe('FilePickerAdapter (android)', () => {
  let adapter: FilePickerAdapter

  beforeEach(async () => {
    vi.resetAllMocks()
    const { FilePicker } = await import('@capawesome/capacitor-file-picker')
    vi.mocked(FilePicker.pickFiles).mockResolvedValue({
      files: [
        {
          name: 'book.epub',
          path: '/storage/emulated/0/Download/book.epub',
          mimeType: 'application/epub+zip',
          size: 1024,
          data: btoa('epub-content'),
          modifiedAt: Date.now(),
        },
      ],
    })

    const { createAndroidFilePickerAdapter } = await import('./file-picker.android')
    adapter = createAndroidFilePickerAdapter()
  })

  it('pickFile returns ok with file name and data', async () => {
    const result = await adapter.pickFile({ accept: ['.epub'] })
    expect(result.isOk()).toBe(true)
    if (result.isOk()) {
      expect(result.value.name).toBe('book.epub')
      expect(result.value.data).toBeInstanceOf(ArrayBuffer)
    }
  })

  it('pickFile returns err when user cancels (empty files array)', async () => {
    const { FilePicker } = await import('@capawesome/capacitor-file-picker')
    vi.mocked(FilePicker.pickFiles).mockResolvedValue({ files: [] })
    const result = await adapter.pickFile({})
    expect(result.isErr()).toBe(true)
  })

  it('pickFile returns err when plugin throws', async () => {
    const { FilePicker } = await import('@capawesome/capacitor-file-picker')
    vi.mocked(FilePicker.pickFiles).mockRejectedValue(new Error('plugin error'))
    const result = await adapter.pickFile({})
    expect(result.isErr()).toBe(true)
  })

  it('pickDirectory returns ok with path from directory picker', async () => {
    const { FilePicker } = await import('@capawesome/capacitor-file-picker')
    vi.mocked(FilePicker.pickDirectory).mockResolvedValue({
      path: 'content://com.android.externalstorage/vault',
    })
    const result = await adapter.pickDirectory()
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe('content://com.android.externalstorage/vault')
  })

  it('pickDirectory returns err when user cancels', async () => {
    const { FilePicker } = await import('@capawesome/capacitor-file-picker')
    vi.mocked(FilePicker.pickDirectory).mockRejectedValue(new Error('cancelled'))
    const result = await adapter.pickDirectory()
    expect(result.isErr()).toBe(true)
  })

  it('pickDirectory returns err when plugin throws', async () => {
    const { FilePicker } = await import('@capawesome/capacitor-file-picker')
    vi.mocked(FilePicker.pickDirectory).mockRejectedValue(new Error('plugin error'))
    const result = await adapter.pickDirectory()
    expect(result.isErr()).toBe(true)
  })
})
