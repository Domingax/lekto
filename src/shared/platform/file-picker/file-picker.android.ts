import { FilePicker } from '@capawesome/capacitor-file-picker'
import { ok, err } from 'neverthrow'
import type { FilePickerAdapter, PickedFile } from './file-picker.interface'
import type { AsyncResult } from '../../lib/types'

const EXT_TO_MIME: Record<string, string> = {
  '.epub': 'application/epub+zip',
  '.pdf': 'application/pdf',
}

function extToMime(exts: `.${string}`[]): string[] {
  return exts.map((e) => EXT_TO_MIME[e] ?? '*/*')
}

function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

export function createAndroidFilePickerAdapter(): FilePickerAdapter {
  return {
    async pickFile(options: { accept?: `.${string}`[] }): AsyncResult<PickedFile> {
      try {
        const result = await FilePicker.pickFiles({
          ...(options.accept !== undefined && { types: extToMime(options.accept) }),
          limit: 1,
          readData: true,
        })
        const file = result.files[0]
        if (!file) return err('No file selected')
        const data = file.data ? base64ToArrayBuffer(file.data) : new ArrayBuffer(0)
        return ok({ name: file.name, data })
      } catch {
        return err('File pick cancelled or failed')
      }
    },

    async pickDirectory(): AsyncResult<string> {
      try {
        const result = await FilePicker.pickDirectory()
        return ok(result.path)
      } catch {
        return err('Directory pick cancelled or failed')
      }
    },
  }
}
