import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Capacitor } from '@capacitor/core'
import { Button } from '@/components/ui/button'
import { useVaultStore } from '../../../shared/stores'
import { filePickerAdapter } from '../../../shared/platform'
import {
  initVault,
  initVaultWithNativeHandle,
  grantVaultPermission,
  WEB_OPFS_PATH,
  DEFAULT_ANDROID_PATH,
} from '../../../features'

type FlowState = 'idle' | 'create' | 'creating'

const isNativePlatform = Capacitor.isNativePlatform()
const hasDirectoryPicker =
  !isNativePlatform && typeof window !== 'undefined' && 'showDirectoryPicker' in window

export function VaultSetupPage() {
  const navigate = useNavigate()
  const pendingPermissionHandle = useVaultStore((s) => s.pendingPermissionHandle)

  const defaultPath = isNativePlatform ? DEFAULT_ANDROID_PATH : WEB_OPFS_PATH
  const defaultLabel = isNativePlatform
    ? DEFAULT_ANDROID_PATH
    : 'Built-in storage (OPFS)'

  const [flowState, setFlowState] = useState<FlowState>('idle')
  const [selectedPath, setSelectedPath] = useState(defaultPath)
  const [selectedLabel, setSelectedLabel] = useState(defaultLabel)
  const [selectedHandle, setSelectedHandle] = useState<FileSystemDirectoryHandle | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Derived: permission re-grant takes precedence over normal flow
  const isGrantPermission = pendingPermissionHandle !== null

  async function handleModify() {
    setError(null)
    if (isNativePlatform) {
      const result = await filePickerAdapter.pickDirectory()
      if (result.isOk()) {
        setSelectedPath(result.value)
        setSelectedLabel(result.value)
        setSelectedHandle(null)
      }
    } else if (hasDirectoryPicker) {
      try {
        const handle = await (
          window as Window & { showDirectoryPicker: () => Promise<FileSystemDirectoryHandle> }
        ).showDirectoryPicker()
        setSelectedHandle(handle)
        setSelectedLabel(handle.name)
        setSelectedPath(handle.name)
      } catch {
        // user cancelled — no change
      }
    }
  }

  async function handleConfirm() {
    setError(null)
    setFlowState('creating')
    let result
    if (selectedHandle) {
      result = await initVaultWithNativeHandle(selectedHandle)
    } else {
      result = await initVault(selectedPath)
    }
    if (result.isOk()) {
      navigate('/library')
    } else {
      setError(result.error)
      setFlowState('create')
    }
  }

  async function handleGrantPermission() {
    if (!pendingPermissionHandle) return
    setError(null)
    const result = await grantVaultPermission(pendingPermissionHandle)
    if (result.isOk()) {
      navigate('/library')
    } else {
      setError(result.error)
    }
  }

  if (isGrantPermission) {
    return (
      <div>
        <h1>Restore vault access</h1>
        <p>
          Your browser requires you to confirm access to your vault folder after each session.
          Click the button below to restore access.
        </p>
        {error && <p role="alert">{error}</p>}
        <Button onClick={handleGrantPermission}>Restore vault access</Button>
      </div>
    )
  }

  if (flowState === 'idle') {
    return (
      <div>
        <h1>Set up your vault</h1>
        <Button onClick={() => setFlowState('create')}>Create new vault</Button>
        <Button variant="outline" disabled>
          Open existing vault
        </Button>
      </div>
    )
  }

  return (
    <div>
      <h1>Create new vault</h1>
      <p>Location: {selectedLabel}</p>
      {(isNativePlatform || hasDirectoryPicker) && (
        <Button variant="outline" onClick={handleModify} disabled={flowState === 'creating'}>
          {isNativePlatform ? 'Modify' : 'Choose folder'}
        </Button>
      )}
      {error && <p role="alert">{error}</p>}
      <Button onClick={handleConfirm} disabled={flowState === 'creating'}>
        {flowState === 'creating' ? 'Creating…' : 'Confirm'}
      </Button>
      <Button
        variant="outline"
        onClick={() => setFlowState('idle')}
        disabled={flowState === 'creating'}
      >
        Cancel
      </Button>
    </div>
  )
}
