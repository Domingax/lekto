import { ok, err } from 'neverthrow'
import type { AsyncResult } from '@/shared/lib'
import type { BookEntity } from '@/entities'
import { filesystemAdapter } from '@/shared/platform'
import { useVaultStore } from '@/shared/stores'
import { getDb, schema } from '@/shared/db'
import { parseEpub } from '../api/parse-epub'
import { parsePdf } from '../api/parse-pdf'
import { parseTxt } from '../api/parse-txt'
import { detectLanguage } from './detect-language'
import { tokenizeSection } from './tokenize-content'

const CHUNK = 500

export async function importBook(
  data: ArrayBuffer,
  fileName: string,
  resolveLanguage: (detected: string | null) => Promise<string | null>,
): AsyncResult<BookEntity> {
  // Step 1: parse file by format
  const ext = fileName.split('.').pop()?.toLowerCase()
  let parseResult
  if (ext === 'epub') {
    parseResult = await parseEpub(data)
  } else if (ext === 'pdf') {
    parseResult = await parsePdf(data.slice(0), fileName)
  } else if (ext === 'txt') {
    parseResult = await parseTxt(data, fileName)
  } else {
    return err(`Unsupported file format: .${ext ?? 'unknown'}`)
  }
  if (parseResult.isErr()) return err(parseResult.error)
  const parsedBook = parseResult.value

  // Step 2: detect language
  const allText = parsedBook.sections.map((s) => s.text).join(' ')
  const detectedCode = await detectLanguage(allText)

  // Step 3: resolve language via dialog callback
  const language = await resolveLanguage(detectedCode)
  if (language === null) return err('Import cancelled')

  // Step 4: ensure books/ directory exists, then save file to vault.
  // mkdirInVault / writeFileBinaryToVault handle platform differences transparently:
  // on Android with a SAF content:// vault path they route to the native VaultFsPlugin.
  const vaultPath = useVaultStore.getState().vaultPath!
  const mkdirResult = await filesystemAdapter.mkdirInVault(vaultPath, 'books')
  if (mkdirResult.isErr()) return err(`Failed to create books directory: ${mkdirResult.error}`)
  const saveResult = await filesystemAdapter.writeFileBinaryToVault(vaultPath, `books/${fileName}`, new Uint8Array(data))
  if (saveResult.isErr()) return err(`Failed to save file: ${saveResult.error}`)

  // Step 5: build row objects in memory
  const bookId = crypto.randomUUID()
  const now = Math.floor(Date.now() / 1000)

  const bookEntity: BookEntity = {
    id: bookId,
    title: parsedBook.title,
    author: parsedBook.author,
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

  // Step 6: bulk insert — book → sections → tokens (atomic: all-or-nothing)
  const db = getDb()
  try {
    await db.transaction(async (tx) => {
      await tx.insert(schema.books).values([bookEntity])
      if (sectionRows.length > 0) {
        await tx.insert(schema.sections).values(sectionRows)
      }
      for (let i = 0; i < allTokenRows.length; i += CHUNK) {
        await tx.insert(schema.tokens).values(allTokenRows.slice(i, i + CHUNK))
      }
    })
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e))
  }

  // Step 7: update Zustand store (optimistic, no await)
  useVaultStore.getState().addBook(bookEntity)

  return ok(bookEntity)
}
