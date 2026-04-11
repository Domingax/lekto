import { isTauri } from '../is-tauri'
import { createAndroidFilesystemAdapter } from './filesystem.android'
import { createDesktopFilesystemAdapter } from './filesystem.desktop'
import type { FilesystemAdapter } from './filesystem.interface'

export type { FilesystemAdapter }

export const filesystemAdapter: FilesystemAdapter = isTauri()
  ? createDesktopFilesystemAdapter()
  : createAndroidFilesystemAdapter()
