package app.lekto

import app.lekto.core.llm.JsonLlmSettingsStore
import app.lekto.core.llm.OpenAiCompatibleLlmClient
import app.lekto.core.vault.JvmVaultFileSystem
import java.io.File

/**
 * The desktop wiring of the LLM provider (issue #88; ADR-0022, ADR-0023): the
 * non-secret provider configuration is one JSON document under an app-private
 * root beside the vault, and the connection test reaches the provider directly
 * with `HttpURLConnection`. The API key is **not** here — it is stored through
 * the desktop [desktopSecretStore]'s **Secret store** instead.
 *
 * [root] is the test seam, as [desktopVaultStore]'s is; production never passes
 * one.
 */
fun desktopLlm(root: File = File(appDataDirectory("lekto"), "llm")): LlmServices =
    LlmServices(JsonLlmSettingsStore(JvmVaultFileSystem(root)), OpenAiCompatibleLlmClient())
