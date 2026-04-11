import { describe, it, expect, vi, afterEach } from 'vitest'

vi.mock('@/shared/platform/is-tauri', () => ({
  isTauri: vi.fn().mockReturnValue(false),
}))

vi.mock('./filesystem.android', () => ({
  createAndroidFilesystemAdapter: vi.fn().mockReturnValue({ _adapter: 'android', copyFile: vi.fn() }),
}))

vi.mock('./filesystem.desktop', () => ({
  createDesktopFilesystemAdapter: vi.fn().mockReturnValue({ _adapter: 'desktop', copyFile: vi.fn() }),
}))

import { isTauri } from '@/shared/platform/is-tauri'

afterEach(() => {
  vi.clearAllMocks()
  vi.resetModules()
})

describe('filesystemAdapter', () => {
  it('returns desktop adapter when isTauri() is true', async () => {
    vi.mocked(isTauri).mockReturnValue(true)
    const { filesystemAdapter } = await import('./index')
    expect(filesystemAdapter).toEqual({ _adapter: 'desktop', copyFile: expect.any(Function) })
  })

  it('returns android adapter when isTauri() is false', async () => {
    vi.mocked(isTauri).mockReturnValue(false)
    const { filesystemAdapter } = await import('./index')
    expect(filesystemAdapter).toEqual({ _adapter: 'android', copyFile: expect.any(Function) })
  })

  it('exported adapter has copyFile method', async () => {
    vi.mocked(isTauri).mockReturnValue(false)
    const { filesystemAdapter } = await import('./index')
    expect(typeof filesystemAdapter.copyFile).toBe('function')
  })
})
