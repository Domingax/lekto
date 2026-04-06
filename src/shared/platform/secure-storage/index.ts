import { isTauri } from '../is-tauri'
import { createAndroidSecureStorageAdapter } from './secure-storage.android'
import { createDesktopSecureStorageAdapter } from './secure-storage.desktop'
import type { SecureStorageAdapter } from './secure-storage.interface'

export type { SecureStorageAdapter }

export const secureStorageAdapter: SecureStorageAdapter = isTauri()
  ? createDesktopSecureStorageAdapter()
  : createAndroidSecureStorageAdapter()
