import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { TokenEntity } from '@/entities/token'
import { WordToken } from './WordToken'

function makeToken(overrides: Partial<TokenEntity>): TokenEntity {
  return {
    id: 't1',
    sectionId: 's1',
    index: 0,
    type: 'word',
    text: 'hello',
    wordKey: 'hello',
    ...overrides,
  }
}

describe('WordToken', () => {
  it('renders word token with text and data attributes', () => {
    render(<WordToken token={makeToken({ type: 'word', text: 'world', wordKey: 'world' })} />)
    const span = screen.getByText('world')
    expect(span).toHaveAttribute('data-type', 'word')
    expect(span).toHaveAttribute('data-word-key', 'world')
  })

  it('renders word token with null wordKey without data-word-key attribute', () => {
    render(<WordToken token={makeToken({ type: 'word', text: 'foo', wordKey: null })} />)
    const span = screen.getByText('foo')
    expect(span).toHaveAttribute('data-type', 'word')
    expect(span).not.toHaveAttribute('data-word-key')
  })

  it('renders punctuation token with data-type punctuation', () => {
    render(<WordToken token={makeToken({ type: 'punctuation', text: '.', wordKey: null })} />)
    const span = screen.getByText('.')
    expect(span).toHaveAttribute('data-type', 'punctuation')
  })

  it('renders whitespace token without crashing', () => {
    const { container } = render(<WordToken token={makeToken({ type: 'whitespace', text: ' ', wordKey: null })} />)
    expect(container.firstChild).toBeTruthy()
  })
})
