import { create } from 'zustand'

interface VaultState {
  vaultPath: string | null
  isVaultReady: boolean
  setVaultPath: (path: string) => void
  clearVault: () => void
}

export const useVaultStore = create<VaultState>((set) => ({
  vaultPath: null,
  isVaultReady: false,

  setVaultPath: (path: string) =>
    set({ vaultPath: path, isVaultReady: true }),

  clearVault: () =>
    set({ vaultPath: null, isVaultReady: false }),
}))
