/**
 * vault-db.android.ts tests — TDD, Red-Green-Refactor.
 *
 * Dependencies are injected via createAndroidVaultDbAdapter(deps) so tests
 * never rely on dynamic-import mocking.
 */
import { describe, it, expect, vi } from 'vitest'
import { createAndroidVaultDbAdapter } from './vault-db.android'
import type { VaultDbDeps } from './vault-db.android'

const VAULT_PATH = '/storage/emulated/0/Documents/my-vault'
const INTERNAL_PATH = '/data/user/0/com.lekto.app/databases/lektoSQLite.db'
const BASE64_DATA = 'U1FMaXRlZm9ybWF0IDM='

function makeDeps(overrides: Partial<VaultDbDeps> = {}): VaultDbDeps {
  const mockGetUrl = vi.fn().mockResolvedValue({ url: INTERNAL_PATH })
  const mockOpen = vi.fn().mockResolvedValue(undefined)
  const mockConn = { getUrl: mockGetUrl, open: mockOpen }

  const filesystem = {
    readFile: vi.fn().mockResolvedValue({ data: BASE64_DATA }),
    writeFile: vi.fn().mockResolvedValue(undefined),
    ...overrides.filesystem,
  }

  const sqlite = {
    isDatabase: vi.fn().mockResolvedValue({ result: false }),
    retrieveConnection: vi.fn().mockResolvedValue(mockConn),
    createConnection: vi.fn().mockResolvedValue(mockConn),
    closeAllConnections: vi.fn().mockResolvedValue(undefined),
    ...overrides.sqlite,
  }

  return { filesystem, sqlite }
}

describe('createAndroidVaultDbAdapter — replaceVaultDb', () => {
  describe('no existing DB (isDatabase → false)', () => {
    it('reads binary, creates temp connection, gets URL, closes all, writes binary', async () => {
      const deps = makeDeps()
      const adapter = createAndroidVaultDbAdapter(deps)

      const result = await adapter.replaceVaultDb(VAULT_PATH)

      expect(result.isOk()).toBe(true)
      expect(deps.filesystem.readFile).toHaveBeenCalledWith({ path: `${VAULT_PATH}/lekto.db` })
      expect(deps.sqlite.createConnection).toHaveBeenCalledWith('lekto', false, 'no-encryption', 1, false)
      expect(deps.sqlite.closeAllConnections).toHaveBeenCalled()
      expect(deps.filesystem.writeFile).toHaveBeenCalledWith({
        path: INTERNAL_PATH,
        data: BASE64_DATA,
      })
    })
  })

  describe('existing DB (isDatabase → true)', () => {
    it('retrieves existing connection — no createConnection called', async () => {
      const deps = makeDeps({
        sqlite: { isDatabase: vi.fn().mockResolvedValue({ result: true }) } as never,
      })
      const adapter = createAndroidVaultDbAdapter(deps)

      const result = await adapter.replaceVaultDb(VAULT_PATH)

      expect(result.isOk()).toBe(true)
      expect(deps.sqlite.retrieveConnection).toHaveBeenCalledWith('lekto', false)
      expect(deps.sqlite.createConnection).not.toHaveBeenCalled()
      expect(deps.filesystem.writeFile).toHaveBeenCalledWith({
        path: INTERNAL_PATH,
        data: BASE64_DATA,
      })
    })

    it('retrieveConnection throws (stale ref) — falls back to createConnection', async () => {
      const deps = makeDeps({
        sqlite: {
          isDatabase: vi.fn().mockResolvedValue({ result: true }),
          retrieveConnection: vi.fn().mockRejectedValue(new Error('no connection in dict')),
        } as never,
      })
      const adapter = createAndroidVaultDbAdapter(deps)

      const result = await adapter.replaceVaultDb(VAULT_PATH)

      expect(result.isOk()).toBe(true)
      expect(deps.sqlite.createConnection).toHaveBeenCalledWith('lekto', false, 'no-encryption', 1, false)
    })
  })

  describe('closeAllConnections called before writeFile (L3 — teardown order)', () => {
    it('close happens before write', async () => {
      const order: string[] = []
      const deps = makeDeps()
      vi.mocked(deps.sqlite.closeAllConnections).mockImplementation(async () => {
        order.push('close')
      })
      vi.mocked(deps.filesystem.writeFile).mockImplementation(async () => {
        order.push('write')
      })
      const adapter = createAndroidVaultDbAdapter(deps)

      await adapter.replaceVaultDb(VAULT_PATH)

      expect(order).toEqual(['close', 'write'])
    })
  })

  describe('handles Blob data from readFile', () => {
    it('converts Blob to string before write', async () => {
      const deps = makeDeps({
        filesystem: { readFile: vi.fn().mockResolvedValue({ data: new Blob(['binary']) }) } as never,
      })
      const adapter = createAndroidVaultDbAdapter(deps)

      const result = await adapter.replaceVaultDb(VAULT_PATH)

      expect(result.isOk()).toBe(true)
      expect(deps.filesystem.writeFile).toHaveBeenCalled()
    })
  })

  describe('error paths', () => {
    it('lekto.db absent — readFile throws → vault-invalid error, no side effects', async () => {
      const deps = makeDeps({
        filesystem: { readFile: vi.fn().mockRejectedValue(new Error('File not found')) } as never,
      })
      const adapter = createAndroidVaultDbAdapter(deps)

      const result = await adapter.replaceVaultDb(VAULT_PATH)

      expect(result.isErr()).toBe(true)
      expect(result._unsafeUnwrapErr()).toBe('This folder does not contain a valid Lekto vault')
      expect(deps.sqlite.createConnection).not.toHaveBeenCalled()
      expect(deps.filesystem.writeFile).not.toHaveBeenCalled()
    })

    it('getUrl returns empty string → error about internal path, writeFile not called', async () => {
      const mockGetUrl = vi.fn().mockResolvedValue({ url: '' })
      const deps = makeDeps({
        sqlite: {
          createConnection: vi.fn().mockResolvedValue({ getUrl: mockGetUrl, open: vi.fn() }),
        } as never,
      })
      const adapter = createAndroidVaultDbAdapter(deps)

      const result = await adapter.replaceVaultDb(VAULT_PATH)

      expect(result.isErr()).toBe(true)
      expect(result._unsafeUnwrapErr()).toContain('internal database path')
      expect(deps.filesystem.writeFile).not.toHaveBeenCalled()
    })

    it('writeFile throws → returns error with cause', async () => {
      const deps = makeDeps({
        filesystem: {
          writeFile: vi.fn().mockRejectedValue(new Error('permission denied')),
        } as never,
      })
      const adapter = createAndroidVaultDbAdapter(deps)

      const result = await adapter.replaceVaultDb(VAULT_PATH)

      expect(result.isErr()).toBe(true)
      expect(result._unsafeUnwrapErr()).toContain('permission denied')
    })
  })
})
