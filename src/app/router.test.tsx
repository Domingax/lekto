import { describe, it, expect, vi, beforeEach } from 'vitest'
import { redirect } from 'react-router-dom'

vi.mock('../shared/stores', () => ({
  useVaultStore: {
    getState: vi.fn(),
  },
}))

import { rootLoader, libraryLoader } from './router'
import { useVaultStore } from '../shared/stores'

function mockStore(state: { vaultPath: string | null }) {
  vi.mocked(useVaultStore.getState).mockReturnValue({
    vaultPath: state.vaultPath,
    isVaultReady: !!state.vaultPath,
    setVaultPath: vi.fn(),
    clearVault: vi.fn(),
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('rootLoader', () => {
  it('redirects to /vault-setup when no vault configured', () => {
    mockStore({ vaultPath: null })
    const result = rootLoader()
    expect(result).toEqual(redirect('/vault-setup'))
  })

  it('redirects to /library when vault is configured', () => {
    mockStore({ vaultPath: 'lekto-vault' })
    const result = rootLoader()
    expect(result).toEqual(redirect('/library'))
  })
})

describe('libraryLoader', () => {
  it('redirects to /vault-setup when no vault configured', () => {
    mockStore({ vaultPath: null })
    const result = libraryLoader()
    expect(result).toEqual(redirect('/vault-setup'))
  })

  it('returns null when vault is configured', () => {
    mockStore({ vaultPath: 'lekto-vault' })
    const result = libraryLoader()
    expect(result).toBeNull()
  })
})

// /settings uses libraryLoader — same vault-guard behaviour
describe('/settings route (via libraryLoader)', () => {
  it('redirects to /vault-setup when no vault configured', () => {
    mockStore({ vaultPath: null })
    expect(libraryLoader()).toEqual(redirect('/vault-setup'))
  })

  it('allows access when vault is configured', () => {
    mockStore({ vaultPath: '/some/vault' })
    expect(libraryLoader()).toBeNull()
  })
})
