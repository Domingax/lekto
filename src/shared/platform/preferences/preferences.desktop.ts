import { Store } from '@tauri-apps/plugin-store'
import { ok, err } from 'neverthrow'
import type { PreferencesAdapter } from './preferences.interface'
import type { AsyncResult } from '../../lib/types'

// Store file lives in Tauri app data dir (OS-managed, not in user vault)
const STORE_FILE = 'lekto-preferences.json'

export function createDesktopPreferencesAdapter(): PreferencesAdapter {
  return {
    async get(key: string): AsyncResult<string> {
      try {
        const store = await Store.load(STORE_FILE)
        const value = await store.get<string>(key)
        if (value === undefined || value === null) return err('Not found')
        return ok(value)
      } catch {
        return err('Preferences get failed')
      }
    },

    async set(key: string, value: string): AsyncResult<void> {
      try {
        const store = await Store.load(STORE_FILE)
        await store.set(key, value)
        await store.save()
        return ok(undefined)
      } catch {
        return err('Preferences set failed')
      }
    },

    async remove(key: string): AsyncResult<void> {
      try {
        const store = await Store.load(STORE_FILE)
        await store.delete(key)
        await store.save()
        return ok(undefined)
      } catch {
        return err('Preferences remove failed')
      }
    },
  }
}
