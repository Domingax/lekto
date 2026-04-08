import { isTauri } from '../is-tauri'
import { createAndroidPreferencesAdapter } from './preferences.android'
import { createDesktopPreferencesAdapter } from './preferences.desktop'

export type { PreferencesAdapter } from './preferences.interface'

export const preferencesAdapter = isTauri()
  ? createDesktopPreferencesAdapter()
  : createAndroidPreferencesAdapter()
