import 'fake-indexeddb/auto'
import { describe, it, expect, beforeEach } from 'vitest'
import { storeVaultHandle, loadVaultHandle, clearVaultHandle } from './handle-store'

// Reset IDBFactory between tests by recreating the database
beforeEach(async () => {
  await clearVaultHandle()
})

describe('handle-store', () => {
  it('loadVaultHandle returns null when nothing is stored', async () => {
    const handle = await loadVaultHandle()
    expect(handle).toBeNull()
  })

  it('stores and loads a handle', async () => {
    const mockHandle = { name: 'lekto-vault' } as FileSystemDirectoryHandle
    await storeVaultHandle(mockHandle)
    const loaded = await loadVaultHandle()
    expect((loaded as { name: string } | null)?.name).toBe('lekto-vault')
  })

  it('clearVaultHandle removes the stored handle', async () => {
    const mockHandle = { name: 'lekto-vault' } as FileSystemDirectoryHandle
    await storeVaultHandle(mockHandle)
    await clearVaultHandle()
    const loaded = await loadVaultHandle()
    expect(loaded).toBeNull()
  })

  it('storeVaultHandle overwrites the previous handle', async () => {
    const handle1 = { name: 'vault-1' } as FileSystemDirectoryHandle
    const handle2 = { name: 'vault-2' } as FileSystemDirectoryHandle
    await storeVaultHandle(handle1)
    await storeVaultHandle(handle2)
    const loaded = await loadVaultHandle()
    expect((loaded as { name: string } | null)?.name).toBe('vault-2')
  })
})
