package app.lekto.core.llm

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.BufferedReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * The OpenAI-compatible [LlmClient] (issues #88, #89; ADR-0022): it posts to
 * `{baseUrl}/chat/completions` with the user's key — a minimal chat completion
 * for the connection test, a `stream`-flagged request for a phrase translation —
 * and maps the provider's HTTP answer to a [LlmConnectionResult] or a flow of
 * [LlmTranslationEvent]s. The same request shape every supported provider speaks,
 * so one transport carries both.
 *
 * It lives in `jvmSharedMain`, so desktop and Android share one transport, and
 * uses `HttpURLConnection` — no new network dependency (ADR-0022), as
 * `JvmPackDownloader` does. The connection test blocks; the application runs it
 * off the UI thread. The translation is a cold [Flow]: it opens on collection
 * and, because each server-sent line checks the collecting coroutine's
 * liveness, cancelling the collection closes the socket rather than draining it.
 * [connect] is the seam a test injects to point at a local server; [sessionId]
 * is stable for the client's life, which is what OpenCode Go asks for (see
 * [llmRequestHeaders]).
 *
 * On failure it reads at most [MAX_ERROR_BYTES] of the error body and hands only
 * its machine identifier to [connectionResult] — the provider's free text is
 * never rendered, so a body that echoes the request cannot echo a secret.
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

    override fun translate(request: LlmTranslationRequest): Flow<LlmTranslationEvent> = flow {
        when (val open = openStream(request)) {
            is StreamOpen.Failed -> emit(LlmTranslationEvent.Failed(open.message))
            is StreamOpen.Open -> emitAll(readEvents(open.connection))
        }
    }

    /** Opens the streaming request, mapping every failure to an inline [LlmTranslationEvent.Failed]. */
    @Suppress("TooGenericExceptionCaught", "ReturnCount") // A dead transport is an inline event.
    private fun openStream(request: LlmTranslationRequest): StreamOpen {
        val config = request.config
        if (!config.isComplete) return StreamOpen.Failed(LlmConnectionResult.INCOMPLETE)
        val connection = try {
            open(config, request.apiKey, STREAM_TIMEOUT_MILLIS)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return StreamOpen.Failed(LlmConnectionResult.UNREACHABLE)
        }
        return try {
            val body = StreamChatRequest(model = config.model, messages = request.messages, stream = true)
            connection.outputStream.use { output -> output.write(Json.encodeToString(body).encodeToByteArray()) }
            val status = connection.responseCode
            if (status in HTTP_OK until HTTP_REDIRECT) {
                StreamOpen.Open(connection)
            } else {
                val message = failureMessage(status, readBounded(connection.errorStream))
                connection.disconnect()
                StreamOpen.Failed(message)
            }
        } catch (cancellation: CancellationException) {
            connection.disconnect()
            throw cancellation
        } catch (_: Exception) {
            connection.disconnect()
            StreamOpen.Failed(LlmConnectionResult.UNREACHABLE)
        }
    }

    /** Streams the open [connection]'s events and always closes it, turning a dead transport into an inline event. */
    @Suppress("TooGenericExceptionCaught") // A dead transport is an inline event; cancellation must still propagate.
    private fun readEvents(connection: HttpURLConnection): Flow<LlmTranslationEvent> = flow {
        coroutineScope {
            // A watcher whose cancellation closes the socket: a `readLine()`
            // waiting on a quiet provider is unblocked the moment the collector
            // is cancelled, so closing the panel stops the stream at once
            // rather than at the read timeout.
            val closesOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }
            try {
                streamEvents(connection.inputStream) { event -> emit(event) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emit(LlmTranslationEvent.Failed(LlmConnectionResult.UNREACHABLE))
            } finally {
                closesOnCancel.cancel()
                connection.disconnect()
            }
        }
    }

    private fun exchange(config: LlmProviderConfig, apiKey: String): LlmConnectionResult {
        val connection = open(config, apiKey, TIMEOUT_MILLIS)
        try {
            val request = ChatRequest(config.model, listOf(LlmMessage("user", "ping")))
            connection.outputStream.use { output -> output.write(Json.encodeToString(request).encodeToByteArray()) }
            val status = connection.responseCode
            if (status in HTTP_OK until HTTP_REDIRECT) return LlmConnectionResult.Connected
            return connectionResult(status, errorIdentifier(readBounded(connection.errorStream)))
        } finally {
            connection.disconnect()
        }
    }

    /** Opens the chat-completions connection with the headers the active provider needs. */
    private fun open(config: LlmProviderConfig, apiKey: String, readTimeout: Int): HttpURLConnection =
        connect(chatCompletionsUrl(config)).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MILLIS
            this.readTimeout = readTimeout
            doOutput = true
            llmRequestHeaders(config, apiKey, sessionId).forEach { (name, value) -> setRequestProperty(name, value) }
        }

    /**
     * Reads the server-sent events until `[DONE]` or the stream ends, handing
     * each parsed [LlmTranslationEvent] to [onEvent]. Cancellation is checked
     * per line, so the reader closing the panel stops the request rather than
     * draining it; an error event ends the stream.
     */
    private suspend fun streamEvents(stream: InputStream, onEvent: suspend (LlmTranslationEvent) -> Unit) {
        val reader = stream.bufferedReader()
        try {
            while (true) {
                val event = nextEvent(reader) ?: return
                onEvent(event)
                if (event is LlmTranslationEvent.Failed) return
            }
        } finally {
            reader.close()
        }
    }

    /** The next translation event, or `null` at `[DONE]` and at the end of the stream. */
    @Suppress("ReturnCount") // `[DONE]` and the end of the stream are two different exits; both mean "no event".
    private suspend fun nextEvent(reader: BufferedReader): LlmTranslationEvent? {
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = reader.readLine() ?: return null
            if (!line.startsWith(DATA_PREFIX)) continue
            val payload = line.removePrefix(DATA_PREFIX).trim()
            if (payload == DONE) return null
            streamEvent(payload)?.let { event -> return event }
        }
    }

    /**
     * At most [MAX_ERROR_BYTES] of [stream], so a hostile or broken provider
     * cannot make the app buffer a whole body to classify one error. The bytes
     * are parsed for an identifier and then dropped; none of them is displayed.
     */
    private fun readBounded(stream: InputStream?): String {
        if (stream == null) return ""
        return stream.use { input ->
            val buffer = ByteArray(MAX_ERROR_BYTES)
            var read = 0
            while (read < buffer.size) {
                val count = input.read(buffer, read, buffer.size - read)
                if (count < 0) break
                read += count
            }
            buffer.decodeToString(0, read)
        }
    }

    private companion object {
        /** A connection test must fail fast; a provider that hangs is reported unreachable. */
        const val TIMEOUT_MILLIS = 15_000

        /** A translation streams for a while, so one read may wait longer between deltas than a test. */
        const val STREAM_TIMEOUT_MILLIS = 60_000

        /** Enough for any provider's error envelope, and a hard cap on what we buffer. */
        const val MAX_ERROR_BYTES = 8 * 1024

        const val HTTP_OK = 200
        const val HTTP_REDIRECT = 300
    }
}

/** The outcome of opening a translation stream: an open connection, or the inline failure to report. */
private sealed interface StreamOpen {
    data class Open(val connection: HttpURLConnection) : StreamOpen
    data class Failed(val message: String) : StreamOpen
}

/** The prefix of a server-sent event's payload line. */
internal const val DATA_PREFIX: String = "data:"

/** The server-sent event that ends a chat-completions stream. */
internal const val DONE: String = "[DONE]"

/**
 * Parses one server-sent event [payload] (issue #89) into a
 * [LlmTranslationEvent], or `null` when it carries no text (a keep-alive, a
 * role-only first chunk, a malformed frame). An `error` envelope becomes a
 * [LlmTranslationEvent.Failed] whose message is classified from the machine
 * identifier alone, so provider-authored text and any echoed secret never reach
 * the screen (ADR-0022).
 */
@Suppress("ReturnCount") // Each guard rules out a frame shape; a flat `when` would be less readable.
internal fun streamEvent(payload: String): LlmTranslationEvent? {
    val root = runCatching { Json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
    (root["error"] as? JsonObject)?.let { error ->
        val identifier = (error["code"] as? JsonPrimitive)?.contentOrNull
            ?: (error["type"] as? JsonPrimitive)?.contentOrNull
        return LlmTranslationEvent.Failed(failureMessage(HTTP_STREAM_ERROR, identifier))
    }
    val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
    val content = ((choice["delta"] as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull
    return content?.takeIf { text -> text.isNotEmpty() }?.let(LlmTranslationEvent::Delta)
}

/** The status a stream's inline error is classified against when its envelope carries no HTTP status. */
private const val HTTP_STREAM_ERROR = 400

/** The reader-facing message for a failed call, from its status and optional machine identifier. */
internal fun failureMessage(statusCode: Int, errorIdentifier: String?): String =
    (connectionResult(statusCode, errorIdentifier) as LlmConnectionResult.Failed).message

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
private data class ChatRequest(val model: String, val messages: List<LlmMessage>)

/** The streaming chat-completions request a translation sends; `stream` must be explicit, not a default. */
@Serializable
private data class StreamChatRequest(val model: String, val messages: List<LlmMessage>, val stream: Boolean)
