import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('franc', () => ({
  franc: vi.fn(),
}))

describe('detectLanguage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('maps franc "eng" to "en"', async () => {
    const { franc } = await import('franc')
    vi.mocked(franc).mockReturnValue('eng')
    const { detectLanguage } = await import('./detect-language')
    expect(detectLanguage('some english text')).toBe('en')
  })

  it('maps franc "fra" to "fr"', async () => {
    const { franc } = await import('franc')
    vi.mocked(franc).mockReturnValue('fra')
    const { detectLanguage } = await import('./detect-language')
    expect(detectLanguage('du texte français')).toBe('fr')
  })

  it('returns null for "und" (undetermined)', async () => {
    const { franc } = await import('franc')
    vi.mocked(franc).mockReturnValue('und')
    const { detectLanguage } = await import('./detect-language')
    expect(detectLanguage('???')).toBeNull()
  })

  it('returns null for unknown franc code', async () => {
    const { franc } = await import('franc')
    vi.mocked(franc).mockReturnValue('xyz')
    const { detectLanguage } = await import('./detect-language')
    expect(detectLanguage('some text')).toBeNull()
  })
})
