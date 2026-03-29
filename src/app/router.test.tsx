import { describe, it, expect, vi, beforeEach } from 'vitest'
import { redirect } from 'react-router-dom'

vi.mock('../shared/stores', () => ({
  useVaultStore: {
    getState: vi.fn(),
  },
}))

import { rootLoader, libraryLoader } from './router'
import { useVaultStore } from '../shared/stores'

function mockStore(state: {
  vaultPath: string | null
  pendingPermissionHandle: FileSystemDirectoryHandle | null
}) {
  vi.mocked(useVaultStore.getState).mockReturnValue({
    vaultPath: state.vaultPath,
    pendingPermissionHandle: state.pendingPermissionHandle,
    isVaultReady: !!state.vaultPath,
    setVaultPath: vi.fn(),
    setPendingPermissionHandle: vi.fn(),
    clearVault: vi.fn(),
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('rootLoader', () => {
  it('redirects to /vault-setup when no vault configured', () => {
    mockStore({ vaultPath: null, pendingPermissionHandle: null })
    const result = rootLoader()
    expect(result).toEqual(redirect('/vault-setup'))
  })

  it('redirects to /library when vault is configured', () => {
    mockStore({ vaultPath: '__opfs__', pendingPermissionHandle: null })
    const result = rootLoader()
    expect(result).toEqual(redirect('/library'))
  })

  it('redirects to /vault-setup when pendingPermissionHandle is set', () => {
    mockStore({ vaultPath: '__native__', pendingPermissionHandle: {} as FileSystemDirectoryHandle })
    const result = rootLoader()
    expect(result).toEqual(redirect('/vault-setup'))
  })
})

describe('libraryLoader', () => {
  it('redirects to /vault-setup when no vault configured', () => {
    mockStore({ vaultPath: null, pendingPermissionHandle: null })
    const result = libraryLoader()
    expect(result).toEqual(redirect('/vault-setup'))
  })

  it('returns null when vault is configured', () => {
    mockStore({ vaultPath: '__opfs__', pendingPermissionHandle: null })
    const result = libraryLoader()
    expect(result).toBeNull()
  })
})
