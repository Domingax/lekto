import { describe, it, expect, vi, beforeEach } from 'vitest'
import { err as nErr, ok as nOk } from 'neverthrow'

// Mutable state shared with mock factories so vi.resetModules() doesn't lose them
const mockState: {
  isTauri: boolean
  isNativePlatform: boolean
  preferencesGetResult: unknown
} = {
  isTauri: false,
  isNativePlatform: false,
  preferencesGetResult: nOk('/vault'),
}

// Reset module state between tests (the _db singleton must be fresh)
beforeEach(() => {
  vi.resetModules()
  // Reset platform state to "unsupported" baseline
  mockState.isTauri = false
  mockState.isNativePlatform = false
  mockState.preferencesGetResult = nOk('/vault')
})

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => mockState.isNativePlatform },
}))

vi.mock('drizzle-orm/sqlite-proxy', () => ({
  drizzle: vi.fn().mockReturnValue({ _tag: 'mock-drizzle-db' }),
}))

vi.mock('@/shared/platform', () => ({
  isTauri: () => mockState.isTauri,
  preferencesAdapter: {
    get: () => Promise.resolve(mockState.preferencesGetResult),
    set: vi.fn(),
  },
}))

vi.mock('@tauri-apps/plugin-sql', () => ({
  default: {
    load: vi.fn().mockResolvedValue({
      execute: vi.fn().mockResolvedValue(undefined),
      select: vi.fn().mockResolvedValue([]),
    }),
  },
}))

vi.mock('@capacitor-community/sqlite', () => {
  const mockConnection = {
    open: vi.fn().mockResolvedValue(undefined),
    run: vi.fn().mockResolvedValue({}),
    query: vi.fn().mockResolvedValue({ values: [] }),
  }
  class MockSQLiteConnection {
    createConnection() {
      return Promise.resolve(mockConnection)
    }
  }
  return {
    CapacitorSQLite: {},
    SQLiteConnection: MockSQLiteConnection,
  }
})

describe('initDb — Android path', () => {
  it('returns ok with a DrizzleDb instance on first call (Android path)', async () => {
    mockState.isNativePlatform = true

    const { initDb } = await import('./index')
    const result = await initDb()
    expect(result.isOk()).toBe(true)
    expect(result._unsafeUnwrap()).toBeDefined()
  })

  it('returns the same instance on repeated calls (singleton)', async () => {
    mockState.isNativePlatform = true

    const { initDb } = await import('./index')
    const r1 = await initDb()
    const r2 = await initDb()
    expect(r1.isOk()).toBe(true)
    expect(r2.isOk()).toBe(true)
    expect(r1._unsafeUnwrap()).toBe(r2._unsafeUnwrap())
  })
})

describe('initDb — Desktop (Tauri) path', () => {
  it('returns ok(db) when vault path is configured', async () => {
    mockState.isTauri = true
    mockState.preferencesGetResult = nOk('/vault')

    const { initDb } = await import('./index')
    const result = await initDb()
    expect(result.isOk()).toBe(true)
    expect(result._unsafeUnwrap()).toBeDefined()
  })

  it('returns ok(null) when no vault path configured yet (first launch)', async () => {
    mockState.isTauri = true
    mockState.preferencesGetResult = nErr('No vault configured')

    const { initDb } = await import('./index')
    const result = await initDb()
    expect(result.isOk()).toBe(true)
    expect(result._unsafeUnwrap()).toBeNull()
  })
})

describe('initDb — Unsupported platform', () => {
  it('returns err when not Tauri and not native', async () => {
    // isTauri = false and isNativePlatform = false (default baseline)
    const { initDb } = await import('./index')
    const result = await initDb()
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toContain('Unsupported platform')
  })
})

describe('getDb', () => {
  it('throws if called before initDb', async () => {
    const { getDb } = await import('./index')
    expect(() => getDb()).toThrow('DB not initialized')
  })

  it('returns the db instance after initDb on Android', async () => {
    mockState.isNativePlatform = true

    const { initDb, getDb } = await import('./index')
    await initDb()
    const db = getDb()
    expect(db).toBeDefined()
  })
})
