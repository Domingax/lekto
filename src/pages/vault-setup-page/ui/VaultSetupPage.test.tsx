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
  initVaultDesktop: vi.fn(),
  openExistingVaultDesktop: vi.fn(),
  openExistingVaultAndroid: vi.fn(),
  DESKTOP_DEFAULT_VAULT_NAME: 'lekto-vault',
  DEFAULT_ANDROID_PATH: 'lekto-vault',
}))

vi.mock('../../../shared/platform', () => ({
  isTauri: vi.fn().mockReturnValue(false),
  filePickerAdapter: {
    pickDirectory: vi.fn(),
  },
}))

vi.mock('@tauri-apps/api/path', () => ({
  documentDir: vi.fn().mockResolvedValue('/home/user/Documents'),
}))

import { VaultSetupPage } from './VaultSetupPage'
import { initVault, initVaultDesktop, openExistingVaultDesktop, openExistingVaultAndroid } from '../../../features'
import { isTauri, filePickerAdapter } from '../../../shared/platform'
import { documentDir } from '@tauri-apps/api/path'

function renderPage() {
  return render(
    <MemoryRouter>
      <VaultSetupPage />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(isTauri).mockReturnValue(false)
})

describe('VaultSetupPage — Android', () => {
  it('shows both buttons on initial render', () => {
    renderPage()
    expect(screen.getByText('Create new vault')).toBeInTheDocument()
    expect(screen.getByText('Open existing vault')).toBeInTheDocument()
  })

  it('clicking "Create new vault" shows confirm button', () => {
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    expect(screen.getByText('Confirm')).toBeInTheDocument()
  })

  it('Confirm calls initVault with Android path and navigates on success', async () => {
    vi.mocked(initVault).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Confirm'))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/library'))
    expect(initVault).toHaveBeenCalledWith('lekto-vault')
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

  it('Cancel button returns to idle state', () => {
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    expect(screen.getByText('Confirm')).toBeInTheDocument()
    fireEvent.click(screen.getByText('Cancel'))
    expect(screen.getByText('Create new vault')).toBeInTheDocument()
    expect(screen.queryByText('Confirm')).not.toBeInTheDocument()
  })

  it('"Modify" calls filePickerAdapter.pickDirectory and updates path', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok('/custom/path'))
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Modify'))
    await waitFor(() => expect(screen.getByText(/\/custom\/path/)).toBeInTheDocument())
    expect(filePickerAdapter.pickDirectory).toHaveBeenCalled()
  })

  it('clicking "Open existing vault" triggers filePickerAdapter.pickDirectory', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(err('cancelled'))
    renderPage()
    fireEvent.click(screen.getByText('Open existing vault'))
    await waitFor(() => expect(filePickerAdapter.pickDirectory).toHaveBeenCalled())
  })

  it('Android open: picker returns valid vault path → openExistingVaultAndroid called → navigates to /library', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok('/storage/emulated/0/Documents/my-vault'))
    vi.mocked(openExistingVaultAndroid).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByText('Open existing vault'))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/library'))
    expect(openExistingVaultAndroid).toHaveBeenCalledWith('/storage/emulated/0/Documents/my-vault')
  })

  it('Android open: invalid vault → openExistingVaultAndroid returns err → inline error shown, flow stays idle', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok('/storage/emulated/0/Documents/no-vault'))
    vi.mocked(openExistingVaultAndroid).mockResolvedValue(err('This folder does not contain a valid Lekto vault'))
    renderPage()
    fireEvent.click(screen.getByText('Open existing vault'))
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('This folder does not contain a valid Lekto vault'),
    )
    expect(mockNavigate).not.toHaveBeenCalled()
    expect(screen.getByText('Open existing vault')).toBeInTheDocument()
  })

  it('Android open: picker cancelled → no error shown, flow stays idle', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(err('cancelled'))
    renderPage()
    fireEvent.click(screen.getByText('Open existing vault'))
    await waitFor(() => expect(filePickerAdapter.pickDirectory).toHaveBeenCalled())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})

describe('VaultSetupPage — Desktop (Tauri)', () => {
  beforeEach(() => {
    vi.mocked(isTauri).mockReturnValue(true)
  })

  it('calls documentDir on mount and shows resolved path in create state', async () => {
    renderPage()
    // documentDir is called on mount
    await waitFor(() => expect(documentDir).toHaveBeenCalled())
    // navigate to create state to see the resolved path
    fireEvent.click(screen.getByText('Create new vault'))
    await waitFor(() =>
      expect(screen.getByText(/\/home\/user\/Documents\/lekto-vault/)).toBeInTheDocument(),
    )
  })

  it('"Modify" calls filePickerAdapter.pickDirectory and updates path', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok('/custom/vault'))
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Modify'))
    await waitFor(() => expect(screen.getByText(/\/custom\/vault/)).toBeInTheDocument())
    expect(filePickerAdapter.pickDirectory).toHaveBeenCalled()
  })

  it('Confirm calls initVaultDesktop with resolved path and navigates on success', async () => {
    vi.mocked(initVaultDesktop).mockResolvedValue(ok(undefined))
    renderPage()
    // wait for documentDir to resolve
    await waitFor(() =>
      expect(screen.queryByText('Resolving default location…')).not.toBeInTheDocument(),
    )
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Confirm'))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/library'))
    expect(initVaultDesktop).toHaveBeenCalledWith('/home/user/Documents/lekto-vault')
  })

  it('shows error and stays on create state when initVaultDesktop fails', async () => {
    vi.mocked(initVaultDesktop).mockResolvedValue(err('vault error'))
    renderPage()
    await waitFor(() =>
      expect(screen.queryByText('Resolving default location…')).not.toBeInTheDocument(),
    )
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Confirm'))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('vault error'))
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('Confirm is disabled until documentDir resolves (M2)', () => {
    vi.mocked(documentDir).mockReturnValue(new Promise(() => {}))
    renderPage()
    fireEvent.click(screen.getByText('Create new vault'))
    expect(screen.getByText('Confirm')).toBeDisabled()
  })

  it('shows error label and Confirm remains disabled when documentDir rejects (M1)', async () => {
    vi.mocked(documentDir).mockRejectedValue(new Error('path plugin unavailable'))
    renderPage()
    await waitFor(() =>
      expect(screen.queryByText('Resolving default location…')).not.toBeInTheDocument(),
    )
    fireEvent.click(screen.getByText('Create new vault'))
    expect(screen.getByText(/Could not resolve default location/)).toBeInTheDocument()
    expect(screen.getByText('Confirm')).toBeDisabled()
  })

  it('shows error in handleModify when pickDirectory returns a real failure (L2)', async () => {
    renderPage()
    await waitFor(() =>
      expect(screen.queryByText('Resolving default location…')).not.toBeInTheDocument(),
    )
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(err('Permission denied'))
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Modify'))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Permission denied'))
  })

  it('silently ignores cancel in handleModify on desktop (L2)', async () => {
    renderPage()
    await waitFor(() =>
      expect(screen.queryByText('Resolving default location…')).not.toBeInTheDocument(),
    )
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(err('cancelled'))
    fireEvent.click(screen.getByText('Create new vault'))
    fireEvent.click(screen.getByText('Modify'))
    await waitFor(() => expect(filePickerAdapter.pickDirectory).toHaveBeenCalled())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('Desktop open: picker returns valid vault path → openExistingVaultDesktop called → navigates to /library', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok('/home/user/Documents/my-vault'))
    vi.mocked(openExistingVaultDesktop).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByText('Open existing vault'))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/library'))
    expect(openExistingVaultDesktop).toHaveBeenCalledWith('/home/user/Documents/my-vault')
  })

  it('Desktop open: openExistingVaultDesktop returns err → inline error shown', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok('/home/user/Documents/bad-vault'))
    vi.mocked(openExistingVaultDesktop).mockResolvedValue(err('This folder does not contain a valid Lekto vault'))
    renderPage()
    fireEvent.click(screen.getByText('Open existing vault'))
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('This folder does not contain a valid Lekto vault'),
    )
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('Desktop open: picker returns real error (not cancelled) → inline error shown', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(err('Permission denied'))
    renderPage()
    fireEvent.click(screen.getByText('Open existing vault'))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Permission denied'))
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
