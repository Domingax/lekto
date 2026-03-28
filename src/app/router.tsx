import { createBrowserRouter, redirect } from 'react-router-dom'
import { useVaultStore } from '../shared/stores'
import { VaultSetupPage } from '../pages'
import { LibraryPage } from '../pages'

export const router = createBrowserRouter([
  {
    path: '/',
    loader: () => {
      const { vaultPath, pendingPermissionHandle } = useVaultStore.getState()
      if (pendingPermissionHandle) return redirect('/vault-setup')
      if (vaultPath) return redirect('/library')
      return redirect('/vault-setup')
    },
  },
  {
    path: '/vault-setup',
    element: <VaultSetupPage />,
  },
  {
    path: '/library',
    element: <LibraryPage />,
  },
])
