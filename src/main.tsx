import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { initDb, runMigrations, seedLanguages, getDb, schema } from '@/shared/db'
import { useVaultStore } from '@/shared/stores'
import { getVaultPath } from '@/features'
import { createAppRouter } from '@/app/router'

let _stepEl: HTMLElement | null = null

function showStep(step: string) {
  if (!_stepEl) {
    _stepEl = document.createElement('p')
    _stepEl.style.cssText = 'color:#aaa;padding:1rem;font-family:monospace;font-size:12px'
    document.getElementById('root')?.appendChild(_stepEl)
  }
  _stepEl.textContent = `[startup] ${step}`
  console.debug(`[startup] ${step}`)
}

function showError(message: string) {
  const el = document.getElementById('root')!
  const p = document.createElement('p')
  p.style.cssText = 'color:red;padding:1rem'
  p.textContent = message
  el.replaceChildren(p)
}

window.addEventListener('unhandledrejection', (e) => {
  showError(`Unhandled error: ${e.reason instanceof Error ? e.reason.message : String(e.reason)}`)
})

export async function start() {
  showStep('initDb…')
  const db = await initDb()
  if (db.isErr()) {
    showError(`Database initialization failed: ${db.error}`)
    return
  }

  if (db.value !== null) {
    showStep('runMigrations…')
    const migrations = await runMigrations()
    if (migrations.isErr()) {
      showError(`Database migration failed: ${migrations.error}`)
      return
    }

    showStep('seedLanguages…')
    const seed = await seedLanguages()
    if (seed.isErr()) {
      showError(`Database seed failed: ${seed.error}`)
      return
    }

    showStep('hydrateBooks…')
    const books = await getDb().select().from(schema.books)
    useVaultStore.getState().setBooks(books)
  }

  showStep('getVaultPath…')
  const vaultPathResult = await getVaultPath()
  if (vaultPathResult.isOk()) {
    useVaultStore.getState().setVaultPath(vaultPathResult.value)
  }

  showStep('createRoot…')
  // Router is created here — after the vault store is populated — so that
  // rootLoader sees the correct vaultPath on its very first navigation.
  const router = createAppRouter()

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App router={router} />
    </StrictMode>,
  )
}

start()
