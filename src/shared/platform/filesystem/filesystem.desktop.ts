import {
  readTextFile,
  writeTextFile,
  writeFile as tauriWriteFile,
  remove,
  mkdir,
  readDir,
  exists,
  copyFile as tauriCopyFile,
} from '@tauri-apps/plugin-fs'
import { ok, err } from 'neverthrow'
import type { FilesystemAdapter } from './filesystem.interface'
import type { AsyncResult } from '../../lib/types'

export function createDesktopFilesystemAdapter(): FilesystemAdapter {
  const desktopAdapter: FilesystemAdapter = {
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

    async writeFileBinary(path: string, data: Uint8Array): AsyncResult<void> {
      try {
        await tauriWriteFile(path, data)
        return ok(undefined)
      } catch (e) {
        const detail = e instanceof Error ? e.message : String(e)
        return err(`Failed to write binary file: ${path} — ${detail}`)
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
      } catch (e) {
        return err(`Failed to create directory: ${path} — ${e}`)
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

    async copyFile(src: string, dest: string): AsyncResult<void> {
      try {
        await tauriCopyFile(src, dest)
        return ok(undefined)
      } catch {
        return err(`Failed to copy file: ${src} → ${dest}`)
      }
    },

    async mkdirInVault(vaultPath: string, relativePath: string): AsyncResult<void> {
      return desktopAdapter.mkdir(`${vaultPath}/${relativePath}`)
    },

    async writeFileBinaryToVault(vaultPath: string, relativePath: string, data: Uint8Array): AsyncResult<void> {
      return desktopAdapter.writeFileBinary(`${vaultPath}/${relativePath}`, data)
    },

    async fileExistsInVault(vaultPath: string, relativePath: string): AsyncResult<boolean> {
      return desktopAdapter.exists(`${vaultPath}/${relativePath}`)
    },

    async takeVaultPermissions(_: string): AsyncResult<void> {
      return ok(undefined) // No-op on Desktop — Tauri uses absolute paths, no SAF
    },
  }
  return desktopAdapter
}
