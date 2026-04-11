import { ok, err } from 'neverthrow'
import { filesystemAdapter, preferencesAdapter } from '../../../shared/platform'
import { useVaultStore } from '../../../shared/stores'
import type { AsyncResult } from '../../../shared/lib/types'
import { initDbForNewVault, runMigrations, seedLanguages } from '@/shared/db'

export const VAULT_PATH_KEY = 'vault_path'
export const DEFAULT_ANDROID_PATH = 'lekto-vault'
export const DESKTOP_DEFAULT_VAULT_NAME = 'lekto-vault'

export function getVaultPath(): AsyncResult<string> {
  return preferencesAdapter.get(VAULT_PATH_KEY)
}

export async function isVaultConfigured(): Promise<boolean> {
  const result = await getVaultPath()
  return result.isOk()
}

export async function initVault(path: string): AsyncResult<void> {
  try {
    const mkdirResult = await filesystemAdapter.mkdir('books')
    if (mkdirResult.isErr()) return err(mkdirResult.error)
    const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, path)
    if (setResult.isErr()) return err(setResult.error)
    useVaultStore.getState().setVaultPath(path)
    return ok(undefined)
  } catch (e) {
    return err(`Failed to init vault: ${e}`)
  }
}

export async function initVaultDesktop(vaultPath: string): AsyncResult<void> {
  try {
    const mkdirResult = await filesystemAdapter.mkdir(`${vaultPath}/books`)
    if (mkdirResult.isErr()) return err(mkdirResult.error)

    const dbResult = await initDbForNewVault(vaultPath)
    if (dbResult.isErr()) return err(`DB init failed: ${dbResult.error}`)

    const migrationsResult = await runMigrations()
    if (migrationsResult.isErr()) return err(`DB migration failed: ${migrationsResult.error}`)

    const seedResult = await seedLanguages()
    if (seedResult.isErr()) return err(`DB seed failed: ${seedResult.error}`)

    // Save vault path only after everything succeeded — prevents corrupt state on relaunch
    const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, vaultPath)
    if (setResult.isErr()) return err(setResult.error)

    useVaultStore.getState().setVaultPath(vaultPath)
    return ok(undefined)
  } catch (e) {
    return err(`Failed to init desktop vault: ${e}`)
  }
}
