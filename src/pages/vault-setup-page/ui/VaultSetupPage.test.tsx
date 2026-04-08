import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ok, err } from 'neverthrow'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../../../features', () => ({
  initVault: vi.fn(),
  initVaultWithNativeHandle: vi.fn(),
  WEB_OPFS_PATH: '__opfs__',
  DEFAULT_ANDROID_PATH: 'lekto-vault',
}))

vi.mock('../../../shared/platform', () => ({
  filePickerAdapter: {
    pickDirectory: vi.fn(),
  },
}))

// Capacitor.isNativePlatform() returns false in jsdom
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => false },
}))

import { VaultSetupPage } from './VaultSetupPage'
import { initVault, initVaultWithNativeHandle } from '../../../features'

function renderPage() {
  return render(
    <MemoryRouter>
      <VaultSetupPage />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('VaultSetupPage', () => {
  it('shows both buttons on initial render', () => {
    renderPage()
    expect(screen.getByText('Create new vault')).toBeInTheDocument()
    expect(screen.getByText('Open existing vault')).toBeInTheDocument()
  })

  it('"Open existing vault" is disabled', () => {
    renderPage()
    expect(screen.getByText('Open existing vault')).toBeDisabled()
  })

  it('clicking "Create new vault" shows confirm button', () => {
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    expect(screen.getByText('Confirm')).toBeInTheDocument()
  })

  it('Confirm calls initVault and navigates on success', async () => {
    vi.mocked(initVault).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Confirm'))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/library'))
    expect(initVault).toHaveBeenCalledWith('__opfs__')
  })

  it('shows error and stays on create state when initVault fails', async () => {
    vi.mocked(initVault).mockResolvedValue(err('disk full'))
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Confirm'))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('disk full'))
    expect(mockNavigate).not.toHaveBeenCalled()
    expect(screen.getByText('Confirm')).toBeInTheDocument()
  })

  it('Cancel button returns to idle state', async () => {
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    expect(screen.getByText('Confirm')).toBeInTheDocument()
    fireEvent.click(screen.getByText('Cancel'))
    expect(screen.getByText('Create new vault')).toBeInTheDocument()
    expect(screen.queryByText('Confirm')).not.toBeInTheDocument()
  })

  it('Confirm calls initVaultWithNativeHandle when a native handle is selected', async () => {
    const mockHandle = { name: 'my-vault' } as FileSystemDirectoryHandle
    vi.stubGlobal('showDirectoryPicker', vi.fn().mockResolvedValue(mockHandle))
    vi.mocked(initVaultWithNativeHandle).mockResolvedValue(ok(undefined))

    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Choose folder'))
    await waitFor(() => expect(screen.getByText(/my-vault/)).toBeInTheDocument())
    fireEvent.click(screen.getByText('Confirm'))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/library'))
    expect(initVaultWithNativeHandle).toHaveBeenCalledWith(mockHandle)

    vi.unstubAllGlobals()
  })
})
