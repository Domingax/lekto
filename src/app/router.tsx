import { createBrowserRouter, redirect } from 'react-router-dom'
import { useVaultStore } from '../shared/stores'
import { VaultSetupPage, LibraryPage, SettingsPage } from '../pages'

export function rootLoader() {
  const { vaultPath } = useVaultStore.getState()
  if (vaultPath) return redirect('/library')
  return redirect('/vault-setup')
}

export function libraryLoader() {
  const { vaultPath } = useVaultStore.getState()
  if (!vaultPath) return redirect('/vault-setup')
  return null
}

// Factory function — must be called AFTER the vault store is populated in main.tsx,
// otherwise the initial navigation runs rootLoader with vaultPath: null
// and always redirects to /vault-setup regardless of persisted preferences.
export function createAppRouter() {
  return createBrowserRouter([
    {
      path: '/',
      loader: rootLoader,
    },
    {
      path: '/vault-setup',
      element: <VaultSetupPage />,
    },
    {
      path: '/library',
      loader: libraryLoader,
      element: <LibraryPage />,
    },
    {
      path: '/settings',
      loader: libraryLoader,
      element: <SettingsPage />,
    },
  ])
}
