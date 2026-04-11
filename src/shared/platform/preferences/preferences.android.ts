import { ok, err } from 'neverthrow'
import type { PreferencesAdapter } from './preferences.interface'
import type { AsyncResult } from '../../lib/types'

export function createAndroidPreferencesAdapter(): PreferencesAdapter {
  return {
    async get(key: string): AsyncResult<string> {
      const value = localStorage.getItem(key)
      if (value === null) return err('Not found')
      return ok(value)
    },

    async set(key: string, value: string): AsyncResult<void> {
      localStorage.setItem(key, value)
      return ok(undefined)
    },

    async remove(key: string): AsyncResult<void> {
      localStorage.removeItem(key)
      return ok(undefined)
    },
  }
}
