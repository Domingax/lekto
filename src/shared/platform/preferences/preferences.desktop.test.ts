import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { PreferencesAdapter } from './preferences.interface'

vi.mock('@tauri-apps/plugin-store', () => ({
  Store: {
    load: vi.fn(),
  },
}))

describe('PreferencesAdapter (desktop)', () => {
  let adapter: PreferencesAdapter
  let mockStoreInstance: {
    get: ReturnType<typeof vi.fn>
    set: ReturnType<typeof vi.fn>
    delete: ReturnType<typeof vi.fn>
    save: ReturnType<typeof vi.fn>
  }

  beforeEach(async () => {
    vi.resetAllMocks()
    mockStoreInstance = {
      get: vi.fn().mockResolvedValue('stored-value'),
      set: vi.fn().mockResolvedValue(undefined),
      delete: vi.fn().mockResolvedValue(undefined),
      save: vi.fn().mockResolvedValue(undefined),
    }
    const { Store } = await import('@tauri-apps/plugin-store')
    vi.mocked(Store.load).mockResolvedValue(mockStoreInstance as never)

    const { createDesktopPreferencesAdapter } = await import('./preferences.desktop')
    adapter = createDesktopPreferencesAdapter()
  })

  it('get returns ok with stored value', async () => {
    const result = await adapter.get('my-key')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe('stored-value')
  })

  it('get returns err when key is not found (undefined)', async () => {
    mockStoreInstance.get.mockResolvedValue(undefined)
    const result = await adapter.get('missing-key')
    expect(result.isErr()).toBe(true)
  })

  it('get returns err when key is null', async () => {
    mockStoreInstance.get.mockResolvedValue(null)
    const result = await adapter.get('null-key')
    expect(result.isErr()).toBe(true)
  })

  it('get returns err when Store.load throws', async () => {
    const { Store } = await import('@tauri-apps/plugin-store')
    vi.mocked(Store.load).mockRejectedValue(new Error('store error'))
    const result = await adapter.get('my-key')
    expect(result.isErr()).toBe(true)
  })

  it('set returns ok on success', async () => {
    const result = await adapter.set('my-key', 'my-value')
    expect(result.isOk()).toBe(true)
  })

  it('set calls store.set and store.save', async () => {
    await adapter.set('my-key', 'my-value')
    expect(mockStoreInstance.set).toHaveBeenCalledWith('my-key', 'my-value')
    expect(mockStoreInstance.save).toHaveBeenCalled()
  })

  it('set returns err when store throws', async () => {
    mockStoreInstance.set.mockRejectedValue(new Error('write error'))
    const result = await adapter.set('my-key', 'my-value')
    expect(result.isErr()).toBe(true)
  })

  it('remove returns ok on success', async () => {
    const result = await adapter.remove('my-key')
    expect(result.isOk()).toBe(true)
  })

  it('remove calls store.delete and store.save', async () => {
    await adapter.remove('my-key')
    expect(mockStoreInstance.delete).toHaveBeenCalledWith('my-key')
    expect(mockStoreInstance.save).toHaveBeenCalled()
  })

  it('remove returns err when store throws', async () => {
    mockStoreInstance.delete.mockRejectedValue(new Error('delete error'))
    const result = await adapter.remove('my-key')
    expect(result.isErr()).toBe(true)
  })
})
