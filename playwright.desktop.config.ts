import { defineConfig } from '@playwright/test'

/**
 * Playwright config for desktop (Tauri) E2E tests.
 *
 * Requires tauri-driver to be running before executing tests:
 *   cargo install tauri-driver
 *   tauri-driver &
 *
 * The test binary must be built first:
 *   npm run tauri:build
 *
 * The npm script `test:e2e:desktop` handles this sequence automatically.
 * In CI this config is used after tauri-driver is started as a background process.
 */
export default defineConfig({
  testDir: './e2e/desktop',
  timeout: 60_000,
  retries: 0,
  workers: 1,
  reporter: process.env.CI ? 'github' : 'html',
  // No webServer block — tauri-driver is started externally by the npm script / CI step.
  use: {
    trace: 'on-first-retry',
  },
})
