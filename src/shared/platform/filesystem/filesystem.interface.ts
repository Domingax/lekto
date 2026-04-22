import type { AsyncResult } from '../../lib/types'

export interface FilesystemAdapter {
  readFile(path: string): AsyncResult<string>
  writeFile(path: string, data: string): AsyncResult<void>
  writeFileBinary(path: string, data: Uint8Array): AsyncResult<void>
  deleteFile(path: string): AsyncResult<void>
  mkdir(path: string): AsyncResult<void>
  readdir(path: string): AsyncResult<string[]>
  exists(path: string): AsyncResult<boolean>
  copyFile(src: string, dest: string): AsyncResult<void>
  /**
   * Create a directory relative to a vault root.
   * On Android, routes through the native VaultFs plugin when vaultPath is a SAF URI.
   * On Desktop, equivalent to mkdir(`${vaultPath}/${relativePath}`).
   */
  mkdirInVault(vaultPath: string, relativePath: string): AsyncResult<void>
  /**
   * Write binary data to a path relative to a vault root.
   * On Android, routes through the native VaultFs plugin when vaultPath is a SAF URI.
   * On Desktop, equivalent to writeFileBinary(`${vaultPath}/${relativePath}`, data).
   */
  writeFileBinaryToVault(vaultPath: string, relativePath: string, data: Uint8Array): AsyncResult<void>
  /**
   * Check whether a file exists at a path relative to a vault root.
   * On Android, routes through the native VaultFs plugin when vaultPath is a SAF URI.
   * On Desktop, equivalent to exists(`${vaultPath}/${relativePath}`).
   */
  fileExistsInVault(vaultPath: string, relativePath: string): AsyncResult<boolean>
  /**
   * Persist SAF read+write permissions for the vault path so they survive app restarts.
   * Must be called immediately after the user picks a vault directory.
   * No-op on Desktop and for non-SAF Android paths.
   */
  takeVaultPermissions(vaultPath: string): AsyncResult<void>
}
