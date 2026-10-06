package app.lekto.core.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * The OpenAI-compatible [LlmClient] (issue #88; ADR-0022): it posts a minimal
 * chat completion to `{baseUrl}/chat/completions` with the user's key and maps
 * the provider's HTTP answer to a [LlmConnectionResult]. The same request shape
 * every supported provider speaks, so the connection test proves the exact path
 * a later phrase translation will take.
 *
 * It lives in `jvmSharedMain`, so desktop and Android share one transport, and
 * uses `HttpURLConnection` — no new network dependency (ADR-0022), as
 * `JvmPackDownloader` does. It is blocking; the application runs it off the UI
 * thread. [connect] is the seam a test injects to point at a local server;
 * [sessionId] is stable for the client's life, which is what OpenCode Go asks for
 * (see [llmRequestHeaders]).
 */
class OpenAiCompatibleLlmClient(
    private val connect: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
    private val sessionId: String = UUID.randomUUID().toString(),
) : LlmClient {

    @Suppress("TooGenericExceptionCaught") // Any transport failure is an honest inline message, never a crash.
    override fun testConnection(config: LlmProviderConfig, apiKey: String): LlmConnectionResult {
        if (!config.isComplete) return LlmConnectionResult.Failed(LlmConnectionResult.INCOMPLETE)
        return try {
            exchange(config, apiKey)
        } catch (_: Exception) {
            LlmConnectionResult.Failed(LlmConnectionResult.UNREACHABLE)
        }
    }

    private fun exchange(config: LlmProviderConfig, apiKey: String): LlmConnectionResult {
        val connection = connect(chatCompletionsUrl(config)).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            doOutput = true
            llmRequestHeaders(config, apiKey, sessionId).forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val request = ChatRequest(config.model, listOf(ChatMessage("user", "ping")))
            connection.outputStream.use { output -> output.write(Json.encodeToString(request).encodeToByteArray()) }
            // The body is never read: mapping the status alone keeps a provider's
            // echoed request — and so a key — out of every message.
            return connectionResultFor(connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /** A connection test must fail fast; a provider that hangs is reported unreachable. */
        const val TIMEOUT_MILLIS = 15_000
    }
}

/**
 * The headers the transport sends for [config] (issue #88). A keyless provider —
 * a local Ollama — sends no `Authorization`; every other provider authenticates
 * with its bearer key.
 *
 * OpenCode Go additionally requires the client to identify itself: a named
 * `User-Agent` (not a generic HTTP-library name) and a stable `x-opencode-session`
 * for routing and prompt caching. Without the session header it rejects the
 * request outright (`MissingSessionID`), so it is sent for both OpenCode
 * gateways; a provider that does not ask for it never sees it.
 *
 * Extracted so the header policy is a pure value a test can pin, and so no header
 * ever carries anything but the configuration and the session.
 */
internal fun llmRequestHeaders(
    config: LlmProviderConfig,
    apiKey: String,
    sessionId: String,
): List<Pair<String, String>> = buildList {
    add("Content-Type" to "application/json")
    add("User-Agent" to USER_AGENT)
    if (apiKey.isNotBlank()) add("Authorization" to "Bearer $apiKey")
    if (config.provider == LlmProvider.OPENCODE_ZEN || config.provider == LlmProvider.OPENCODE_GO) {
        add("x-opencode-session" to sessionId)
    }
}

/** How Lekto identifies itself to a provider; a named client, not the HTTP library. */
internal const val USER_AGENT: String = "lekto/0.1.0"

/** The `{baseUrl}/chat/completions` endpoint, with the base URL's trailing slash tolerated. */
internal fun chatCompletionsUrl(config: LlmProviderConfig): String = "${config.baseUrl.trimEnd('/')}/chat/completions"

/** The minimal chat-completions request the connection test sends: one short user turn. */
@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatMessage>)

@Serializable
private data class ChatMessage(val role: String, val content: String)
