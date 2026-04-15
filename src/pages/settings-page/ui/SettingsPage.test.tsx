import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ok, err } from 'neverthrow'

vi.mock('../../../features', () => ({
  switchVault: vi.fn(),
}))

vi.mock('../../../shared/platform', () => ({
  filePickerAdapter: {
    pickDirectory: vi.fn(),
  },
}))

vi.mock('../../../shared/stores', () => ({
  useVaultStore: vi.fn(),
}))

import { SettingsPage } from './SettingsPage'
import { switchVault } from '../../../features'
import { filePickerAdapter } from '../../../shared/platform'
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
  vi.mocked(useVaultStore).mockImplementation(
    ((selector: (s: unknown) => unknown) => selector({ vaultPath: VAULT_PATH })) as never,
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

  it('picker returns path → confirm-switch state shown', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Switch to this folder')).toBeInTheDocument())
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

  it('confirm switch → switchVault called → idle on ok', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(switchVault).mockResolvedValue(ok(undefined))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Switch to this folder')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Switch to this folder'))
    await waitFor(() => expect(screen.getByText('Change location')).toBeInTheDocument())
    expect(switchVault).toHaveBeenCalledWith(NEW_PATH)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('switch returns err → inline error shown, idle state', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(switchVault).mockResolvedValue(err('Permission denied'))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Switch to this folder')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Switch to this folder'))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Permission denied'))
    expect(screen.getByText('Change location')).toBeInTheDocument()
  })

  it('cancel from confirm-switch → returns to idle', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Switch to this folder')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Cancel'))
    expect(screen.getByText('Change location')).toBeInTheDocument()
    expect(screen.queryByText('Switch to this folder')).not.toBeInTheDocument()
  })

  it('"switching" state disables all buttons', async () => {
    vi.mocked(filePickerAdapter.pickDirectory).mockResolvedValue(ok(NEW_PATH))
    vi.mocked(switchVault).mockReturnValue(new Promise(() => {}))
    renderPage()
    fireEvent.click(screen.getByText('Change location'))
    await waitFor(() => expect(screen.getByText('Switch to this folder')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Switch to this folder'))
    await waitFor(() => expect(screen.getByText('Switching vault…')).toBeInTheDocument())
    const buttons = screen.getAllByRole('button')
    for (const btn of buttons) expect(btn).toBeDisabled()
  })
})
