import { Filesystem, Directory, Encoding } from '@capacitor/filesystem'
import { ok, err } from 'neverthrow'
import { VaultFs } from './vault-fs.android'
import type { FilesystemAdapter } from './filesystem.interface'
import type { AsyncResult } from '../../lib/types'

const BASE_DIR = Directory.Documents

function isSafUri(path: string): boolean {
  return path.startsWith('content://')
}

function uint8ToBase64(data: Uint8Array): string {
  let binary = ''
  data.forEach((b) => (binary += String.fromCodePoint(b)))
  return btoa(binary)
}

export function createAndroidFilesystemAdapter(): FilesystemAdapter {
  const adapter: FilesystemAdapter = {
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
        await Filesystem.writeFile({ path, data: uint8ToBase64(data), directory: BASE_DIR, recursive: true })
        return ok(undefined)
      } catch (e) {
        const detail = e instanceof Error ? e.message : String(e)
        return err(`Failed to write binary file: ${path} — ${detail}`)
      }
    },

    async mkdirInVault(vaultPath: string, relativePath: string): AsyncResult<void> {
      if (isSafUri(vaultPath)) {
        try {
          await VaultFs.mkdir({ treeUri: vaultPath, path: relativePath })
          return ok(undefined)
        } catch (e) {
          const detail = e instanceof Error ? e.message : String(e)
          return err(`Failed to create vault directory: ${relativePath} — ${detail}`)
        }
      }
      return adapter.mkdir(`${vaultPath}/${relativePath}`)
    },

    async writeFileBinaryToVault(vaultPath: string, relativePath: string, data: Uint8Array): AsyncResult<void> {
      if (isSafUri(vaultPath)) {
        try {
          await VaultFs.writeFile({ treeUri: vaultPath, path: relativePath, data: uint8ToBase64(data) })
          return ok(undefined)
        } catch (e) {
          const detail = e instanceof Error ? e.message : String(e)
          return err(`Failed to write binary file to vault: ${relativePath} — ${detail}`)
        }
      }
      return adapter.writeFileBinary(`${vaultPath}/${relativePath}`, data)
    },

    async fileExistsInVault(vaultPath: string, relativePath: string): AsyncResult<boolean> {
      if (isSafUri(vaultPath)) {
        try {
          const result = await VaultFs.fileExists({ treeUri: vaultPath, path: relativePath })
          return ok(result.exists)
        } catch (e) {
          const detail = e instanceof Error ? e.message : String(e)
          return err(`Failed to check file existence in vault: ${relativePath} — ${detail}`)
        }
      }
      return adapter.exists(`${vaultPath}/${relativePath}`)
    },

    async deleteFileInVault(vaultPath: string, relativePath: string): AsyncResult<void> {
      if (isSafUri(vaultPath)) {
        try {
          await VaultFs.deleteFile({ treeUri: vaultPath, path: relativePath })
          return ok(undefined)
        } catch (e) {
          const detail = e instanceof Error ? e.message : String(e)
          return err(`Failed to delete file in vault: ${relativePath} — ${detail}`)
        }
      }
      return adapter.deleteFile(`${vaultPath}/${relativePath}`)
    },

    async takeVaultPermissions(vaultPath: string): AsyncResult<void> {
      if (!isSafUri(vaultPath)) return ok(undefined)
      try {
        await VaultFs.takePermissions({ treeUri: vaultPath })
        return ok(undefined)
      } catch (e) {
        const detail = e instanceof Error ? e.message : String(e)
        return err(`Failed to persist vault permissions — ${detail}`)
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
  return adapter
}
