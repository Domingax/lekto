import { Stronghold, Client } from '@tauri-apps/plugin-stronghold'
import { appDataDir } from '@tauri-apps/api/path'
import { ok, err } from 'neverthrow'
import type { SecureStorageAdapter } from './secure-storage.interface'
import type { AsyncResult } from '../../lib/types'

// Static password passed to the Rust hash function (argon2 derives the actual key).
// For a local-only app with no user accounts, a static passphrase is acceptable.
// Never changes after first vault initialization — changing it would lock out existing secrets.
const STRONGHOLD_PASSWORD = 'letko-desktop-secure-storage-v1'
const STRONGHOLD_CLIENT = 'lekto-client'

let _stronghold: Stronghold | null = null
let _client: Client | null = null

async function getClient(): Promise<Client> {
  if (_client) return _client
  const dir = await appDataDir()
  const vaultPath = `${dir}/lekto-secrets.holsd`
  _stronghold = await Stronghold.load(vaultPath, STRONGHOLD_PASSWORD)
  try {
    _client = await _stronghold.loadClient(STRONGHOLD_CLIENT)
  } catch {
    _client = await _stronghold.createClient(STRONGHOLD_CLIENT)
  }
  return _client
}

const encoder = new TextEncoder()
const decoder = new TextDecoder()

export function createDesktopSecureStorageAdapter(): SecureStorageAdapter {
  return {
    async get(key: string): AsyncResult<string> {
      try {
        const client = await getClient()
        const store = client.getStore()
        const data = await store.get(key)
        if (data === null || data === undefined) return err('Secure storage: key not found')
        return ok(decoder.decode(data))
      } catch {
        return err('Secure storage get failed')
      }
    },

    async set(key: string, value: string): AsyncResult<void> {
      try {
        const client = await getClient()
        const store = client.getStore()
        await store.insert(key, Array.from(encoder.encode(value)))
        await _stronghold!.save()
        return ok(undefined)
      } catch {
        return err('Secure storage set failed')
      }
    },

    async remove(key: string): AsyncResult<void> {
      try {
        const client = await getClient()
        const store = client.getStore()
        await store.remove(key)
        await _stronghold!.save()
        return ok(undefined)
      } catch {
        return err('Secure storage remove failed')
      }
    },
  }
}
