import { describe, it, expect, beforeEach } from 'vitest'
import { useVaultStore } from './use-vault-store'

describe('useVaultStore', () => {
  beforeEach(() => {
    useVaultStore.setState({
      vaultPath: null,
      isVaultReady: false,
    })
  })

  it('has correct initial state', () => {
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBeNull()
    expect(state.isVaultReady).toBe(false)
  })

  it('setVaultPath sets vaultPath and isVaultReady', () => {
    useVaultStore.getState().setVaultPath('lekto-vault')
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBe('lekto-vault')
    expect(state.isVaultReady).toBe(true)
  })

  it('clearVault resets all state', () => {
    useVaultStore.getState().setVaultPath('lekto-vault')
    useVaultStore.getState().clearVault()
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBeNull()
    expect(state.isVaultReady).toBe(false)
  })
})
