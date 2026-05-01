import { ok, err } from 'neverthrow'
import type { AsyncResult } from '@/shared/lib'
import { filesystemAdapter } from '@/shared/platform'
import { useVaultStore } from '@/shared/stores'
import { getDb, schema } from '@/shared/db'
import { eq } from 'drizzle-orm'

export async function deleteBook(bookId: string): AsyncResult<void> {
  const book = useVaultStore.getState().books.find((b) => b.id === bookId)
  if (!book) return err('Book not found')

  const vaultPath = useVaultStore.getState().vaultPath
  if (!vaultPath) return err('Vault not mounted')

  const db = getDb()
  try {
    await db.delete(schema.books).where(eq(schema.books.id, bookId))
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e))
  }

  const removeResult = await filesystemAdapter.deleteFileInVault(vaultPath, `books/${book.fileName}`)
  if (removeResult.isErr() && !removeResult.error.toLowerCase().includes('not found')) {
    console.warn(`[delete-book] file removal failed: ${removeResult.error}`)
  }

  useVaultStore.getState().removeBook(bookId)

  return ok(undefined)
}
