import { beforeAll, afterAll, it, expect } from 'vitest'
import { remote } from 'webdriverio'

let driver: WebdriverIO.Browser

beforeAll(async () => {
  driver = await remote({
    hostname: '127.0.0.1',
    port: 4444,
    capabilities: {
      'tauri:options': {
        application: 'src-tauri/target/debug/lekto',
      },
    },
  })
}, 60_000)

afterAll(async () => {
  await driver?.deleteSession()
})

it('vault creation → library navigation', async () => {
  // App opens on vault setup page
  await expect
    .poll(() => driver.$('h1=Set up your vault').isDisplayed(), { timeout: 15_000 })
    .toBe(true)

  // Start vault creation flow
  await (await driver.$('button=Create new vault')).click()
  await expect
    .poll(() => driver.$('h1=Create new vault').isDisplayed(), { timeout: 10_000 })
    .toBe(true)

  // Confirm with default location
  await (await driver.$('button=Confirm')).click()

  // Book import stub: assert library page is reachable
  // XPath used because WebDriverIO's *=text maps to "partial link text" (anchors only)
  await expect
    .poll(() => driver.$('//*[contains(., "Library")]').isDisplayed(), { timeout: 15_000 })
    .toBe(true)
}, 60_000)
