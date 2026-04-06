import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    // Set to true by the Tauri CLI via TAURI_ENV_PLATFORM env var (Tauri v2).
    // Ensures Tauri-only imports are tree-shaken out of Capacitor/web builds.
    '__TAURI__': JSON.stringify(!!process.env['TAURI_ENV_PLATFORM']),
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin',
      'Cross-Origin-Embedder-Policy': 'require-corp',
    },
  },
})
