import { isTauri } from '../is-tauri'
import { setWebFilesystemRoot } from './filesystem.web'
import { createAndroidFilesystemAdapter } from './filesystem.android'
import { createDesktopFilesystemAdapter } from './filesystem.desktop'
import type { FilesystemAdapter } from './filesystem.interface'

export type { FilesystemAdapter }

export const filesystemAdapter: FilesystemAdapter = isTauri()
  ? createDesktopFilesystemAdapter()
  : createAndroidFilesystemAdapter()

// No-op on desktop and Android; only used for web OPFS flows.
export function setFilesystemRoot(handle: FileSystemDirectoryHandle | null): void {
  if (!isTauri()) {
    setWebFilesystemRoot(handle)
  }
}
