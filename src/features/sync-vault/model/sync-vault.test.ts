import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ok, err } from 'neverthrow'

vi.mock('../../../shared/platform', () => ({
  filesystemAdapter: {
    mkdir: vi.fn(),
  },
  preferencesAdapter: {
    get: vi.fn(),
    set: vi.fn(),
  },
}))

vi.mock('@/shared/db', () => ({
  initDbForNewVault: vi.fn(),
  runMigrations: vi.fn(),
  seedLanguages: vi.fn(),
}))

vi.mock('../../../shared/stores', () => ({
  useVaultStore: {
    getState: vi.fn(),
  },
}))

import {
  VAULT_PATH_KEY,
  DEFAULT_ANDROID_PATH,
  DESKTOP_DEFAULT_VAULT_NAME,
  getVaultPath,
  isVaultConfigured,
  initVault,
  initVaultDesktop,
} from './sync-vault'
import { filesystemAdapter, preferencesAdapter } from '../../../shared/platform'
import { initDbForNewVault, runMigrations, seedLanguages } from '@/shared/db'
import { useVaultStore } from '../../../shared/stores'

const mockSetVaultPath = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(useVaultStore.getState).mockReturnValue({
    vaultPath: null,
    isVaultReady: false,
    setVaultPath: mockSetVaultPath,
    clearVault: vi.fn(),
  })
})

describe('constants', () => {
  it('exports expected values', () => {
    expect(VAULT_PATH_KEY).toBe('vault_path')
    expect(DEFAULT_ANDROID_PATH).toBe('lekto-vault')
    expect(DESKTOP_DEFAULT_VAULT_NAME).toBe('lekto-vault')
  })
})

describe('getVaultPath', () => {
  it('returns ok when preferences has vault path', async () => {
    vi.mocked(preferencesAdapter.get).mockResolvedValue(ok('/some/path'))
    const result = await getVaultPath()
    expect(result.isOk()).toBe(true)
    expect(preferencesAdapter.get).toHaveBeenCalledWith(VAULT_PATH_KEY)
  })

  it('returns err when no vault path stored', async () => {
    vi.mocked(preferencesAdapter.get).mockResolvedValue(err('Not found'))
    const result = await getVaultPath()
    expect(result.isErr()).toBe(true)
  })
})

describe('isVaultConfigured', () => {
  it('returns true when vault path is set', async () => {
    vi.mocked(preferencesAdapter.get).mockResolvedValue(ok('lekto-vault'))
    expect(await isVaultConfigured()).toBe(true)
  })

  it('returns false when vault path is not set', async () => {
    vi.mocked(preferencesAdapter.get).mockResolvedValue(err('Not found'))
    expect(await isVaultConfigured()).toBe(false)
  })
})

describe('initVault', () => {
  it('creates books dir, persists path, updates store', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(ok(undefined))
    vi.mocked(preferencesAdapter.set).mockResolvedValue(ok(undefined))

    const result = await initVault('lekto-vault')

    expect(result.isOk()).toBe(true)
    expect(filesystemAdapter.mkdir).toHaveBeenCalledWith('books')
    expect(preferencesAdapter.set).toHaveBeenCalledWith(VAULT_PATH_KEY, 'lekto-vault')
    expect(mockSetVaultPath).toHaveBeenCalledWith('lekto-vault')
  })

  it('returns err when mkdir fails', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(err('no space'))
    const result = await initVault('lekto-vault')
    expect(result.isErr()).toBe(true)
  })

  it('returns err when preferences.set fails', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(ok(undefined))
    vi.mocked(preferencesAdapter.set).mockResolvedValue(err('storage full'))
    const result = await initVault('lekto-vault')
    expect(result.isErr()).toBe(true)
  })

  it('returns err on unexpected exception', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockRejectedValue(new Error('crash'))
    const result = await initVault('/some/path')
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('Failed to init vault')
  })
})

describe('initVaultDesktop', () => {
  const vaultPath = '/home/user/Documents/lekto-vault'

  beforeEach(() => {
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(ok(undefined))
    vi.mocked(preferencesAdapter.set).mockResolvedValue(ok(undefined))
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(initDbForNewVault).mockResolvedValue(ok({}) as any)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(runMigrations).mockResolvedValue(ok(undefined) as any)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(seedLanguages).mockResolvedValue(ok(undefined) as any)
  })

  it('happy path — mkdir, inits db, migrates, seeds, then persists path and updates store', async () => {
    const result = await initVaultDesktop(vaultPath)

    expect(result.isOk()).toBe(true)
    expect(filesystemAdapter.mkdir).toHaveBeenCalledWith(`${vaultPath}/books`)
    expect(initDbForNewVault).toHaveBeenCalledWith(vaultPath)
    expect(runMigrations).toHaveBeenCalled()
    expect(seedLanguages).toHaveBeenCalled()
    expect(preferencesAdapter.set).toHaveBeenCalledWith(VAULT_PATH_KEY, vaultPath)
    expect(mockSetVaultPath).toHaveBeenCalledWith(vaultPath)
  })

  it('returns err when mkdir fails, db never inited', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(err('permission denied'))
    const result = await initVaultDesktop(vaultPath)
    expect(result.isErr()).toBe(true)
    expect(initDbForNewVault).not.toHaveBeenCalled()
    expect(preferencesAdapter.set).not.toHaveBeenCalled()
  })

  it('returns err when initDbForNewVault fails', async () => {
    vi.mocked(initDbForNewVault).mockResolvedValue(err('sql error'))
    const result = await initVaultDesktop(vaultPath)
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('DB init failed')
    expect(preferencesAdapter.set).not.toHaveBeenCalled()
  })

  it('returns err when runMigrations fails, preference never stored', async () => {
    vi.mocked(runMigrations).mockResolvedValue(err('migration error'))
    const result = await initVaultDesktop(vaultPath)
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('DB migration failed')
    expect(preferencesAdapter.set).not.toHaveBeenCalled()
  })

  it('returns err when seedLanguages fails, preference never stored', async () => {
    vi.mocked(seedLanguages).mockResolvedValue(err('seed error'))
    const result = await initVaultDesktop(vaultPath)
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('DB seed failed')
    expect(preferencesAdapter.set).not.toHaveBeenCalled()
  })

  it('returns err when preferences.set fails after successful db setup', async () => {
    vi.mocked(preferencesAdapter.set).mockResolvedValue(err('storage full'))
    const result = await initVaultDesktop(vaultPath)
    expect(result.isErr()).toBe(true)
    expect(runMigrations).toHaveBeenCalled()
  })

  it('returns err on unexpected exception', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockRejectedValue(new Error('crash'))
    const result = await initVaultDesktop(vaultPath)
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('Failed to init desktop vault')
  })
})
