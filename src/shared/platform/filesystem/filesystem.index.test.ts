import { describe, it, expect, vi, afterEach } from 'vitest'

vi.mock('@/shared/platform/is-tauri', () => ({
  isTauri: vi.fn().mockReturnValue(false),
}))

vi.mock('./filesystem.android', () => ({
  createAndroidFilesystemAdapter: vi.fn().mockReturnValue({ _adapter: 'android' }),
}))

vi.mock('./filesystem.desktop', () => ({
  createDesktopFilesystemAdapter: vi.fn().mockReturnValue({ _adapter: 'desktop' }),
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
    expect(filesystemAdapter).toEqual({ _adapter: 'desktop' })
  })

  it('returns android adapter when isTauri() is false', async () => {
    vi.mocked(isTauri).mockReturnValue(false)
    const { filesystemAdapter } = await import('./index')
    expect(filesystemAdapter).toEqual({ _adapter: 'android' })
  })
})
