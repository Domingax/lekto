import { Filesystem as CapacitorFilesystem } from '@capacitor/filesystem'
import { CapacitorSQLite, SQLiteConnection as CapacitorSQLiteConnection } from '@capacitor-community/sqlite'
import { ok, err } from 'neverthrow'
import type { AsyncResult } from '../../lib/types'

export interface VaultDbAdapter {
  replaceVaultDb(vaultPath: string): AsyncResult<void>
}

// Minimal shape of the Capacitor Filesystem API used here
interface FileReadResult {
  data: string | Blob
}
interface FileSystem {
  readFile(options: { path: string }): Promise<FileReadResult>
  writeFile(options: { path: string; data: string }): Promise<unknown>
}

// Minimal shape of the SQLite plugin connection
interface SqliteConn {
  getUrl(): Promise<{ url?: string }>
  open(): Promise<void>
}

// Minimal shape of the SQLiteConnection manager
interface SqliteManager {
  isDatabase(name: string): Promise<{ result?: boolean }>
  retrieveConnection(name: string, readonly: boolean): Promise<SqliteConn>
  createConnection(
    name: string,
    encrypted: boolean,
    mode: string,
    version: number,
    readonly: boolean,
  ): Promise<SqliteConn>
  closeAllConnections(): Promise<void>
}

// Dependencies injected so the adapter is testable without dynamic-import mocking
export interface VaultDbDeps {
  filesystem: FileSystem
  sqlite: SqliteManager
}

export function createAndroidVaultDbAdapter(deps?: VaultDbDeps): VaultDbAdapter {
  return {
    async replaceVaultDb(vaultPath: string) {
      // In production, use the real Capacitor modules (static imports above).
      // In tests, use the injected deps so Capacitor is never called in jsdom.
      const Filesystem = deps ? deps.filesystem : CapacitorFilesystem
      const sqlite: SqliteManager = deps
        ? deps.sqlite
        : new CapacitorSQLiteConnection(CapacitorSQLite)

      // Step 1 — Read the vault binary.
      // Filesystem.readFile without `directory` treats path as absolute —
      // correct for paths returned by FilePicker.pickDirectory() on Android.
      // A throw means lekto.db is absent → vault is invalid.
      let base64Data: string
      try {
        const fileResult = await Filesystem.readFile({ path: `${vaultPath}/lekto.db` })
        base64Data =
          typeof fileResult.data === 'string'
            ? fileResult.data
            : await fileResult.data.text()
      } catch {
        return err('This folder does not contain a valid Lekto vault')
      }

      // Step 2 — Discover the internal path dynamically via the connection URL.
      // This avoids hardcoding the package name or Android user ID.
      let internalPath: string
      try {
        const isDbResult = await sqlite.isDatabase('lekto')
        let conn: SqliteConn
        if (isDbResult.result) {
          try {
            conn = await sqlite.retrieveConnection('lekto', false)
          } catch {
            // Connection object gone (stale) — recreate it
            conn = await sqlite.createConnection('lekto', false, 'no-encryption', 1, false)
            await conn.open()
          }
        } else {
          conn = await sqlite.createConnection('lekto', false, 'no-encryption', 1, false)
          await conn.open()
        }
        const urlResult = await conn.getUrl()
        internalPath = urlResult.url ?? ''
        if (!internalPath) throw new Error('Could not determine internal database path')
        await sqlite.closeAllConnections()
      } catch (e) {
        return err(e instanceof Error ? e.message : String(e))
      }

      // Step 3 — Overwrite the internal DB file.
      // internalPath is absolute; no `directory` option needed.
      try {
        await Filesystem.writeFile({ path: internalPath, data: base64Data })
        return ok(undefined)
      } catch (e) {
        return err(e instanceof Error ? e.message : String(e))
      }
    },
  }
}
