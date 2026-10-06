package app.lekto

import android.content.Context
import app.lekto.core.llm.JsonLlmSettingsStore
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.OpenAiCompatibleLlmClient
import app.lekto.core.vault.JvmVaultFileSystem
import java.io.File

/**
 * The Android wiring of the LLM provider (issue #88; ADR-0022, ADR-0023): the
 * non-secret provider configuration is one JSON document under `Context.filesDir`
 * — app-private, never the vault — and the connection test reaches the provider
 * directly over `HttpURLConnection`. The API key is stored through
 * `AndroidSecretStore`'s **Secret store**, not here. Ollama is excluded: it is a
 * local server reachable only from a desktop (ADR-0022).
 */
fun androidLlm(context: Context): LlmServices = LlmServices(
    settings = JsonLlmSettingsStore(JvmVaultFileSystem(File(context.filesDir, "llm"))),
    client = OpenAiCompatibleLlmClient(),
    providers = LlmProvider.entries - LlmProvider.OLLAMA,
)
