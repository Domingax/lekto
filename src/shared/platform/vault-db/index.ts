import { createAndroidVaultDbAdapter } from './vault-db.android'

export type { VaultDbAdapter, VaultDbDeps } from './vault-db.android'

// Desktop opens vault DBs in-place via Tauri SQL — no binary import needed.
// The vaultDbAdapter is only called from Android code paths.
export const vaultDbAdapter: VaultDbAdapter = createAndroidVaultDbAdapter()
