import { createAndroidVaultDbAdapter } from './vault-db.android'
import type { VaultDbAdapter, VaultDbDeps } from './vault-db.android'

export type { VaultDbAdapter, VaultDbDeps }

// Desktop opens vault DBs in-place via Tauri SQL — no binary import needed.
// The vaultDbAdapter is only called from Android code paths.
export const vaultDbAdapter: VaultDbAdapter = createAndroidVaultDbAdapter()
