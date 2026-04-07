import { beforeAll, afterAll, it, expect } from 'vitest'
import { remote } from 'webdriverio'

let driver: WebdriverIO.Browser

// Tests marked with requiresIpc need a functional Tauri IPC + SQLite + filesystem
// environment. They are skipped in CI (GitHub Actions sets CI=true) and run locally.
const requiresIpc = it.skipIf(!!process.env.CI)

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

it('app launches and renders vault setup screen', async () => {
  await expect
    .poll(() => driver.$('h1=Set up your vault').isDisplayed(), { timeout: 15_000 })
    .toBe(true)
}, 60_000)

requiresIpc('vault creation → library navigation', async () => {
  await expect
    .poll(() => driver.$('h1=Set up your vault').isDisplayed(), { timeout: 15_000 })
    .toBe(true)

  await (await driver.$('button=Create new vault')).click()
  await expect
    .poll(() => driver.$('h1=Create new vault').isDisplayed(), { timeout: 10_000 })
    .toBe(true)

  await (await driver.$('button=Confirm')).click()
  await expect
    .poll(() => driver.$('p=Library (coming soon)').isDisplayed(), { timeout: 15_000 })
    .toBe(true)
}, 60_000)
