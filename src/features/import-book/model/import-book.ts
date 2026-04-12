import { ok, err } from 'neverthrow'
import type { AsyncResult } from '@/shared/lib'
import type { BookEntity } from '@/entities'
import { filesystemAdapter } from '@/shared/platform'
import { useVaultStore } from '@/shared/stores'
import { getDb, schema } from '@/shared/db'
import { parseEpub } from '../api/parse-epub'
import { detectLanguage } from './detect-language'
import { tokenizeSection } from './tokenize-content'

const CHUNK = 500

export async function importBook(
  data: ArrayBuffer,
  fileName: string,
  resolveLanguage: (detected: string | null) => Promise<string | null>,
): AsyncResult<BookEntity> {
  // Step 1: parse EPUB
  const parseResult = await parseEpub(data)
  if (parseResult.isErr()) return err(parseResult.error)
  const parsedBook = parseResult.value

  // Step 2: detect language
  const allText = parsedBook.sections.map((s) => s.text).join(' ')
  const detectedCode = detectLanguage(allText)

  // Step 3: resolve language via dialog callback
  const language = await resolveLanguage(detectedCode)
  if (language === null) return err('Import cancelled')

  // Step 4: save EPUB to vault
  const vaultPath = useVaultStore.getState().vaultPath!
  const destPath = `${vaultPath}/books/${fileName}`
  const saveResult = await filesystemAdapter.writeFileBinary(destPath, new Uint8Array(data))
  if (saveResult.isErr()) return err(`Failed to save EPUB: ${saveResult.error}`)

  // Step 5: build row objects in memory
  const bookId = crypto.randomUUID()
  const now = Math.floor(Date.now() / 1000)

  const bookEntity: BookEntity = {
    id: bookId,
    title: parsedBook.title,
    fileName,
    language,
    coverPath: null,
    createdAt: now,
  }

  const sectionRows = parsedBook.sections.map((s, i) => ({
    id: `${bookId}_${i}`,
    bookId,
    index: i,
    title: s.title,
  }))

  const allTokenRows = parsedBook.sections.flatMap((s, i) => {
    const sectionId = `${bookId}_${i}`
    return tokenizeSection(s.text, sectionId)
  })

  // Step 6: bulk insert — book → sections → tokens
  const db = getDb()
  await db.insert(schema.books).values([bookEntity])
  if (sectionRows.length > 0) {
    await db.insert(schema.sections).values(sectionRows)
  }
  for (let i = 0; i < allTokenRows.length; i += CHUNK) {
    await db.insert(schema.tokens).values(allTokenRows.slice(i, i + CHUNK))
  }

  // Step 7: update Zustand store (optimistic, no await)
  useVaultStore.getState().addBook(bookEntity)

  return ok(bookEntity)
}
