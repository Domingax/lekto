package app.lekto.core.llm

import app.lekto.core.vault.VaultFileSystem
import kotlinx.serialization.json.Json

/**
 * The app-private home of the non-secret **LLM provider** configuration (issue
 * #88; ADR-0022): the chosen preset, model and custom base URL. It is
 * deliberately **not** the **Vault** (ADR-0005), so the configuration can never
 * appear in an export, and the **API key** never lives here — only in the
 * **Secret store** (ADR-0021). A store that cannot be read reports
 * [LlmProviderConfig.DEFAULT] rather than throwing.
 */
interface LlmSettingsStore {

    /** The stored configuration, or [LlmProviderConfig.DEFAULT] when none is stored. */
    fun load(): LlmProviderConfig

    /** Replaces the stored configuration. */
    fun save(config: LlmProviderConfig)
}

/**
 * The platform [LlmSettingsStore] (issue #88): the configuration as one JSON
 * document in an app-private byte store, the same write-temp-rename store the
 * vault and the derived assets sit on, rooted elsewhere (ADR-0023). It is
 * shared by both clients because `java.io.File` backs it identically on desktop
 * and Android; only the root differs.
 *
 * A document this version cannot read — corrupt, or written by a future one — is
 * treated as "not configured" rather than a crash: the user re-enters their
 * choice, and no secret is involved either way.
 */
class JsonLlmSettingsStore(private val files: VaultFileSystem, private val json: Json = Json) : LlmSettingsStore {

    override fun load(): LlmProviderConfig {
        val bytes = files.read(PATH) ?: return LlmProviderConfig.DEFAULT
        return runCatching { json.decodeFromString<LlmProviderConfig>(bytes.decodeToString()) }
            .getOrDefault(LlmProviderConfig.DEFAULT)
    }

    override fun save(config: LlmProviderConfig) {
        files.writeAtomically(PATH, json.encodeToString(config).encodeToByteArray())
    }

    companion object {
        /** The single document the store owns, relative to its root. */
        const val PATH: String = "llm-provider.json"
    }
}
