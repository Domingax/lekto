import { useState, useRef, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { filePickerAdapter } from '@/shared/platform'
import { importBook } from '@/features/import-book'
import { useVaultStore } from '@/shared/stores'
import { getDb, schema } from '@/shared/db'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'

const SEED_LANGUAGES = [
  { code: 'en', name: 'English' },
  { code: 'fr', name: 'French' },
  { code: 'es', name: 'Spanish' },
  { code: 'de', name: 'German' },
  { code: 'it', name: 'Italian' },
  { code: 'pt', name: 'Portuguese' },
  { code: 'nl', name: 'Dutch' },
  { code: 'ru', name: 'Russian' },
  { code: 'zh', name: 'Chinese' },
  { code: 'ja', name: 'Japanese' },
  { code: 'ko', name: 'Korean' },
  { code: 'ar', name: 'Arabic' },
  { code: 'pl', name: 'Polish' },
  { code: 'sv', name: 'Swedish' },
  { code: 'tr', name: 'Turkish' },
]

export function LibraryPage() {
  const books = useVaultStore((s) => s.books)

  useEffect(() => {
    try {
      getDb().select().from(schema.books).then((rows) => {
        useVaultStore.getState().setBooks(rows)
      }).catch(() => { /* DB not ready yet — vault not configured */ })
    } catch {
      // DB not initialized — vault not configured yet
    }
  }, [])

  const [isImporting, setIsImporting] = useState(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [langDialogOpen, setLangDialogOpen] = useState(false)
  const [detectedLang, setDetectedLang] = useState<string | null>(null)
  const [selectedLang, setSelectedLang] = useState<string>('en')
  const langResolverRef = useRef<((code: string | null) => void) | null>(null)

  const resolveLanguage = (detected: string | null): Promise<string | null> =>
    new Promise((resolve) => {
      setDetectedLang(detected)
      setSelectedLang(detected ?? 'en')
      langResolverRef.current = resolve
      setLangDialogOpen(true)
    })

  const handleLangConfirm = () => {
    setLangDialogOpen(false)
    langResolverRef.current?.(selectedLang)
  }

  const handleLangCancel = () => {
    setLangDialogOpen(false)
    langResolverRef.current?.(null)
  }

  const handleImport = async () => {
    setImportError(null)
    const pickerResult = await filePickerAdapter.pickFile({ accept: ['.epub'] })
    if (pickerResult.isErr()) return

    const { data, name } = pickerResult.value
    setIsImporting(true)
    const result = await importBook(data, name, resolveLanguage)
    setIsImporting(false)
    if (result.isErr()) {
      setImportError(result.error)
    }
  }

  return (
    <div>
      <button onClick={handleImport} disabled={isImporting}>
        Import EPUB
      </button>

      {isImporting && <p>Importing…</p>}
      {importError && <p role="alert">{importError}</p>}

      <Dialog open={langDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm language</DialogTitle>
          </DialogHeader>
          <p>Detected: {detectedLang ?? 'unknown'}</p>
          <select
            value={selectedLang}
            onChange={(e) => setSelectedLang(e.target.value)}
          >
            {SEED_LANGUAGES.map((l) => (
              <option key={l.code} value={l.code}>
                {l.name}
              </option>
            ))}
          </select>
          <DialogFooter>
            <button onClick={handleLangCancel}>Cancel</button>
            <button onClick={handleLangConfirm}>Confirm</button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {books.map((b) => (
        <div key={b.id}>
          {b.title} · {b.language} · 0%
        </div>
      ))}

      <Link to="/settings">Settings</Link>
    </div>
  )
}
