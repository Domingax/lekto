import { create } from 'zustand'

interface VaultState {
  vaultPath: string | null
  isVaultReady: boolean
  // Web-only: handle pending permission re-grant after browser restart
  pendingPermissionHandle: FileSystemDirectoryHandle | null
  setVaultPath: (path: string) => void
  setPendingPermissionHandle: (handle: FileSystemDirectoryHandle | null) => void
  clearVault: () => void
}

export const useVaultStore = create<VaultState>((set) => ({
  vaultPath: null,
  isVaultReady: false,
  pendingPermissionHandle: null,

  setVaultPath: (path: string) =>
    set({ vaultPath: path, isVaultReady: true, pendingPermissionHandle: null }),

  setPendingPermissionHandle: (handle: FileSystemDirectoryHandle | null) =>
    set({ pendingPermissionHandle: handle }),

  clearVault: () =>
    set({ vaultPath: null, isVaultReady: false, pendingPermissionHandle: null }),
}))
