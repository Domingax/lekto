package app.lekto.testkit

import app.lekto.core.sync.RemoteIndexStore
import app.lekto.core.sync.SyncItem

/**
 * The in-memory [RemoteIndexStore] (ADR-0027): the engine's memory of the target
 * behind the seam, so an engine's re-read behaviour runs without a file. The real
 * [app.lekto.core.sync.JsonRemoteIndexStore] runs the same contract, so the two
 * cannot drift.
 *
 * A store starts as empty as a fresh install's, and can be handed [initial]
 * items to model a previous session's memory that survived a restart.
 */
class InMemoryRemoteIndexStore(initial: List<SyncItem> = emptyList()) : RemoteIndexStore {

    private val items = linkedMapOf<String, SyncItem>()

    init {
        initial.forEach { items[it.id] = it }
    }

    override fun all(): List<SyncItem> = items.values.toList()

    override fun replaceAll(items: List<SyncItem>) {
        this.items.clear()
        items.forEach { this.items[it.id] = it }
    }
}
