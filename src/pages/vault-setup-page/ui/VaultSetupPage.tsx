import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Capacitor } from '@capacitor/core'
import { Button } from '@/components/ui/button'
import { filePickerAdapter } from '../../../shared/platform'
import {
  initVault,
  initVaultWithNativeHandle,
  WEB_OPFS_PATH,
  DEFAULT_ANDROID_PATH,
} from '../../../features'

type FlowState = 'idle' | 'create' | 'creating'

const isNativePlatform = Capacitor.isNativePlatform()

export function VaultSetupPage() {
  const hasDirectoryPicker = !isNativePlatform && 'showDirectoryPicker' in globalThis
  const navigate = useNavigate()

  const defaultPath = isNativePlatform ? DEFAULT_ANDROID_PATH : WEB_OPFS_PATH
  const defaultLabel = isNativePlatform
    ? DEFAULT_ANDROID_PATH
    : 'Built-in storage (OPFS)'

  const [flowState, setFlowState] = useState<FlowState>('idle')
  const [selectedPath, setSelectedPath] = useState(defaultPath)
  const [selectedLabel, setSelectedLabel] = useState(defaultLabel)
  const [selectedHandle, setSelectedHandle] = useState<FileSystemDirectoryHandle | null>(null)
  const [error, setError] = useState<string | null>(null)

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
          globalThis as typeof globalThis & { showDirectoryPicker: () => Promise<FileSystemDirectoryHandle> }
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
    if (flowState === 'creating') return
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
