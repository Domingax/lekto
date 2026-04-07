import { chromium, test, expect } from '@playwright/test'

/**
 * Minimal happy-path E2E: vault creation → library navigation.
 *
 * tauri-driver exposes the Tauri WebView as a CDP endpoint on port 4444.
 * We connect via connectOverCDP instead of launching a browser ourselves.
 *
 * AC#4: "vault creation → book import stub" is satisfied when the app
 * successfully creates a vault and navigates to the library page.
 */
test('vault creation → library navigation', async () => {
  const browser = await chromium.connectOverCDP('http://localhost:4444')
  const context = browser.contexts()[0]
  const page = context.pages()[0]

  try {
    // App opens on vault setup page
    await expect(page.getByRole('heading', { name: 'Set up your vault' })).toBeVisible({
      timeout: 15_000,
    })

    // Start vault creation flow
    await page.getByRole('button', { name: 'Create new vault' }).click()
    await expect(page.getByRole('heading', { name: 'Create new vault' })).toBeVisible()

    // Confirm with default location (OPFS on desktop)
    await page.getByRole('button', { name: 'Confirm' }).click()

    // Book import stub: assert library page is reachable
    await expect(page.getByText('Library')).toBeVisible({ timeout: 15_000 })
  } finally {
    await browser.close()
  }
})
