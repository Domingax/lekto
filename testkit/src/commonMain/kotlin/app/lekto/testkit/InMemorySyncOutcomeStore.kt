package app.lekto.testkit

import app.lekto.core.sync.SyncOutcome
import app.lekto.core.sync.SyncOutcomeStore

/**
 * An in-memory [SyncOutcomeStore] for tests (issue #121): the last sync outcome
 * behind the seam, so a consumer's logic runs without a file or a platform root.
 * A store starts empty — no outcome yet — exactly as a fresh install does, and
 * can be handed [initial] to model an outcome that survived a restart.
 */
class InMemorySyncOutcomeStore(initial: SyncOutcome? = null) : SyncOutcomeStore {

    private var stored: SyncOutcome? = initial

    override fun load(): SyncOutcome? = stored

    override fun save(outcome: SyncOutcome) {
        stored = outcome
    }

    override fun clear() {
        stored = null
    }
}
