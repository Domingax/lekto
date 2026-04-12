import { Filesystem, Directory, Encoding } from '@capacitor/filesystem'
import { ok, err } from 'neverthrow'
import type { FilesystemAdapter } from './filesystem.interface'
import type { AsyncResult } from '../../lib/types'

const BASE_DIR = Directory.Documents

export function createAndroidFilesystemAdapter(): FilesystemAdapter {
  return {
    async readFile(path: string): AsyncResult<string> {
      try {
        const result = await Filesystem.readFile({
          path,
          directory: BASE_DIR,
          encoding: Encoding.UTF8,
        })
        const data =
          typeof result.data === 'string'
            ? result.data
            : await (result.data as Blob).text()
        return ok(data)
      } catch {
        return err(`Failed to read file: ${path}`)
      }
    },

    async writeFile(path: string, data: string): AsyncResult<void> {
      try {
        await Filesystem.writeFile({
          path,
          data,
          directory: BASE_DIR,
          encoding: Encoding.UTF8,
          recursive: true,
        })
        return ok(undefined)
      } catch {
        return err(`Failed to write file: ${path}`)
      }
    },

    async writeFileBinary(path: string, data: Uint8Array): AsyncResult<void> {
      try {
        let binary = ''
        data.forEach((b) => (binary += String.fromCharCode(b)))
        await Filesystem.writeFile({ path, data: btoa(binary), directory: BASE_DIR, recursive: true })
        return ok(undefined)
      } catch (e) {
        const detail = e instanceof Error ? e.message : String(e)
        return err(`Failed to write binary file: ${path} — ${detail}`)
      }
    },

    async deleteFile(path: string): AsyncResult<void> {
      try {
        await Filesystem.deleteFile({ path, directory: BASE_DIR })
        return ok(undefined)
      } catch {
        return err(`Failed to delete file: ${path}`)
      }
    },

    async mkdir(path: string): AsyncResult<void> {
      try {
        await Filesystem.mkdir({ path, directory: BASE_DIR, recursive: true })
        return ok(undefined)
      } catch (e) {
        if (e != null && typeof e === 'object' && 'code' in e && e.code === 'OS-PLUG-FILE-0010') {
          return ok(undefined)
        }
        return err(`Failed to create directory: ${path}`)
      }
    },

    async readdir(path: string): AsyncResult<string[]> {
      try {
        const result = await Filesystem.readdir({ path, directory: BASE_DIR })
        const names = result.files.map((f) => f.name)
        return ok(names)
      } catch {
        return err(`Failed to read directory: ${path}`)
      }
    },

    async exists(path: string): AsyncResult<boolean> {
      try {
        await Filesystem.stat({ path, directory: BASE_DIR })
        return ok(true)
      } catch (e) {
        const msg = e != null && typeof e === 'object' && 'message' in e ? String(e.message) : ''
        const isNotFound =
          msg.toLowerCase().includes('not found') ||
          msg.includes('does not exist') ||
          msg.includes('ENOENT') ||
          msg.includes('No such file')
        if (isNotFound) return ok(false)
        return err(`Failed to check file existence: ${path}`)
      }
    },

    async copyFile(src: string, dest: string): AsyncResult<void> {
      try {
        await Filesystem.copy({ from: src, to: dest })
        return ok(undefined)
      } catch {
        return err(`Failed to copy file: ${src} → ${dest}`)
      }
    },
  }
}
