import { describe, it, expect, vi, afterEach } from 'vitest'

vi.mock('@/shared/platform/is-tauri', () => ({
  isTauri: vi.fn().mockReturnValue(false),
}))

vi.mock('./filesystem.web', () => ({
  createWebFilesystemAdapter: vi.fn().mockReturnValue({}),
  setWebFilesystemRoot: vi.fn(),
}))

vi.mock('./filesystem.android', () => ({
  createAndroidFilesystemAdapter: vi.fn().mockReturnValue({ _adapter: 'android' }),
}))

vi.mock('./filesystem.desktop', () => ({
  createDesktopFilesystemAdapter: vi.fn().mockReturnValue({ _adapter: 'desktop' }),
}))

import { isTauri } from '@/shared/platform/is-tauri'
import { setWebFilesystemRoot } from './filesystem.web'

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

describe('setFilesystemRoot', () => {
  it('calls setWebFilesystemRoot on web platform', async () => {
    vi.mocked(isTauri).mockReturnValue(false)
    const { setFilesystemRoot } = await import('./index')
    const mockHandle = {} as FileSystemDirectoryHandle
    setFilesystemRoot(mockHandle)
    expect(setWebFilesystemRoot).toHaveBeenCalledWith(mockHandle)
  })

  it('does not call setWebFilesystemRoot on desktop (Tauri)', async () => {
    vi.mocked(isTauri).mockReturnValue(true)
    const { setFilesystemRoot } = await import('./index')
    setFilesystemRoot(null)
    expect(setWebFilesystemRoot).not.toHaveBeenCalled()
  })
})
