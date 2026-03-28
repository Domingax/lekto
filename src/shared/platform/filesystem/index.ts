import { Capacitor } from '@capacitor/core'
import { createWebFilesystemAdapter, setWebFilesystemRoot } from './filesystem.web'
import { createAndroidFilesystemAdapter } from './filesystem.android'
import type { FilesystemAdapter } from './filesystem.interface'

export type { FilesystemAdapter }

export const filesystemAdapter: FilesystemAdapter = Capacitor.isNativePlatform()
  ? createAndroidFilesystemAdapter()
  : createWebFilesystemAdapter()

// Platform-safe wrapper: configures the web adapter root; no-op on Android.
export function setFilesystemRoot(handle: FileSystemDirectoryHandle | null): void {
  if (!Capacitor.isNativePlatform()) {
    setWebFilesystemRoot(handle)
  }
}
