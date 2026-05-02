import { ok, err } from 'neverthrow'
import type { Result } from 'neverthrow'
import { getDb } from './index'
import { languages } from './schema'
import { SEED_LANGUAGES } from '@/shared/lib'

export async function seedLanguages(): Promise<Result<void, string>> {
  try {
    const db = getDb()
    await db.insert(languages).values(SEED_LANGUAGES).onConflictDoNothing()
    return ok(undefined)
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e))
  }
}
