import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ok, err } from 'neverthrow'

vi.mock('../../../features', () => ({
  relocateVaultDesktop: vi.fn(),
  openExistingVaultDesktop: vi.fn(),
  openExistingVaultAndroid: vi.fn(),
}))

vi.mock('../../../shared/platform', () => ({
  isTauri: vi.fn().mockReturnValue(false),
  filePickerAdapter: {
    pickDirectory: vi.fn(),
  },
  filesystemAdapter: {
    exists: vi.fn(),
  },
}))

vi.mock('../../../shared/stores', () => ({
  useVaultStore: vi.fn(),
}))

import { SettingsPage } from './SettingsPage'
import { relocateVaultDesktop, openExistingVaultDesktop, openExistingVaultAndroid } from '../../../features'
import { isTauri, filePickerAdapter, filesystemAdapter } from '../../../shared/platform'
import { useVaultStore } from '../../../shared/stores'

const VAULT_PATH = '/some/vault'
const NEW_PATH = '/new/vault'

function renderPage() {
  return render(
    <MemoryRouter>
      <SettingsPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(isTauri).mockReturnValue(false)
  vi.mocked(useVaultStore).mockImplementation((selector: (s: unknown) => unknown) =>
    selector({ vaultPath: VAULT_PATH }),
  )
})

describe('SettingsPage', () => {
  it('renders current vault path from useVaultStore', () => {
    renderPage()
    expect(screen.getByText(VAULT_PATH)).toBeInTheDocument()
  })

  it('"Change location" button is present and enabled when vault is active', () => {
    renderPage()
    expect(screen.getByText('Change location')).toBeEnabled()
  })

  it('clicking "Change location" calls filePickerAdapter.pickDirectory', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(err('cancelled'))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(filePickerAdapter.pickDirectory).toHaveBeenCalled())
  })

  it('picker returns path with existing lekto.db → confirm-switch state shown', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(filesystemAdapter.exists).mockResolvedValue(ok(true))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Use existing vault data')).toBeInTheDocument())
    expect(screen.getByText(new RegExp(NEW_PATH))).toBeInTheDocument()
  })

  it('picker returns path without lekto.db → confirm-migrate state shown', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(filesystemAdapter.exists).mockResolvedValue(ok(false))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Migrate current data here')).toBeInTheDocument())
    expect(screen.getByText(new RegExp(NEW_PATH))).toBeInTheDocument()
  })

  it('picker cancelled → state stays idle, no error shown', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(err('cancelled'))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(filePickerAdapter.pickDirectory).toHaveBeenCalled())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('Change location')).toBeInTheDocument()
  })

  it('picker errors → inline error shown', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(err('Permission denied'))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Permission denied'))
  })

  it('confirm migrate → relocateVaultDesktop called → on ok, idle state', async () => {
    vi.mocked(isTauri).mockReturnValue(true)
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(filesystemAdapter.exists).mockResolvedValue(ok(false))
    vi.mocked(relocateVaultDesktop).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Migrate current data here')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Migrate current data here'))
    await waitFor(() => expect(screen.getByText('Change location')).toBeInTheDocument())
    expect(relocateVaultDesktop).toHaveBeenCalledWith(NEW_PATH)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('confirm migrate → relocateVaultDesktop returns err → inline error shown, idle state', async () => {
    vi.mocked(isTauri).mockReturnValue(true)
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(filesystemAdapter.exists).mockResolvedValue(ok(false))
    vi.mocked(relocateVaultDesktop).mockResolvedValue(err('copy failed'))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Migrate current data here')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Migrate current data here'))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('copy failed'))
    expect(screen.getByText('Change location')).toBeInTheDocument()
  })

  it('confirm switch (Desktop) → openExistingVaultDesktop called → on ok, idle state', async () => {
    vi.mocked(isTauri).mockReturnValue(true)
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(filesystemAdapter.exists).mockResolvedValue(ok(true))
    vi.mocked(openExistingVaultDesktop).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Use existing vault data')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Use existing vault data'))
    await waitFor(() => expect(screen.getByText('Change location')).toBeInTheDocument())
    expect(openExistingVaultDesktop).toHaveBeenCalledWith(NEW_PATH)
  })

  it('confirm switch (Android) → openExistingVaultAndroid called', async () => {
    vi.mocked(isTauri).mockReturnValue(false)
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(filesystemAdapter.exists).mockResolvedValue(ok(true))
    vi.mocked(openExistingVaultAndroid).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Use existing vault data')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Use existing vault data'))
    await waitFor(() => expect(screen.getByText('Change location')).toBeInTheDocument())
    expect(openExistingVaultAndroid).toHaveBeenCalledWith(NEW_PATH)
  })

  it('cancel from confirm state → returns to idle', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(filesystemAdapter.exists).mockResolvedValue(ok(false))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Migrate current data here')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Cancel'))
    expect(screen.getByText('Change location')).toBeInTheDocument()
    expect(screen.queryByText('Migrate current data here')).not.toBeInTheDocument()
  })

  it('"migrating" state disables all buttons', async () => {
    vi.mocked(isTauri).mockReturnValue(true)
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(filesystemAdapter.exists).mockResolvedValue(ok(false))
    // Never resolves — holds in migrating state
    vi.mocked(relocateVaultDesktop).mockReturnValue(new Promise(() => {}))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Migrate current data here')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Migrate current data here'))
    await waitFor(() => expect(screen.getByText('Migrating vault…')).toBeInTheDocument())
    const buttons = screen.getAllByRole('button')
    for (const btn of buttons) expect(btn).toBeDisabled()
  })
})
