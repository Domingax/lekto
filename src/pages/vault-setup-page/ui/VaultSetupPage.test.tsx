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
  grantVaultPermission: vi.fn(),
  WEB_OPFS_PATH: '__opfs__',
  DEFAULT_ANDROID_PATH: 'lekto-vault',
}))

vi.mock('../../../shared/platform', () => ({
  filePickerAdapter: {
    pickDirectory: vi.fn(),
  },
}))

const mockUseVaultStore = vi.fn()
vi.mock('../../../shared/stores', () => ({
  useVaultStore: (selector: (s: { pendingPermissionHandle: null }) => unknown) =>
    mockUseVaultStore(selector),
}))

// Capacitor.isNativePlatform() returns false in jsdom
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => false },
}))

import { VaultSetupPage } from './VaultSetupPage'
import { initVault, grantVaultPermission } from '../../../features'

function renderPage() {
  return render(
    <MemoryRouter>
      <VaultSetupPage />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockUseVaultStore.mockImplementation(
    (selector: (s: { pendingPermissionHandle: null }) => unknown) =>
      selector({ pendingPermissionHandle: null }),
  )
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

  it('renders grant-permission state when pendingPermissionHandle is set', () => {
    const mockHandle = {} as FileSystemDirectoryHandle
    mockUseVaultStore.mockImplementation(
      (selector: (s: { pendingPermissionHandle: FileSystemDirectoryHandle }) => unknown) =>
        selector({ pendingPermissionHandle: mockHandle }),
    )
    renderPage()
    expect(screen.getByRole('button', { name: 'Restore vault access' })).toBeInTheDocument()
  })

  it('"Restore vault access" click calls grantVaultPermission and navigates', async () => {
    const mockHandle = {} as FileSystemDirectoryHandle
    mockUseVaultStore.mockImplementation(
      (selector: (s: { pendingPermissionHandle: FileSystemDirectoryHandle }) => unknown) =>
        selector({ pendingPermissionHandle: mockHandle }),
    )
    vi.mocked(grantVaultPermission).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Restore vault access' }))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/library'))
    expect(grantVaultPermission).toHaveBeenCalledWith(mockHandle)
  })
})
