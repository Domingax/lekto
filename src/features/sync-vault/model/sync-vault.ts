import { ok, err } from 'neverthrow'
import { filesystemAdapter, setFilesystemRoot, preferencesAdapter, isTauri } from '../../../shared/platform'
import { useVaultStore } from '../../../shared/stores'
import { storeVaultHandle, loadVaultHandle } from '../lib/handle-store'
import type { AsyncResult } from '../../../shared/lib/types'
import { initDb, runMigrations, seedLanguages } from '@/shared/db'

export const VAULT_PATH_KEY = 'vault_path'
export const WEB_OPFS_PATH = '__opfs__'
export const WEB_NATIVE_PATH = '__native__'
export const DEFAULT_ANDROID_PATH = 'lekto-vault'

export function getVaultPath(): AsyncResult<string> {
  return preferencesAdapter.get(VAULT_PATH_KEY)
}

export async function isVaultConfigured(): Promise<boolean> {
  const result = await getVaultPath()
  return result.isOk()
}

export async function initVault(path: string): AsyncResult<void> {
  try {
    if (path === WEB_OPFS_PATH) setFilesystemRoot(null)

    const mkdirResult = await filesystemAdapter.mkdir('books')
    if (mkdirResult.isErr()) return err(mkdirResult.error)

    const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, path)
    if (setResult.isErr()) return err(setResult.error)

    if (isTauri()) {
      const dbResult = await initDb()
      if (dbResult.isErr()) return err(`DB init failed: ${dbResult.error}`)
      if (dbResult.value === null) return err('DB init returned null unexpectedly')

      const migrationsResult = await runMigrations()
      if (migrationsResult.isErr()) return err(`DB migration failed: ${migrationsResult.error}`)

      const seedResult = await seedLanguages()
      if (seedResult.isErr()) return err(`DB seed failed: ${seedResult.error}`)
    }

    useVaultStore.getState().setVaultPath(path)
    return ok(undefined)
  } catch (e) {
    return err(`Failed to init vault: ${e}`)
  }
}

export async function initVaultWithNativeHandle(
  handle: FileSystemDirectoryHandle,
): AsyncResult<void> {
  try {
    await storeVaultHandle(handle)
    setFilesystemRoot(handle)

    const mkdirResult = await filesystemAdapter.mkdir('books')
    if (mkdirResult.isErr()) return err(mkdirResult.error)

    const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, WEB_NATIVE_PATH)
    if (setResult.isErr()) return err(setResult.error)

    useVaultStore.getState().setVaultPath(WEB_NATIVE_PATH)
    return ok(undefined)
  } catch (e) {
    return err(`Failed to init vault with native handle: ${e}`)
  }
}

export async function loadAndRestoreVaultHandle(): Promise<'ok' | 'needs-permission' | 'not-found'> {
  try {
    const handle = await loadVaultHandle()
    if (!handle) return 'not-found'

    const permission = await handle.queryPermission({ mode: 'readwrite' })
    if (permission === 'granted') {
      setFilesystemRoot(handle)
      useVaultStore.getState().setVaultPath(WEB_NATIVE_PATH)
      return 'ok'
    }

    useVaultStore.getState().setPendingPermissionHandle(handle)
    return 'needs-permission'
  } catch {
    return 'not-found'
  }
}

export async function grantVaultPermission(
  handle: FileSystemDirectoryHandle,
): AsyncResult<void> {
  const result = await handle.requestPermission({ mode: 'readwrite' })
  if (result !== 'granted') return err('Permission denied')

  setFilesystemRoot(handle)
  useVaultStore.getState().setVaultPath(WEB_NATIVE_PATH)
  return ok(undefined)
}
