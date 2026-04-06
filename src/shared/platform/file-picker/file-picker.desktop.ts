import { open } from '@tauri-apps/plugin-dialog'
import { readFile } from '@tauri-apps/plugin-fs'
import { ok, err } from 'neverthrow'
import type { FilePickerAdapter, PickedFile } from './file-picker.interface'
import type { AsyncResult } from '../../lib/types'

export function createDesktopFilePickerAdapter(): FilePickerAdapter {
  return {
    async pickFile(options: { accept?: `.${string}`[] }): AsyncResult<PickedFile> {
      try {
        const extensions = (options.accept ?? []).map((ext) => ext.slice(1)) // remove leading dot
        const selected = await open({
          multiple: false,
          filters:
            extensions.length > 0
              ? [{ name: 'Book files', extensions }]
              : undefined,
        })
        if (selected === null) return err('File pick cancelled or failed')
        const path = typeof selected === 'string' ? selected : selected[0]
        if (!path) return err('File pick cancelled or failed')
        const bytes = await readFile(path)
        const name = path.split('/').at(-1) ?? path.split('\\').at(-1) ?? path
        return ok({ name, data: bytes.buffer as ArrayBuffer })
      } catch {
        return err('File pick cancelled or failed')
      }
    },

    async pickDirectory(): AsyncResult<string> {
      try {
        const selected = await open({ directory: true })
        if (selected === null) return err('Directory pick cancelled or failed')
        return ok(typeof selected === 'string' ? selected : selected[0] ?? '')
      } catch {
        return err('Directory pick cancelled or failed')
      }
    },
  }
}
