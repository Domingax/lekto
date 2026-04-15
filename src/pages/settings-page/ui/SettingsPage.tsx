import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { filePickerAdapter } from '../../../shared/platform'
import { switchVault } from '../../../features'
import { useVaultStore } from '../../../shared/stores'

type VaultFlowState = 'idle' | 'confirm-switch' | 'switching'

export function SettingsPage() {
  const vaultPath = useVaultStore((s) => s.vaultPath)
  const [flowState, setFlowState] = useState<VaultFlowState>('idle')
  const [pickedPath, setPickedPath] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleChangePick() {
    setError(null)
    const result = await filePickerAdapter.pickDirectory()
    if (result.isErr()) {
      if (result.error !== 'cancelled') setError(result.error)
      return
    }
    setPickedPath(result.value)
    setFlowState('confirm-switch')
  }

  async function handleConfirmSwitch() {
    setFlowState('switching')
    const result = await switchVault(pickedPath)
    if (result.isOk()) {
      setPickedPath('')
      setFlowState('idle')
    } else {
      setError(result.error)
      setFlowState('idle')
    }
  }

  function handleCancel() {
    setFlowState('idle')
    setPickedPath('')
    setError(null)
  }

  return (
    <div>
      <h1>Settings</h1>
      <section>
        <h2>Vault</h2>
        <p>{vaultPath ?? 'Not configured'}</p>
        {error && <p role="alert">{error}</p>}

        {flowState === 'idle' && (
          <Button onClick={handleChangePick} disabled={!vaultPath}>
            Change location
          </Button>
        )}

        {flowState === 'confirm-switch' && (
          <div>
            <p>New location: {pickedPath}</p>
            <Button onClick={handleConfirmSwitch}>Switch to this folder</Button>
            <Button variant="outline" onClick={handleCancel}>
              Cancel
            </Button>
          </div>
        )}

        {flowState === 'switching' && (
          <div>
            <p>Switching vault…</p>
            <Button disabled>Switch to this folder</Button>
            <Button variant="outline" disabled>
              Cancel
            </Button>
          </div>
        )}
      </section>
    </div>
  )
}
