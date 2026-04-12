import { registerPlugin } from '@capacitor/core'

export interface VaultFsPlugin {
  /** Write base64-encoded binary data to a path relative to a SAF tree URI. */
  writeFile(options: { treeUri: string; path: string; data: string }): Promise<void>
  /** Create a directory (and intermediate parents) relative to a SAF tree URI. */
  mkdir(options: { treeUri: string; path: string }): Promise<void>
}

/**
 * Bridge to the native VaultFsPlugin (Android only).
 * Used when the vault path is a SAF content:// URI from the directory picker.
 */
export const VaultFs = registerPlugin<VaultFsPlugin>('VaultFs')
