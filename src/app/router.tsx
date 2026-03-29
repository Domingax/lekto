import { createBrowserRouter, redirect } from 'react-router-dom'
import { useVaultStore } from '../shared/stores'
import { VaultSetupPage, LibraryPage } from '../pages'

export function rootLoader() {
  const { vaultPath, pendingPermissionHandle } = useVaultStore.getState()
  if (pendingPermissionHandle) return redirect('/vault-setup')
  if (vaultPath) return redirect('/library')
  return redirect('/vault-setup')
}

export function libraryLoader() {
  const { vaultPath } = useVaultStore.getState()
  if (!vaultPath) return redirect('/vault-setup')
  return null
}

export const router = createBrowserRouter([
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
])
