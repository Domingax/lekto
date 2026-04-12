import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ok, err } from 'neverthrow'

vi.mock('@/shared/platform', () => ({
  filesystemAdapter: {
    mkdir: vi.fn(),
    readdir: vi.fn(),
    copyFile: vi.fn(),
    exists: vi.fn(),
  },
  preferencesAdapter: {
    get: vi.fn(),
    set: vi.fn(),
  },
}))

vi.mock('@/shared/db', () => ({
  initDbForNewVault: vi.fn(),
  runMigrations: vi.fn(),
  resetDb: vi.fn(),
}))

vi.mock('@/shared/stores', () => ({
  useVaultStore: {
    getState: vi.fn(),
  },
}))

import { filesystemAdapter, preferencesAdapter } from '@/shared/platform'
import { initDbForNewVault, runMigrations, resetDb } from '@/shared/db'
import { useVaultStore } from '@/shared/stores'
import { VAULT_PATH_KEY } from '@/shared/lib'

const CURRENT_PATH = '/current/vault'
const NEW_PATH = '/new/vault'

describe('relocateVaultDesktop', () => {
  let setVaultPath: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.resetAllMocks()
    setVaultPath = vi.fn()
    vi.mocked(useVaultStore.getState).mockReturnValue({
      vaultPath: CURRENT_PATH,
      setVaultPath,
    } as never)
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(ok(undefined))
    vi.mocked(filesystemAdapter.readdir).mockResolvedValue(ok(['book1.epub', 'book2.epub']))
    vi.mocked(filesystemAdapter.copyFile).mockResolvedValue(ok(undefined))
    vi.mocked(initDbForNewVault).mockResolvedValue(ok({} as never))
    vi.mocked(runMigrations).mockResolvedValue(ok(undefined))
    vi.mocked(preferencesAdapter.set).mockResolvedValue(ok(undefined))
  })

  it('returns ok and updates store when migration succeeds', async () => {
    const { relocateVaultDesktop } = await import('./model/sync-vault')
    const result = await relocateVaultDesktop(NEW_PATH)

    expect(result.isOk()).toBe(true)
    expect(setVaultPath).toHaveBeenCalledWith(NEW_PATH)
    expect(filesystemAdapter.mkdir).toHaveBeenCalledWith(`${NEW_PATH}/books`)
    expect(filesystemAdapter.readdir).toHaveBeenCalledWith(`${CURRENT_PATH}/books`)
    expect(filesystemAdapter.copyFile).toHaveBeenCalledWith(
      `${CURRENT_PATH}/books/book1.epub`,
      `${NEW_PATH}/books/book1.epub`,
    )
    expect(filesystemAdapter.copyFile).toHaveBeenCalledWith(
      `${CURRENT_PATH}/books/book2.epub`,
      `${NEW_PATH}/books/book2.epub`,
    )
    expect(resetDb).toHaveBeenCalled()
    expect(filesystemAdapter.copyFile).toHaveBeenCalledWith(
      `${CURRENT_PATH}/lekto.db`,
      `${NEW_PATH}/lekto.db`,
    )
    expect(initDbForNewVault).toHaveBeenCalledWith(NEW_PATH)
    expect(preferencesAdapter.set).toHaveBeenCalledWith(VAULT_PATH_KEY, NEW_PATH)
  })

  it('returns err immediately when no active vault', async () => {
    vi.mocked(useVaultStore.getState).mockReturnValue({ vaultPath: null, setVaultPath } as never)
    const { relocateVaultDesktop } = await import('./model/sync-vault')

    const result = await relocateVaultDesktop(NEW_PATH)

    expect(result.isErr()).toBe(true)
    expect(filesystemAdapter.mkdir).not.toHaveBeenCalled()
    expect(setVaultPath).not.toHaveBeenCalled()
  })

  it('returns err and leaves store unchanged when mkdir fails', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(err('mkdir failed'))
    const { relocateVaultDesktop } = await import('./model/sync-vault')

    const result = await relocateVaultDesktop(NEW_PATH)

    expect(result.isErr()).toBe(true)
    expect(setVaultPath).not.toHaveBeenCalled()
    expect(resetDb).not.toHaveBeenCalled()
  })

  it('returns err when copyFile for a book fails (resetDb not yet called)', async () => {
    vi.mocked(filesystemAdapter.copyFile).mockResolvedValueOnce(err('copy failed'))
    const { relocateVaultDesktop } = await import('./model/sync-vault')

    const result = await relocateVaultDesktop(NEW_PATH)

    expect(result.isErr()).toBe(true)
    expect(resetDb).not.toHaveBeenCalled()
    expect(setVaultPath).not.toHaveBeenCalled()
  })

  it('returns err and restores original DB when copyFile for lekto.db fails', async () => {
    // First two calls succeed (books), third call (lekto.db copy) fails
    vi.mocked(filesystemAdapter.copyFile)
      .mockResolvedValueOnce(ok(undefined))
      .mockResolvedValueOnce(ok(undefined))
      .mockResolvedValueOnce(err('db copy failed'))
    const { relocateVaultDesktop } = await import('./model/sync-vault')

    const result = await relocateVaultDesktop(NEW_PATH)

    expect(result.isErr()).toBe(true)
    expect(resetDb).toHaveBeenCalled()
    expect(initDbForNewVault).toHaveBeenCalledWith(CURRENT_PATH)
    expect(setVaultPath).not.toHaveBeenCalled()
  })

  it('returns err and restores original DB when initDbForNewVault at new path fails', async () => {
    vi.mocked(initDbForNewVault).mockResolvedValueOnce(err('db init failed'))
    const { relocateVaultDesktop } = await import('./model/sync-vault')

    const result = await relocateVaultDesktop(NEW_PATH)

    expect(result.isErr()).toBe(true)
    expect(initDbForNewVault).toHaveBeenCalledWith(NEW_PATH)
    expect(initDbForNewVault).toHaveBeenCalledWith(CURRENT_PATH)
    expect(setVaultPath).not.toHaveBeenCalled()
  })

  it('returns err and restores original DB when runMigrations fails', async () => {
    vi.mocked(runMigrations).mockResolvedValueOnce(err('migration failed'))
    const { relocateVaultDesktop } = await import('./model/sync-vault')

    const result = await relocateVaultDesktop(NEW_PATH)

    expect(result.isErr()).toBe(true)
    expect(initDbForNewVault).toHaveBeenCalledWith(NEW_PATH)
    expect(initDbForNewVault).toHaveBeenCalledWith(CURRENT_PATH)
    expect(setVaultPath).not.toHaveBeenCalled()
  })
})
