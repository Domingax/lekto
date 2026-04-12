import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { filePickerAdapter, filesystemAdapter, isTauri } from '../../../shared/platform'
import { relocateVaultDesktop, openExistingVaultDesktop, openExistingVaultAndroid } from '../../../features'
import { useVaultStore } from '../../../shared/stores'

type VaultFlowState = 'idle' | 'confirm-migrate' | 'confirm-switch' | 'migrating' | 'switching'

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
    const picked = result.value
    const existsResult = await filesystemAdapter.exists(`${picked}/lekto.db`)
    if (existsResult.isErr()) {
      setError(existsResult.error)
      return
    }
    setPickedPath(picked)
    if (existsResult.value) {
      setFlowState('confirm-switch')
    } else {
      setFlowState('confirm-migrate')
    }
  }

  async function handleConfirmMigrate() {
    if (!isTauri()) {
      setError('Vault migration is not yet supported on Android')
      setFlowState('idle')
      return
    }
    setFlowState('migrating')
    const result = await relocateVaultDesktop(pickedPath)
    if (result.isOk()) {
      setPickedPath('')
      setFlowState('idle')
    } else {
      setError(result.error)
      setFlowState('idle')
    }
  }

  async function handleConfirmSwitch() {
    setFlowState('switching')
    const result = isTauri()
      ? await openExistingVaultDesktop(pickedPath)
      : await openExistingVaultAndroid(pickedPath)
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

        {flowState === 'confirm-migrate' && (
          <div>
            <p>New location: {pickedPath}</p>
            <Button onClick={handleConfirmMigrate}>Migrate current data here</Button>
            <Button variant="outline" onClick={handleCancel}>
              Cancel
            </Button>
          </div>
        )}

        {flowState === 'confirm-switch' && (
          <div>
            <p>New location: {pickedPath}</p>
            <Button onClick={handleConfirmSwitch}>Use existing vault data</Button>
            <Button variant="outline" onClick={handleCancel}>
              Cancel
            </Button>
          </div>
        )}

        {flowState === 'migrating' && (
          <div>
            <p>Migrating vault…</p>
            <Button disabled>Migrate current data here</Button>
            <Button variant="outline" disabled>
              Cancel
            </Button>
          </div>
        )}

        {flowState === 'switching' && (
          <div>
            <p>Switching vault…</p>
            <Button disabled>Use existing vault data</Button>
            <Button variant="outline" disabled>
              Cancel
            </Button>
          </div>
        )}
      </section>
    </div>
  )
}
