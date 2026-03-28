import { describe, it, expect, beforeEach } from 'vitest'
import { createWebPreferencesAdapter } from './preferences.web'

describe('WebPreferencesAdapter', () => {
  let adapter: ReturnType<typeof createWebPreferencesAdapter>

  beforeEach(() => {
    localStorage.clear()
    adapter = createWebPreferencesAdapter()
  })

  it('sets and gets a value', async () => {
    await adapter.set('key1', 'value1')
    const result = await adapter.get('key1')
    expect(result.isOk()).toBe(true)
    expect(result._unsafeUnwrap()).toBe('value1')
  })

  it('returns err when key is absent', async () => {
    const result = await adapter.get('missing')
    expect(result.isErr()).toBe(true)
  })

  it('removes a key', async () => {
    await adapter.set('key1', 'value1')
    await adapter.remove('key1')
    const result = await adapter.get('key1')
    expect(result.isErr()).toBe(true)
  })

  it('overwrites an existing value', async () => {
    await adapter.set('key1', 'first')
    await adapter.set('key1', 'second')
    const result = await adapter.get('key1')
    expect(result._unsafeUnwrap()).toBe('second')
  })

  it('set returns ok', async () => {
    const result = await adapter.set('key1', 'value1')
    expect(result.isOk()).toBe(true)
  })

  it('remove returns ok even when key does not exist', async () => {
    const result = await adapter.remove('nonexistent')
    expect(result.isOk()).toBe(true)
  })
})
