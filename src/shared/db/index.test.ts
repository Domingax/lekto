import { describe, it, expect, vi, beforeEach } from 'vitest'
import { err as nErr, ok as nOk } from 'neverthrow'

type ProxyFn = (sql: string, params: unknown[], method: string) => Promise<{ rows: unknown[] }>
let capturedProxy: ProxyFn | null = null

// Mutable state shared with mock factories so vi.resetModules() doesn't lose them
const mockState: {
  isTauri: boolean
  isNativePlatform: boolean
  preferencesGetResult: unknown
  androidQueryResult: Record<string, unknown>
} = {
  isTauri: false,
  isNativePlatform: false,
  preferencesGetResult: nOk('/vault'),
  androidQueryResult: { values: [] },
}

// Reset module state between tests (the _db singleton must be fresh)
beforeEach(() => {
  vi.resetModules()
  capturedProxy = null
  // Reset platform state to "unsupported" baseline
  mockState.isTauri = false
  mockState.isNativePlatform = false
  mockState.preferencesGetResult = nOk('/vault')
  mockState.androidQueryResult = { values: [] }
})

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => mockState.isNativePlatform },
}))

vi.mock('drizzle-orm/sqlite-proxy', () => ({
  drizzle: vi.fn().mockImplementation((proxyFn: unknown) => {
    capturedProxy = proxyFn as ProxyFn
    return { _tag: 'mock-drizzle-db' }
  }),
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
  class MockSQLiteConnection {
    createConnection() {
      return Promise.resolve({
        open: vi.fn().mockResolvedValue(undefined),
        run: vi.fn().mockResolvedValue({}),
        query: vi.fn().mockImplementation(() => Promise.resolve(mockState.androidQueryResult)),
      })
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

describe('Desktop proxy callbacks', () => {
  beforeEach(() => {
    mockState.isTauri = true
  })

  it('run method calls db.execute and returns { rows: [] }', async () => {
    const { initDb } = await import('./index')
    await initDb()
    expect(capturedProxy).not.toBeNull()
    const result = await capturedProxy!('SELECT 1', [], 'run')
    expect(result).toEqual({ rows: [] })
  })

  it('values method calls db.select and returns mapped rows', async () => {
    const { initDb } = await import('./index')
    await initDb()
    const result = await capturedProxy!('SELECT 1', [], 'values')
    expect(result).toHaveProperty('rows')
    expect(Array.isArray(result.rows)).toBe(true)
  })

  it('get method calls db.select and returns raw rows', async () => {
    const { initDb } = await import('./index')
    await initDb()
    const result = await capturedProxy!('SELECT 1', [], 'get')
    expect(result).toHaveProperty('rows')
    expect(Array.isArray(result.rows)).toBe(true)
  })
})

describe('resetDb', () => {
  it('clears the singleton so getDb throws again', async () => {
    mockState.isNativePlatform = true
    const { initDb, resetDb, getDb } = await import('./index')
    await initDb()
    expect(() => getDb()).not.toThrow()
    resetDb()
    expect(() => getDb()).toThrow('DB not initialized')
  })
})

describe('initDbForNewVault', () => {
  it('returns ok with a DrizzleDb when vault path is valid', async () => {
    mockState.isTauri = true
    const { initDbForNewVault } = await import('./index')
    const result = await initDbForNewVault('/some/vault')
    expect(result.isOk()).toBe(true)
    expect(result._unsafeUnwrap()).toBeDefined()
  })

  it('returns err when createDesktopDb throws', async () => {
    const { default: Database } = await import('@tauri-apps/plugin-sql')
    vi.mocked(Database.load).mockRejectedValueOnce(new Error('cannot open db'))
    const { initDbForNewVault } = await import('./index')
    const result = await initDbForNewVault('/bad/path')
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toBe('cannot open db')
  })
})

describe('Android proxy callbacks', () => {
  beforeEach(() => {
    mockState.isNativePlatform = true
  })

  it('run method calls connection.run and returns { rows: [] }', async () => {
    const { initDb } = await import('./index')
    await initDb()
    expect(capturedProxy).not.toBeNull()
    const result = await capturedProxy!('SELECT 1', [], 'run')
    expect(result).toEqual({ rows: [] })
  })

  it('values method maps result.values to array-of-arrays', async () => {
    mockState.androidQueryResult = { values: [{ id: 1, name: 'test' }] }
    const { initDb } = await import('./index')
    await initDb()
    const result = await capturedProxy!('SELECT 1', [], 'values')
    expect(result).toEqual({ rows: [[1, 'test']] })
  })

  it('get method returns raw rows', async () => {
    mockState.androidQueryResult = { values: [{ id: 1 }] }
    const { initDb } = await import('./index')
    await initDb()
    const result = await capturedProxy!('SELECT 1', [], 'get')
    expect(result).toEqual({ rows: [{ id: 1 }] })
  })

  it('uses empty array when result.values is null (nullish coalescing fallback)', async () => {
    mockState.androidQueryResult = { values: null }
    const { initDb } = await import('./index')
    await initDb()
    const result = await capturedProxy!('SELECT 1', [], 'get')
    expect(result).toEqual({ rows: [] })
  })
})
