import { ok, err } from 'neverthrow'
import { filesystemAdapter, preferencesAdapter } from '../../../shared/platform'
import { useVaultStore } from '../../../shared/stores'
import type { AsyncResult } from '../../../shared/lib/types'
import { VAULT_PATH_KEY } from '../../../shared/lib'
export { VAULT_PATH_KEY } from '../../../shared/lib'
import { initDbForNewVault, importAndroidVaultDb, runMigrations, resetDb, seedLanguages } from '@/shared/db'
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

export async function openExistingVaultDesktop(vaultPath: string): AsyncResult<void> {
  try {
    const existsResult = await filesystemAdapter.exists(`${vaultPath}/lekto.db`)
    if (existsResult.isErr()) return err(existsResult.error)
    if (!existsResult.value) return err('This folder does not contain a valid Lekto vault')

    const dbResult = await initDbForNewVault(vaultPath)
    if (dbResult.isErr()) return err(`DB init failed: ${dbResult.error}`)

    const migrationsResult = await runMigrations()
    if (migrationsResult.isErr()) return err(`DB migration failed: ${migrationsResult.error}`)

    const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, vaultPath)
    if (setResult.isErr()) return err(setResult.error)

    useVaultStore.getState().setVaultPath(vaultPath)
    return ok(undefined)
  } catch (e) {
    return err(`Failed to open existing vault: ${e}`)
  }
}

export async function openExistingVaultAndroid(vaultPath: string): AsyncResult<void> {
  try {
    // Persist SAF permissions immediately — the temporary grant from pickDirectory
    // is only valid in the current session; takeVaultPermissions makes it survive restarts.
    const permResult = await filesystemAdapter.takeVaultPermissions(vaultPath)
    if (permResult.isErr()) return err(`Failed to persist vault permissions: ${permResult.error}`)

    // Existence is validated inside importAndroidVaultDb — the binary read
    // will fail with the vault-invalid error if lekto.db is absent at vaultPath.
    const dbResult = await importAndroidVaultDb(vaultPath)
    if (dbResult.isErr()) return err(dbResult.error)

    const migrationsResult = await runMigrations()
    if (migrationsResult.isErr()) return err(`DB migration failed: ${migrationsResult.error}`)

    const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, vaultPath)
    if (setResult.isErr()) return err(setResult.error)

    useVaultStore.getState().setVaultPath(vaultPath)
    return ok(undefined)
  } catch (e) {
    return err(`Failed to open existing vault (Android): ${e}`)
  }
}

export async function relocateVaultDesktop(newVaultPath: string): AsyncResult<void> {
  const currentVaultPath = useVaultStore.getState().vaultPath
  if (!currentVaultPath) return err('No active vault to relocate')

  try {
    const mkResult = await filesystemAdapter.mkdir(`${newVaultPath}/books`)
    if (mkResult.isErr()) return err(mkResult.error)

    const readdirResult = await filesystemAdapter.readdir(`${currentVaultPath}/books`)
    if (readdirResult.isErr()) return err(readdirResult.error)

    for (const filename of readdirResult.value) {
      const copyResult = await filesystemAdapter.copyFile(
        `${currentVaultPath}/books/${filename}`,
        `${newVaultPath}/books/${filename}`,
      )
      if (copyResult.isErr()) return err(copyResult.error)
    }

    // Reset before copying DB — connection must be closed before the file is accessed
    resetDb()

    const copyDbResult = await filesystemAdapter.copyFile(
      `${currentVaultPath}/lekto.db`,
      `${newVaultPath}/lekto.db`,
    )
    if (copyDbResult.isErr()) {
      await initDbForNewVault(currentVaultPath) // best-effort restore
      return err(copyDbResult.error)
    }

    const dbResult = await initDbForNewVault(newVaultPath)
    if (dbResult.isErr()) {
      await initDbForNewVault(currentVaultPath) // best-effort restore
      return err(`DB init at new location failed: ${dbResult.error}`)
    }

    const migrationsResult = await runMigrations()
    if (migrationsResult.isErr()) {
      await initDbForNewVault(currentVaultPath) // best-effort restore
      return err(`DB migration failed: ${migrationsResult.error}`)
    }

    const setResult = await preferencesAdapter.set(VAULT_PATH_KEY, newVaultPath)
    if (setResult.isErr()) return err(setResult.error)

    useVaultStore.getState().setVaultPath(newVaultPath)
    return ok(undefined)
  } catch (e) {
    return err(`Failed to relocate vault: ${e}`)
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
