import { describe, it, expect, beforeEach } from 'vitest'
import { useVaultStore } from './use-vault-store'

describe('useVaultStore', () => {
  beforeEach(() => {
    useVaultStore.setState({
      vaultPath: null,
      isVaultReady: false,
      pendingPermissionHandle: null,
    })
  })

  it('has correct initial state', () => {
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBeNull()
    expect(state.isVaultReady).toBe(false)
    expect(state.pendingPermissionHandle).toBeNull()
  })

  it('setVaultPath sets vaultPath and isVaultReady, clears pendingPermissionHandle', () => {
    const mockHandle = {} as FileSystemDirectoryHandle
    useVaultStore.getState().setPendingPermissionHandle(mockHandle)
    useVaultStore.getState().setVaultPath('__opfs__')
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBe('__opfs__')
    expect(state.isVaultReady).toBe(true)
    expect(state.pendingPermissionHandle).toBeNull()
  })

  it('setPendingPermissionHandle sets the handle', () => {
    const mockHandle = { name: 'lekto-vault' } as FileSystemDirectoryHandle
    useVaultStore.getState().setPendingPermissionHandle(mockHandle)
    expect(useVaultStore.getState().pendingPermissionHandle).toBe(mockHandle)
  })

  it('clearVault resets all state', () => {
    useVaultStore.getState().setVaultPath('__opfs__')
    useVaultStore.getState().clearVault()
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBeNull()
    expect(state.isVaultReady).toBe(false)
    expect(state.pendingPermissionHandle).toBeNull()
  })
})
