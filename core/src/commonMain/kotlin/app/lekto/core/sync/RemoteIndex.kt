package app.lekto.core.sync

/**
 * The engine's device-local view of what the target held the last time it
 * looked: one [SyncItem] per id.
 *
 * A cursor reports only what *changed* since the token was issued, so the engine
 * cannot merge against that partial list — an unchanged remote record would look
 * absent and be re-uploaded every run (ADR-0009; research §8.4). Keeping the
 * index is what turns a delta into a full picture: a listing replaces it whole,
 * a change set updates only the ids it names. The engine persists it through a
 * [RemoteIndexStore] and passes its [knownRevisions] to [SyncTarget.list], so a
 * sync re-reads only what moved (ADR-0027).
 */
internal class RemoteIndex {

    private val items = linkedMapOf<String, SyncItem>()

    /** Every item the target held when this index was last updated. */
    fun all(): List<SyncItem> = items.values.toList()

    /** The item the target held for [id], or `null` when it held none. */
    fun get(id: String): SyncItem? = items[id]

    /**
     * The revision this index last saw for each id, which the engine passes to
     * [SyncTarget.list] so a driver reads only what moved (ADR-0027).
     */
    fun knownRevisions(): Map<String, Revision> = items.mapValues { (_, item) -> item.revision }

    /**
     * Remembers that the target holds nothing for [id] any more.
     */
    fun remove(id: String) {
        items.remove(id)
    }

    /** Remembers [item] as what the target now holds for its id. */
    fun upsert(item: SyncItem) {
        items[item.id] = item
    }

    /** Replaces the whole view with a full [listing]. */
    fun replaceAll(listing: List<SyncItem>) {
        items.clear()
        listing.forEach(::upsert)
    }

    /**
     * Replaces the whole view with a [listing] (ADR-0027): a changed item brings
     * its own body, and an unchanged id keeps the body the index already held, so
     * an item whose revision did not move is never read again.
     */
    fun replaceAll(listing: SyncListing) {
        val next = linkedMapOf<String, SyncItem>()
        listing.items.forEach { next[it.id] = it }
        listing.unchanged.forEach { id -> items[id]?.let { next[id] = it } }
        items.clear()
        items.putAll(next)
    }

    /** Applies the items a change set named, leaving the rest of the view intact. */
    fun apply(changes: List<SyncItem>) {
        changes.forEach(::upsert)
    }
}
