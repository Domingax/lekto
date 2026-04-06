import { isTauri } from '../is-tauri'
import { createWebPreferencesAdapter } from './preferences.web'
import { createDesktopPreferencesAdapter } from './preferences.desktop'

export type { PreferencesAdapter } from './preferences.interface'

export const preferencesAdapter = isTauri()
  ? createDesktopPreferencesAdapter()
  : createWebPreferencesAdapter()
