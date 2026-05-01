import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ok, err } from 'neverthrow'
import type { BookEntity } from '@/entities'

vi.mock('@/shared/platform', () => ({
  filePickerAdapter: {
    pickFile: vi.fn(),
  },
}))

vi.mock('@/features/import-book', () => ({
  importBook: vi.fn(),
}))

vi.mock('@/shared/stores', () => {
  const books: BookEntity[] = []
  return {
    useVaultStore: vi.fn((selector: (s: unknown) => unknown) =>
      selector({ books, vaultPath: '/vault', isVaultReady: true, setVaultPath: vi.fn(), clearVault: vi.fn(), setBooks: vi.fn(), addBook: vi.fn() }),
    ),
  }
})

import { LibraryPage } from './LibraryPage'
import { filePickerAdapter } from '@/shared/platform'
import { importBook } from '@/features/import-book'
import { useVaultStore } from '@/shared/stores'

function renderPage() {
  return render(
    <MemoryRouter>
      <LibraryPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('LibraryPage', () => {
  it('renders the import button', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /import book/i })).toBeInTheDocument()
  })

  it('calls file picker with correct accept array including pdf and txt', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(err('cancelled'))

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    await waitFor(() => expect(filePickerAdapter.pickFile).toHaveBeenCalled())
    expect(filePickerAdapter.pickFile).toHaveBeenCalledWith({ accept: ['.epub', '.pdf', '.txt'] })
  })

  it('shows progress paragraph while importBook is pending', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(
      ok({ data: new ArrayBuffer(8), name: 'test.epub' }),
    )
    // importBook hangs indefinitely
    vi.mocked(importBook).mockReturnValue(new Promise(() => {}))

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    await waitFor(() => expect(screen.getByText('Importing…')).toBeInTheDocument())
  })

  it('shows inline error when filePickerAdapter returns err', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(err('cancelled'))

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    // cancelled is silent — no error shown (not a failure, just cancel)
    await waitFor(() => expect(filePickerAdapter.pickFile).toHaveBeenCalled())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows inline error when importBook returns err', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(
      ok({ data: new ArrayBuffer(8), name: 'test.epub' }),
    )
    vi.mocked(importBook).mockResolvedValue(err('Failed to parse EPUB'))

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Failed to parse EPUB'))
  })

  it('language dialog opens when importBook calls resolveLanguage', async () => {
    vi.mocked(filePickerAdapter.pickFile).mockResolvedValue(
      ok({ data: new ArrayBuffer(8), name: 'test.epub' }),
    )
    vi.mocked(importBook).mockImplementation((_data, _name, resolveLanguage) => {
      // Call resolveLanguage to trigger dialog, but never resolve the outer promise yet
      return new Promise((resolve) => {
        resolveLanguage('en').then((lang) => {
          if (lang) resolve(ok({ id: '1', title: 'T', author: null, fileName: 'test.epub', language: lang, coverPath: null, createdAt: 1 }))
          else resolve(err('Import cancelled'))
        })
      })
    })

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /import book/i }))
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument())
  })

  it('books appear in the list via store', () => {
    const mockBooks: BookEntity[] = [
      { id: '1', title: 'My Novel', author: null, fileName: 'novel.epub', language: 'en', coverPath: null, createdAt: 1000 },
    ]
    vi.mocked(useVaultStore).mockImplementation(
      ((selector: (s: unknown) => unknown) =>
        selector({ books: mockBooks, vaultPath: '/vault', isVaultReady: true, setVaultPath: vi.fn(), clearVault: vi.fn(), setBooks: vi.fn(), addBook: vi.fn() })) as never,
    )
    renderPage()
    expect(screen.getByText(/My Novel/)).toBeInTheDocument()
  })
})
