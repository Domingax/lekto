import type { AsyncResult } from '../../lib/types'

export interface PreferencesAdapter {
  get(key: string): AsyncResult<string>
  set(key: string, value: string): AsyncResult<void>
  remove(key: string): AsyncResult<void>
}
