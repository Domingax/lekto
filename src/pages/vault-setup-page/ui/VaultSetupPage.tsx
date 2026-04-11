import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { documentDir } from '@tauri-apps/api/path'
import { Button } from '@/components/ui/button'
import { filePickerAdapter, isTauri } from '../../../shared/platform'
import {
  initVault,
  initVaultDesktop,
  openExistingVaultDesktop,
  openExistingVaultAndroid,
  DESKTOP_DEFAULT_VAULT_NAME,
  DEFAULT_ANDROID_PATH,
} from '../../../features'

type FlowState = 'idle' | 'create' | 'creating' | 'opening'

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
    if (!isDesktop) return
    async function resolveDefaultPath() {
      try {
        const dir = await documentDir()
        const defaultPath = `${dir}/${DESKTOP_DEFAULT_VAULT_NAME}`
        setSelectedPath(defaultPath)
        setSelectedLabel(defaultPath)
      } catch {
        setSelectedLabel('Could not resolve default location — use Modify to choose manually')
      }
    }
    void resolveDefaultPath()
    // isDesktop is a stable platform flag — intentionally omitted from deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleModify() {
    setError(null)
    const result = await filePickerAdapter.pickDirectory()
    if (result.isOk()) {
      setSelectedPath(result.value)
      setSelectedLabel(result.value)
    } else if (isDesktop && result.error !== 'cancelled') {
      setError(result.error)
    }
  }

  async function handleOpenPick() {
    setError(null)
    const result = await filePickerAdapter.pickDirectory()
    if (result.isOk()) {
      setFlowState('opening')
      const openResult = isDesktop
        ? await openExistingVaultDesktop(result.value)
        : await openExistingVaultAndroid(result.value)
      if (openResult.isOk()) {
        navigate('/library')
      } else {
        setError(openResult.error)
        setFlowState('idle')
      }
    } else {
      if (isDesktop && result.error !== 'cancelled') {
        setError(result.error)
      }
      setFlowState('idle')
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

  if (flowState === 'idle' || flowState === 'opening') {
    return (
      <div>
        <h1>Set up your vault</h1>
        {error && <p role="alert">{error}</p>}
        <Button onClick={() => setFlowState('create')} disabled={flowState === 'opening'}>
          Create new vault
        </Button>
        <Button
          variant="outline"
          onClick={handleOpenPick}
          disabled={flowState === 'opening'}
        >
          {flowState === 'opening' ? 'Opening…' : 'Open existing vault'}
        </Button>
        {flowState === 'opening' && <p>Opening vault…</p>}
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
      <Button onClick={handleConfirm} disabled={flowState === 'creating' || (isDesktop && selectedPath === '')}>
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
