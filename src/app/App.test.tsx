import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import App from '../App'
import { createAppRouter } from './router'

describe('App', () => {
  it('renders without throwing', async () => {
    render(<App router={createAppRouter()} />)
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /Create new vault/i })).toBeInTheDocument()
    )
  })
})
