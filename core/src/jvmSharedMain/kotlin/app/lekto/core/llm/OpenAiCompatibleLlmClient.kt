package app.lekto.core.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

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
 * thread. [connect] is the seam a test injects to point at a local server.
 */
class OpenAiCompatibleLlmClient(
    private val connect: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
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
            // A keyless provider (a local Ollama) gets no Authorization header;
            // every other provider authenticates with its bearer key.
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
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

/** The `{baseUrl}/chat/completions` endpoint, with the base URL's trailing slash tolerated. */
internal fun chatCompletionsUrl(config: LlmProviderConfig): String = "${config.baseUrl.trimEnd('/')}/chat/completions"

/** The minimal chat-completions request the connection test sends: one short user turn. */
@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatMessage>)

@Serializable
private data class ChatMessage(val role: String, val content: String)
