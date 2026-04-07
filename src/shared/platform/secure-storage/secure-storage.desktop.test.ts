import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { SecureStorageAdapter } from './secure-storage.interface'

describe('SecureStorageAdapter (desktop)', () => {
  let adapter: SecureStorageAdapter
  let mockStore: {
    get: ReturnType<typeof vi.fn>
    insert: ReturnType<typeof vi.fn>
    remove: ReturnType<typeof vi.fn>
  }
  let mockStronghold: {
    loadClient: ReturnType<typeof vi.fn>
    createClient: ReturnType<typeof vi.fn>
    save: ReturnType<typeof vi.fn>
  }

  let mockInvoke: ReturnType<typeof vi.fn>

  beforeEach(async () => {
    vi.resetModules()

    mockInvoke = vi.fn().mockResolvedValue('mock-passphrase-hex')

    mockStore = {
      get: vi.fn(),
      insert: vi.fn().mockResolvedValue(undefined),
      remove: vi.fn().mockResolvedValue(undefined),
    }

    const mockClient = {
      getStore: vi.fn().mockReturnValue(mockStore),
    }

    mockStronghold = {
      loadClient: vi.fn().mockResolvedValue(mockClient),
      createClient: vi.fn().mockResolvedValue(mockClient),
      save: vi.fn().mockResolvedValue(undefined),
    }

    vi.doMock('@tauri-apps/api/core', () => ({
      invoke: mockInvoke,
    }))

    vi.doMock('@tauri-apps/plugin-stronghold', () => ({
      Stronghold: {
        load: vi.fn().mockResolvedValue(mockStronghold),
      },
      Client: {},
    }))

    vi.doMock('@tauri-apps/api/path', () => ({
      appDataDir: vi.fn().mockResolvedValue('/home/user/.local/share/lekto'),
    }))

    const { createDesktopSecureStorageAdapter } = await import('./secure-storage.desktop')
    adapter = createDesktopSecureStorageAdapter()
  })

  it('get returns ok with decoded value', async () => {
    const encoded = new TextEncoder().encode('stored-value')
    mockStore.get.mockResolvedValue(encoded)
    const result = await adapter.get('my-key')
    expect(result.isOk()).toBe(true)
    if (result.isOk()) expect(result.value).toBe('stored-value')
  })

  it('get returns err when key not found (null)', async () => {
    mockStore.get.mockResolvedValue(null)
    const result = await adapter.get('missing-key')
    expect(result.isErr()).toBe(true)
  })

  it('get returns err when key not found (undefined)', async () => {
    mockStore.get.mockResolvedValue(undefined)
    const result = await adapter.get('missing-key')
    expect(result.isErr()).toBe(true)
  })

  it('get returns err when stronghold throws', async () => {
    mockStore.get.mockRejectedValue(new Error('stronghold error'))
    const result = await adapter.get('my-key')
    expect(result.isErr()).toBe(true)
  })

  it('set returns ok on success', async () => {
    const result = await adapter.set('my-key', 'my-value')
    expect(result.isOk()).toBe(true)
  })

  it('set encodes value and calls insert + save', async () => {
    await adapter.set('my-key', 'hello')
    expect(mockStore.insert).toHaveBeenCalledWith(
      'my-key',
      Array.from(new TextEncoder().encode('hello')),
    )
    expect(mockStronghold.save).toHaveBeenCalled()
  })

  it('set returns err when insert throws', async () => {
    mockStore.insert.mockRejectedValue(new Error('write error'))
    const result = await adapter.set('my-key', 'my-value')
    expect(result.isErr()).toBe(true)
  })

  it('remove returns ok on success', async () => {
    const result = await adapter.remove('my-key')
    expect(result.isOk()).toBe(true)
  })

  it('remove returns err when store throws', async () => {
    mockStore.remove.mockRejectedValue(new Error('delete error'))
    const result = await adapter.remove('my-key')
    expect(result.isErr()).toBe(true)
  })

  it('get returns err keychain unavailable when invoke rejects', async () => {
    mockInvoke.mockRejectedValue(new Error('keychain unavailable'))
    const result = await adapter.get('any-key')
    expect(result.isErr()).toBe(true)
    if (result.isErr()) expect(result.error).toBe('Secure storage: keychain unavailable')
  })

  it('set returns err keychain unavailable when invoke rejects', async () => {
    mockInvoke.mockRejectedValue(new Error('keychain unavailable'))
    const result = await adapter.set('any-key', 'any-value')
    expect(result.isErr()).toBe(true)
    if (result.isErr()) expect(result.error).toBe('Secure storage: keychain unavailable')
  })

  it('getClient calls invoke with get_or_create_vault_passphrase', async () => {
    const encoded = new TextEncoder().encode('val')
    mockStore.get.mockResolvedValue(encoded)
    await adapter.get('key1')
    expect(mockInvoke).toHaveBeenCalledWith('get_or_create_vault_passphrase')
  })

  it('reuses existing client on subsequent calls (singleton)', async () => {
    const encoded = new TextEncoder().encode('val')
    mockStore.get.mockResolvedValue(encoded)
    await adapter.get('key1')
    await adapter.get('key2')
    const { Stronghold } = await import('@tauri-apps/plugin-stronghold')
    expect(vi.mocked(Stronghold.load)).toHaveBeenCalledTimes(1)
  })

  it('falls back to createClient when loadClient throws', async () => {
    mockStronghold.loadClient.mockRejectedValue(new Error('client not found'))
    const encoded = new TextEncoder().encode('val')
    mockStore.get.mockResolvedValue(encoded)
    const result = await adapter.get('my-key')
    expect(result.isOk()).toBe(true)
    expect(mockStronghold.createClient).toHaveBeenCalled()
  })
})
