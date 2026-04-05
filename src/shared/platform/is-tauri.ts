// Build-time constant injected by Vite define.
// Set to true only when the Tauri CLI builds/serves the frontend.
declare const __TAURI__: boolean

export function isTauri(): boolean {
  return typeof __TAURI__ !== 'undefined' && __TAURI__
}
