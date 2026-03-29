// Both implementations are identical (localStorage) for MVP.
// The web adapter is used on all platforms.
import { createWebPreferencesAdapter } from './preferences.web'

export type { PreferencesAdapter } from './preferences.interface'
export const preferencesAdapter = createWebPreferencesAdapter()
