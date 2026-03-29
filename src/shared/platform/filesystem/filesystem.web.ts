import { ok, err } from 'neverthrow'
import type { FilesystemAdapter } from './filesystem.interface'
import type { AsyncResult } from '../../lib/types'

// Native folder root set by sync-vault when user picks a folder via showDirectoryPicker.
// Falls back to OPFS when null (Firefox/Safari, or OPFS default on Chrome/Edge).
let _nativeRoot: FileSystemDirectoryHandle | null = null

export function setWebFilesystemRoot(handle: FileSystemDirectoryHandle | null): void {
  _nativeRoot = handle
}

function getRoot(): Promise<FileSystemDirectoryHandle> {
  if (_nativeRoot) return Promise.resolve(_nativeRoot)
  return navigator.storage.getDirectory()
}

async function resolveFile(
  path: string,
  create = false,
): Promise<FileSystemFileHandle> {
  const root = await getRoot()
  const parts = path.split('/').filter(Boolean)
  const fileName = parts.at(-1)
  if (!fileName) throw new Error(`Invalid path: ${path}`)

  let dir: FileSystemDirectoryHandle = root
  for (const part of parts.slice(0, -1)) {
    dir = await dir.getDirectoryHandle(part, { create })
  }
  return dir.getFileHandle(fileName, { create })
}

async function resolveDir(
  path: string,
  create = false,
): Promise<FileSystemDirectoryHandle> {
  const root = await getRoot()
  const parts = path.split('/').filter((p) => p !== '' && p !== '.')
  let dir: FileSystemDirectoryHandle = root
  for (const part of parts) {
    dir = await dir.getDirectoryHandle(part, { create })
  }
  return dir
}

export function createWebFilesystemAdapter(): FilesystemAdapter {
  return {
    async readFile(path: string): AsyncResult<string> {
      try {
        const fileHandle = await resolveFile(path)
        const file = await fileHandle.getFile()
        const content = await file.text()
        return ok(content)
      } catch {
        return err(`Failed to read file: ${path}`)
      }
    },

    async writeFile(path: string, data: string): AsyncResult<void> {
      let writable: FileSystemWritableFileStream | undefined
      try {
        const fileHandle = await resolveFile(path, true)
        writable = await fileHandle.createWritable()
        await writable.write(data)
        await writable.close()
        return ok(undefined)
      } catch {
        await writable?.abort()
        return err(`Failed to write file: ${path}`)
      }
    },

    async deleteFile(path: string): AsyncResult<void> {
      try {
        const root = await getRoot()
        const parts = path.split('/').filter(Boolean)
        const fileName = parts.at(-1)
        if (!fileName) return err(`Invalid path: ${path}`)
        let dir: FileSystemDirectoryHandle = root
        for (const part of parts.slice(0, -1)) {
          dir = await dir.getDirectoryHandle(part)
        }
        await dir.removeEntry(fileName)
        return ok(undefined)
      } catch {
        return err(`Failed to delete file: ${path}`)
      }
    },

    async mkdir(path: string): AsyncResult<void> {
      try {
        await resolveDir(path, true)
        return ok(undefined)
      } catch {
        return err(`Failed to create directory: ${path}`)
      }
    },

    async readdir(path: string): AsyncResult<string[]> {
      try {
        const dir = await resolveDir(path)
        const names: string[] = []
        for await (const [name] of dir.entries()) {
          names.push(name)
        }
        return ok(names)
      } catch {
        return err(`Failed to read directory: ${path}`)
      }
    },

    async exists(path: string): AsyncResult<boolean> {
      try {
        await resolveFile(path)
        return ok(true)
      } catch {
        try {
          await resolveDir(path)
          return ok(true)
        } catch {
          return ok(false)
        }
      }
    },
  }
}
