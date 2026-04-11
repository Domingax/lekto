import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ok } from 'neverthrow'

const mockRun = vi.fn().mockResolvedValue(undefined)
const mockValues = vi.fn().mockResolvedValue([])

vi.mock('./index', () => ({
  getDb: vi.fn(() => ({ run: mockRun, values: mockValues })),
}))

describe('runMigrations', () => {
  beforeEach(() => vi.clearAllMocks())

  it('returns ok() on success', async () => {
    const { runMigrations } = await import('./migrate')
    const result = await runMigrations()
    expect(result).toEqual(ok(undefined))
  })

it('returns err() on migration failure', async () => {
    mockRun.mockRejectedValueOnce(new Error('migration failed'))
    const { runMigrations } = await import('./migrate')
    const result = await runMigrations()
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toBe('migration failed')
  })

  it('idempotent re-run returns ok() on second call', async () => {
    const { runMigrations } = await import('./migrate')
    const r1 = await runMigrations()
    const r2 = await runMigrations()
    expect(r1).toEqual(ok(undefined))
    expect(r2).toEqual(ok(undefined))
  })

  it('includes cause.message when cause is an Error', async () => {
    const cause = new Error('disk full')
    const error = new Error('migration failed')
    error.cause = cause
    mockRun.mockRejectedValueOnce(error)
    const { runMigrations } = await import('./migrate')
    const result = await runMigrations()
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toBe('migration failed → disk full')
  })

  it('includes JSON.stringify(cause) when cause is a plain object', async () => {
    const error = new Error('migration failed')
    error.cause = { code: 42 }
    mockRun.mockRejectedValueOnce(error)
    const { runMigrations } = await import('./migrate')
    const result = await runMigrations()
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toBe('migration failed → {"code":42}')
  })

  it('includes String(cause) when cause is a primitive', async () => {
    const error = new Error('migration failed')
    error.cause = 'constraint violation'
    mockRun.mockRejectedValueOnce(error)
    const { runMigrations } = await import('./migrate')
    const result = await runMigrations()
    expect(result.isErr()).toBe(true)
    expect(result._unsafeUnwrapErr()).toBe('migration failed → constraint violation')
  })
})
