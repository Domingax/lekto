package app.lekto.core.sync

/**
 * The engine's device-local memory of the target (ADR-0027): the last item it
 * saw per id, so a sync re-reads only what changed instead of the whole vault
 * every run.
 *
 * It is rooted with the tombstone store, never the **Vault** (ADR-0005) and
 * never an export: the memory caches record bodies the target already holds, so
 * it is reproducible and not user-authored data. Losing it — an absent or
 * unreadable document — costs one full read, never a record.
 */
interface RemoteIndexStore {

    /** Every remembered item, one per id. */
    fun all(): List<SyncItem>

    /** Replaces the memory with [items], one per id. */
    fun replaceAll(items: List<SyncItem>)
}
