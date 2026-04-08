import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { documentDir } from '@tauri-apps/api/path'
import { Button } from '@/components/ui/button'
import { filePickerAdapter, isTauri } from '../../../shared/platform'
import {
  initVault,
  initVaultDesktop,
  DESKTOP_DEFAULT_VAULT_NAME,
  DEFAULT_ANDROID_PATH,
} from '../../../features'

type FlowState = 'idle' | 'create' | 'creating'

export function VaultSetupPage() {
  const isDesktop = isTauri()
  const navigate = useNavigate()

  const [flowState, setFlowState] = useState<FlowState>('idle')
  const [selectedPath, setSelectedPath] = useState(() =>
    isDesktop ? '' : DEFAULT_ANDROID_PATH,
  )
  const [selectedLabel, setSelectedLabel] = useState(() =>
    isDesktop ? 'Resolving default location…' : DEFAULT_ANDROID_PATH,
  )
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (isDesktop) {
      documentDir().then((dir) => {
        const defaultPath = `${dir}/${DESKTOP_DEFAULT_VAULT_NAME}`
        setSelectedPath(defaultPath)
        setSelectedLabel(defaultPath)
      })
    }
    // isDesktop is a stable platform flag — intentionally omitted from deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleModify() {
    setError(null)
    const result = await filePickerAdapter.pickDirectory()
    if (result.isOk()) {
      setSelectedPath(result.value)
      setSelectedLabel(result.value)
    }
  }

  async function handleConfirm() {
    if (flowState === 'creating') return
    setError(null)
    setFlowState('creating')
    const result = isDesktop
      ? await initVaultDesktop(selectedPath)
      : await initVault(selectedPath)
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
      <Button variant="outline" onClick={handleModify} disabled={flowState === 'creating'}>
        Modify
      </Button>
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
