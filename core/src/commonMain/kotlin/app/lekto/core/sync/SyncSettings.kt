package app.lekto.core.sync

import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The app-private, non-secret configuration of a WebDAV sync target (issue #28):
 * the **base URL** and username the driver connects to, and whether the user has
 * explicitly [enabled] sync.
 *
 * It is deliberately **not** the **Vault** (ADR-0005) — like the LLM provider's
 * configuration (ADR-0023), it can never appear in an export or move to another
 * device — and the **application password** is deliberately absent: a secret
 * lives in the **Secret store** (ADR-0021), never in a value that is logged or
 * serialised. Sync starts [disabled][enabled]; enabling it is always a
 * deliberate act, never a silent one (issue #28).
 *
 * Rejected: storing the password beside the URL (it would leak into a backup or
 * a log), and folding the endpoint into the **Vault** (ADR-0010 keeps the vault
 * app-private and a target is a device's own choice).
 */
@Serializable
data class SyncSettings(val serverUrl: String = "", val username: String = "", val enabled: Boolean = false) {

    /** Whether a server address and a username are set, so a driver can be built. */
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank()

    companion object {
        /** What an app that has never configured sync holds: nothing, and off. */
        val DEFAULT: SyncSettings = SyncSettings()
    }
}

/**
 * The app-private home of the [SyncSettings] (issue #28; the seam
 * [app.lekto.core.llm.LlmSettingsStore] established for the LLM provider). It is
 * deliberately **not** the **Vault** (ADR-0005), so the endpoint and the enable
 * flag never appear in an export, and the password never lives here at all.
 *
 * A store that cannot be read reports [SyncSettings.DEFAULT] rather than
 * throwing: the user re-enters the endpoint, and nothing secret is involved
 * either way.
 */
interface SyncSettingsStore {

    /** The stored configuration, or [SyncSettings.DEFAULT] when none is stored. */
    fun load(): SyncSettings

    /** Replaces the stored configuration. */
    fun save(settings: SyncSettings)
}

/**
 * The platform [SyncSettingsStore] (issue #28): the configuration as one JSON
 * document over any [VaultFileSystem], the same write-temp-rename byte seam as
 * the vault and the derived assets, rooted elsewhere (ADR-0003, ADR-0010). It is
 * shared by both clients because `java.io.File` backs it identically on desktop
 * and Android; only the root differs. Both clients wire a driver now (issue
 * #116; ADR-0026), so the endpoint is app-private on each.
 */
class JsonSyncSettingsStore(private val files: VaultFileSystem, private val json: Json = Json) : SyncSettingsStore {

    override fun load(): SyncSettings {
        val bytes = files.read(PATH) ?: return SyncSettings.DEFAULT
        return runCatching { json.decodeFromString<SyncSettings>(bytes.decodeToString()) }
            .getOrDefault(SyncSettings.DEFAULT)
    }

    override fun save(settings: SyncSettings) {
        files.writeAtomically(PATH, json.encodeToString(settings).encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val PATH: String = "sync-settings.json"
    }
}
