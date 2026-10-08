package app.lekto.testkit

import app.lekto.core.sync.SyncSettings
import app.lekto.core.sync.SyncSettingsStore

/**
 * An in-memory [SyncSettingsStore] for tests (issue #28): the app-private
 * configuration behind the seam, so a consumer's logic runs without a file or a
 * platform root. A store starts unconfigured and disabled —
 * [SyncSettings.DEFAULT] — exactly as a fresh install does.
 */
class InMemorySyncSettingsStore(initial: SyncSettings = SyncSettings.DEFAULT) : SyncSettingsStore {

    private var stored: SyncSettings = initial

    override fun load(): SyncSettings = stored

    override fun save(settings: SyncSettings) {
        stored = settings
    }
}
