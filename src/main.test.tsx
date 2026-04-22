import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ok, err } from 'neverthrow'

const mockSelect = vi.fn().mockReturnValue({ from: vi.fn().mockResolvedValue([]) })

vi.mock('@/shared/db', () => ({
  initDb: vi.fn().mockResolvedValue(ok({})),
  runMigrations: vi.fn().mockResolvedValue(ok(undefined)),
  seedLanguages: vi.fn().mockResolvedValue(ok(undefined)),
  getDb: vi.fn(() => ({ select: mockSelect })),
  schema: { books: 'books' },
}))
vi.mock('react-dom/client', () => ({
  createRoot: vi.fn().mockReturnValue({ render: vi.fn() }),
}))
vi.mock('./App.tsx', () => ({ default: () => null }))

beforeEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = '<div id="root"></div>'
})

describe('start()', () => {
  it('renders the app after successful init, migrations, and seed', async () => {
    const { start } = await import('./main')
    const { createRoot } = await import('react-dom/client')
    await start()
    expect(vi.mocked(createRoot)).toHaveBeenCalled()
  })

  it('shows init error and does not render app when initDb fails', async () => {
    const { initDb } = await import('@/shared/db')
    vi.mocked(initDb).mockResolvedValueOnce(err('OPFS unavailable'))
    const { start } = await import('./main')
    const { createRoot } = await import('react-dom/client')
    await start()
    expect(document.getElementById('root')!.textContent).toContain('OPFS unavailable')
    expect(vi.mocked(createRoot)).not.toHaveBeenCalled()
  })

  it('shows migration error and does not render app when migrations fail', async () => {
    const { runMigrations } = await import('@/shared/db')
    vi.mocked(runMigrations).mockResolvedValueOnce(err('migration failed'))
    const { start } = await import('./main')
    const { createRoot } = await import('react-dom/client')
    await start()
    expect(document.getElementById('root')!.textContent).toContain('migration failed')
    expect(vi.mocked(createRoot)).not.toHaveBeenCalled()
  })

  it('shows seed error and does not render app when seed fails', async () => {
    const { seedLanguages } = await import('@/shared/db')
    vi.mocked(seedLanguages).mockResolvedValueOnce(err('seed failed'))
    const { start } = await import('./main')
    const { createRoot } = await import('react-dom/client')
    await start()
    expect(document.getElementById('root')!.textContent).toContain('seed failed')
    expect(vi.mocked(createRoot)).not.toHaveBeenCalled()
  })
})
