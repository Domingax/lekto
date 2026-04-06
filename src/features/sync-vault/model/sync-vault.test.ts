import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ok, err } from 'neverthrow'

vi.mock('../../../shared/platform', () => ({
  filesystemAdapter: {
    mkdir: vi.fn(),
  },
  setFilesystemRoot: vi.fn(),
  isTauri: vi.fn().mockReturnValue(false),
  preferencesAdapter: {
    get: vi.fn(),
    set: vi.fn(),
  },
}))

vi.mock('@/shared/db', () => ({
  initDb: vi.fn().mockResolvedValue({ isOk: () => true, isErr: () => false, value: {} }),
  runMigrations: vi.fn().mockResolvedValue({ isOk: () => true, isErr: () => false, value: undefined }),
  seedLanguages: vi.fn().mockResolvedValue({ isOk: () => true, isErr: () => false, value: undefined }),
}))

vi.mock('../../../shared/stores', () => ({
  useVaultStore: {
    getState: vi.fn(),
  },
}))

vi.mock('../lib/handle-store', () => ({
  storeVaultHandle: vi.fn(),
  loadVaultHandle: vi.fn(),
}))

import {
  VAULT_PATH_KEY,
  WEB_OPFS_PATH,
  WEB_NATIVE_PATH,
  DEFAULT_ANDROID_PATH,
  getVaultPath,
  isVaultConfigured,
  initVault,
  initVaultWithNativeHandle,
  loadAndRestoreVaultHandle,
  grantVaultPermission,
} from './sync-vault'
import { filesystemAdapter, setFilesystemRoot, preferencesAdapter, isTauri } from '../../../shared/platform'
import { initDb, runMigrations, seedLanguages } from '@/shared/db'
import { useVaultStore } from '../../../shared/stores'
import { storeVaultHandle, loadVaultHandle } from '../lib/handle-store'

const mockSetVaultPath = vi.fn()
const mockSetPendingPermissionHandle = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(isTauri).mockReturnValue(false)
  vi.mocked(useVaultStore.getState).mockReturnValue({
    vaultPath: null,
    isVaultReady: false,
    pendingPermissionHandle: null,
    setVaultPath: mockSetVaultPath,
    setPendingPermissionHandle: mockSetPendingPermissionHandle,
    clearVault: vi.fn(),
  })
})

describe('constants', () => {
  it('exports expected sentinel values', () => {
    expect(VAULT_PATH_KEY).toBe('vault_path')
    expect(WEB_OPFS_PATH).toBe('__opfs__')
    expect(WEB_NATIVE_PATH).toBe('__native__')
    expect(DEFAULT_ANDROID_PATH).toBe('lekto-vault')
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
    vi.mocked(preferencesAdapter.get).mockResolvedValue(ok('__opfs__'))
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

    const result = await initVault(WEB_OPFS_PATH)

    expect(result.isOk()).toBe(true)
    expect(filesystemAdapter.mkdir).toHaveBeenCalledWith('books')
    expect(preferencesAdapter.set).toHaveBeenCalledWith(VAULT_PATH_KEY, WEB_OPFS_PATH)
    expect(mockSetVaultPath).toHaveBeenCalledWith(WEB_OPFS_PATH)
  })

  it('returns err when mkdir fails', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(err('no space'))
    const result = await initVault(WEB_OPFS_PATH)
    expect(result.isErr()).toBe(true)
  })

  it('returns err when preferences.set fails', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(ok(undefined))
    vi.mocked(preferencesAdapter.set).mockResolvedValue(err('storage full'))
    const result = await initVault(WEB_OPFS_PATH)
    expect(result.isErr()).toBe(true)
  })
})

describe('initVaultWithNativeHandle', () => {
  const mockHandle = { name: 'my-vault' } as FileSystemDirectoryHandle

  it('stores handle, sets root, creates books, persists native path, updates store', async () => {
    vi.mocked(storeVaultHandle).mockResolvedValue(undefined)
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(ok(undefined))
    vi.mocked(preferencesAdapter.set).mockResolvedValue(ok(undefined))

    const result = await initVaultWithNativeHandle(mockHandle)

    expect(result.isOk()).toBe(true)
    expect(storeVaultHandle).toHaveBeenCalledWith(mockHandle)
    expect(setFilesystemRoot).toHaveBeenCalledWith(mockHandle)
    expect(filesystemAdapter.mkdir).toHaveBeenCalledWith('books')
    expect(preferencesAdapter.set).toHaveBeenCalledWith(VAULT_PATH_KEY, WEB_NATIVE_PATH)
    expect(mockSetVaultPath).toHaveBeenCalledWith(WEB_NATIVE_PATH)
  })

  it('returns err when mkdir fails', async () => {
    vi.mocked(storeVaultHandle).mockResolvedValue(undefined)
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(err('permission denied'))
    const result = await initVaultWithNativeHandle(mockHandle)
    expect(result.isErr()).toBe(true)
  })
})

describe('loadAndRestoreVaultHandle', () => {
  it('returns not-found when no handle in IDB', async () => {
    vi.mocked(loadVaultHandle).mockResolvedValue(null)
    const result = await loadAndRestoreVaultHandle()
    expect(result).toBe('not-found')
  })

  it('restores handle and returns ok when permission is granted', async () => {
    const mockHandle = {
      queryPermission: vi.fn().mockResolvedValue('granted'),
    } as unknown as FileSystemDirectoryHandle
    vi.mocked(loadVaultHandle).mockResolvedValue(mockHandle)

    const result = await loadAndRestoreVaultHandle()

    expect(result).toBe('ok')
    expect(setFilesystemRoot).toHaveBeenCalledWith(mockHandle)
    expect(mockSetVaultPath).toHaveBeenCalledWith(WEB_NATIVE_PATH)
  })

  it('stores pending handle and returns needs-permission when permission is prompt', async () => {
    const mockHandle = {
      queryPermission: vi.fn().mockResolvedValue('prompt'),
    } as unknown as FileSystemDirectoryHandle
    vi.mocked(loadVaultHandle).mockResolvedValue(mockHandle)

    const result = await loadAndRestoreVaultHandle()

    expect(result).toBe('needs-permission')
    expect(mockSetPendingPermissionHandle).toHaveBeenCalledWith(mockHandle)
  })
})

describe('grantVaultPermission', () => {
  it('sets root and vault path on granted', async () => {
    const mockHandle = {
      requestPermission: vi.fn().mockResolvedValue('granted'),
    } as unknown as FileSystemDirectoryHandle

    const result = await grantVaultPermission(mockHandle)

    expect(result.isOk()).toBe(true)
    expect(setFilesystemRoot).toHaveBeenCalledWith(mockHandle)
    expect(mockSetVaultPath).toHaveBeenCalledWith(WEB_NATIVE_PATH)
  })

  it('returns err when permission denied', async () => {
    const mockHandle = {
      requestPermission: vi.fn().mockResolvedValue('denied'),
    } as unknown as FileSystemDirectoryHandle

    const result = await grantVaultPermission(mockHandle)

    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toBe('Permission denied')
  })
})

describe('initVault — Tauri path', () => {
  beforeEach(() => {
    vi.mocked(isTauri).mockReturnValue(true)
    vi.mocked(filesystemAdapter.mkdir).mockResolvedValue(ok(undefined))
    vi.mocked(preferencesAdapter.set).mockResolvedValue(ok(undefined))
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(initDb).mockResolvedValue(ok({}) as any)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(runMigrations).mockResolvedValue(ok(undefined) as any)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(seedLanguages).mockResolvedValue(ok(undefined) as any)
  })

  it('happy path — inits db, runs migrations and seed, updates store', async () => {
    const result = await initVault('/vault/path')
    expect(result.isOk()).toBe(true)
    expect(initDb).toHaveBeenCalled()
    expect(runMigrations).toHaveBeenCalled()
    expect(seedLanguages).toHaveBeenCalled()
    expect(mockSetVaultPath).toHaveBeenCalledWith('/vault/path')
  })

  it('returns err when initDb fails', async () => {
    vi.mocked(initDb).mockResolvedValue(err('sql error'))
    const result = await initVault('/vault/path')
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('DB init failed')
  })

  it('returns err when initDb returns null (first-launch race condition)', async () => {
    vi.mocked(initDb).mockResolvedValue(ok(null))
    const result = await initVault('/vault/path')
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('null')
  })

  it('returns err when runMigrations fails', async () => {
    vi.mocked(runMigrations).mockResolvedValue(err('migration error'))
    const result = await initVault('/vault/path')
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('DB migration failed')
  })

  it('returns err when seedLanguages fails', async () => {
    vi.mocked(seedLanguages).mockResolvedValue(err('seed error'))
    const result = await initVault('/vault/path')
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('DB seed failed')
  })
})

describe('initVault — catch block', () => {
  it('returns err on unexpected exception', async () => {
    vi.mocked(filesystemAdapter.mkdir).mockRejectedValue(new Error('crash'))
    const result = await initVault('/some/path')
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('Failed to init vault')
  })
})

describe('initVaultWithNativeHandle — catch block', () => {
  it('returns err on unexpected exception', async () => {
    const mockHandle = { name: 'vault' } as FileSystemDirectoryHandle
    vi.mocked(storeVaultHandle).mockRejectedValue(new Error('IDB crash'))
    const result = await initVaultWithNativeHandle(mockHandle)
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('Failed to init vault with native handle')
  })
})

describe('loadAndRestoreVaultHandle — catch block', () => {
  it('returns not-found on unexpected exception', async () => {
    vi.mocked(loadVaultHandle).mockRejectedValue(new Error('IDB error'))
    const result = await loadAndRestoreVaultHandle()
    expect(result).toBe('not-found')
  })
})
