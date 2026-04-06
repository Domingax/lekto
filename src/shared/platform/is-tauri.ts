// Build-time constant injected by Vite define via TAURI_ENV_PLATFORM (Tauri v2 CLI).
// Set to true only when the Tauri CLI builds/serves the frontend.
declare const __TAURI__: boolean

export function isTauri(): boolean {
  return __TAURI__
}
