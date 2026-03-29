import { describe, it, expect, vi, afterEach } from 'vitest'

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: vi.fn().mockReturnValue(false) },
}))

vi.mock('./filesystem.web', () => ({
  createWebFilesystemAdapter: vi.fn().mockReturnValue({}),
  setWebFilesystemRoot: vi.fn(),
}))

vi.mock('./filesystem.android', () => ({
  createAndroidFilesystemAdapter: vi.fn().mockReturnValue({}),
}))

import { Capacitor } from '@capacitor/core'
import { setWebFilesystemRoot } from './filesystem.web'

afterEach(() => {
  vi.clearAllMocks()
})

describe('setFilesystemRoot', () => {
  it('calls setWebFilesystemRoot on web platform', async () => {
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(false)
    const { setFilesystemRoot } = await import('./index')
    const mockHandle = {} as FileSystemDirectoryHandle
    setFilesystemRoot(mockHandle)
    expect(setWebFilesystemRoot).toHaveBeenCalledWith(mockHandle)
  })

  it('does not call setWebFilesystemRoot on Android', async () => {
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true)
    const { setFilesystemRoot } = await import('./index')
    setFilesystemRoot(null)
    expect(setWebFilesystemRoot).not.toHaveBeenCalled()
  })
})
