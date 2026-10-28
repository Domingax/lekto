package app.lekto.core.sync

import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last sync run's outcome, in plain language (issue #121): whether it
 * succeeded and the one-line [message] the section shows at rest.
 *
 * It is deliberately **not** the **Vault** (ADR-0005) and never an export: it is
 * device-local bookkeeping, like the enable flag beside it (ADR-0023). Keeping it
 * across a restart is the point — the user sees the last outcome when the app
 * opens rather than an empty section.
 */
@Serializable
data class SyncOutcome(
    val succeeded: Boolean,
    val message: String,
    /** When the run finished, epoch milliseconds; `null` for a document written before the time was kept. */
    val atMillis: Long? = null,
)

/**
 * The app-private home of the last [SyncOutcome] (issue #121), the same
 * device-local store pattern as [SyncSettingsStore] (ADR-0023), the tombstone
 * store (ADR-0015) and the remote index (ADR-0027). It is deliberately **not**
 * the **Vault** (ADR-0005), so the last outcome never appears in an export.
 *
 * A store that cannot be read reports `null` rather than throwing: an absent or
 * unreadable document simply means "no outcome yet", and nothing secret is
 * involved.
 */
interface SyncOutcomeStore {

    /** The last recorded outcome, or `null` when none was recorded. */
    fun load(): SyncOutcome?

    /** Replaces the last outcome with [outcome]. */
    fun save(outcome: SyncOutcome)

    /** Forgets the last outcome, so a disconnected section shows nothing. */
    fun clear()
}

/**
 * The platform [SyncOutcomeStore] (issue #121): the outcome as one JSON document
 * over any [VaultFileSystem], the same write-temp-rename byte seam as the vault
 * and the sync settings, rooted with them rather than inside the vault (ADR-0003,
 * ADR-0010). It is shared by both clients because `java.nio.file` backs it
 * identically on desktop and Android; only the root differs.
 */
class JsonSyncOutcomeStore(private val files: VaultFileSystem, private val json: Json = Json) : SyncOutcomeStore {

    override fun load(): SyncOutcome? {
        val bytes = files.read(PATH) ?: return null
        return runCatching { json.decodeFromString<SyncOutcome>(bytes.decodeToString()) }.getOrNull()
    }

    override fun save(outcome: SyncOutcome) {
        files.writeAtomically(PATH, json.encodeToString(outcome).encodeToByteArray())
    }

    override fun clear() {
        files.delete(PATH)
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val PATH: String = "sync-outcome.json"
    }
}
