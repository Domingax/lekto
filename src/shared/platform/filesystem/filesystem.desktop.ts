import {
  readTextFile,
  writeTextFile,
  remove,
  mkdir,
  readDir,
  exists,
} from '@tauri-apps/plugin-fs'
import { ok, err } from 'neverthrow'
import type { FilesystemAdapter } from './filesystem.interface'
import type { AsyncResult } from '../../lib/types'

export function createDesktopFilesystemAdapter(): FilesystemAdapter {
  return {
    async readFile(path: string): AsyncResult<string> {
      try {
        const content = await readTextFile(path)
        return ok(content)
      } catch {
        return err(`Failed to read file: ${path}`)
      }
    },

    async writeFile(path: string, data: string): AsyncResult<void> {
      try {
        await writeTextFile(path, data)
        return ok(undefined)
      } catch {
        return err(`Failed to write file: ${path}`)
      }
    },

    async deleteFile(path: string): AsyncResult<void> {
      try {
        await remove(path)
        return ok(undefined)
      } catch {
        return err(`Failed to delete file: ${path}`)
      }
    },

    async mkdir(path: string): AsyncResult<void> {
      try {
        await mkdir(path, { recursive: true })
        return ok(undefined)
      } catch {
        return err(`Failed to create directory: ${path}`)
      }
    },

    async readdir(path: string): AsyncResult<string[]> {
      try {
        const entries = await readDir(path)
        const names = entries.map((e) => e.name ?? '')
        return ok(names)
      } catch {
        return err(`Failed to read directory: ${path}`)
      }
    },

    async exists(path: string): AsyncResult<boolean> {
      try {
        const result = await exists(path)
        return ok(result)
      } catch {
        return ok(false)
      }
    },
  }
}
