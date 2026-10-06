package app.lekto.core.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The outcome of a connection test against an **LLM provider** (issue #88): the
 * provider [Connected] and answered, or it [Failed] with an honest reason the
 * settings screen shows inline. A failure is a first-class outcome, not an
 * exception into the reading session (ADR-0022), and its [Failed.message] never
 * carries the **API key** or any text the provider controls.
 */
sealed interface LlmConnectionResult {

    /** The provider accepted the request, so the base URL, model and key work together. */
    data object Connected : LlmConnectionResult

    /** The provider could not be reached or refused the request; [message] is safe to show. */
    data class Failed(val message: String) : LlmConnectionResult

    companion object {
        /** Shown before a request is sent: no base URL or model has been chosen. */
        const val INCOMPLETE: String = "Choose a provider and a model before testing the connection."

        /** The request never reached the provider — offline, a wrong host, or a refused connection. */
        const val UNREACHABLE: String =
            "The provider could not be reached. Check your connection and the base URL."

        /** HTTP 401/403: the key is wrong, expired, or not accepted by this provider. */
        const val REJECTED_KEY: String = "The provider rejected the API key."

        /** HTTP 404: the compatibility path is not there; the base URL is probably wrong. */
        const val NOT_FOUND: String = "The provider has no endpoint there. Check the base URL."

        /** HTTP 429: the provider is asking the client to slow down. */
        const val RATE_LIMITED: String = "The provider is rate-limiting. Try again shortly."

        /** HTTP 400: the request was malformed, usually an unknown model name. */
        const val BAD_REQUEST: String = "The provider rejected the request. Check the model name."

        /** The provider named the model in its error: it does not offer that one. */
        const val UNKNOWN_MODEL: String = "The provider doesn't offer that model. Check the model name."

        /**
         * The provider wants the client to attach a session identifier it can route
         * on — OpenCode Go answers `MissingSessionID` without `x-opencode-session`.
         */
        const val MISSING_SESSION: String =
            "The provider needs the app to identify its session. Update the app or pick another provider."

        /** The provider's account cannot pay for the request — no credit or an exhausted plan. */
        const val NO_FUNDS: String = "The provider account can't pay for the request. Check your billing."

        /** HTTP 5xx or anything else: the provider is up but unhappy; retrying may help. */
        const val SERVER_ERROR: String = "The provider answered with an error. Try again shortly."
    }
}

/**
 * The provider adapter seam (issue #88; ADR-0022): it reaches the user's own
 * **LLM provider** directly with their **API key** and reports whether it
 * answers. The OpenAI-compatible implementation lives in `jvmSharedMain`; a
 * scripted fake lives in `testkit`, so the settings controller is driven without
 * a network.
 *
 * The call blocks — it opens a socket — so the application runs it off the UI
 * thread, exactly as it runs a download or a vault write. Every failure is an
 * [LlmConnectionResult.Failed], never a thrown exception, so a dead provider
 * cannot interrupt the reading session.
 */
fun interface LlmClient {

    /** Tests [config] with [apiKey], reporting whether the provider answers. */
    fun testConnection(config: LlmProviderConfig, apiKey: String): LlmConnectionResult
}

/**
 * The reader-facing outcome of a failed call (issue #88), from the HTTP
 * [statusCode] and an optional provider [errorIdentifier].
 *
 * The [errorIdentifier] is a short machine token the provider puts in its error
 * envelope — `MissingSessionID`, `invalid_api_key`, `model_not_found` — read by
 * [errorIdentifier]. It is used **only as a lookup key** into the fixed
 * [KNOWN_ERRORS] table and is **never rendered**: the provider's own free text is
 * untrusted (a Custom base URL is user-supplied and could echo the request), so
 * only our constants ever reach the screen. An unknown identifier falls back to
 * the status-only mapping in [connectionResultFor].
 */
internal fun connectionResult(statusCode: Int, errorIdentifier: String?): LlmConnectionResult {
    val known = errorIdentifier?.lowercase()?.let(KNOWN_ERRORS::get)
    return known ?: connectionResultFor(statusCode)
}

/**
 * Maps an HTTP [statusCode] from the chat-completions call to the reader-facing
 * outcome (issue #88), used when the body names no error we recognise. It is pure
 * and lives in `commonMain`, so the two clients map a provider's answer the same
 * way and the mapping is tested without a socket. The response body is never
 * rendered — only its machine identifier, via [connectionResult] — so a provider
 * that echoes the request cannot echo a secret into a message.
 */
fun connectionResultFor(statusCode: Int): LlmConnectionResult = when {
    statusCode in HTTP_OK until HTTP_REDIRECT -> LlmConnectionResult.Connected

    statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN ->
        LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY)

    statusCode == HTTP_NOT_FOUND -> LlmConnectionResult.Failed(LlmConnectionResult.NOT_FOUND)

    statusCode == HTTP_RATE_LIMITED -> LlmConnectionResult.Failed(LlmConnectionResult.RATE_LIMITED)

    statusCode in HTTP_BAD_REQUEST until HTTP_SERVER_ERROR ->
        LlmConnectionResult.Failed(LlmConnectionResult.BAD_REQUEST)

    else -> LlmConnectionResult.Failed(LlmConnectionResult.SERVER_ERROR)
}

/**
 * The machine error identifier in a provider's error body, or `null` when the
 * body is not the JSON envelope we expect (issue #88). It reads the shape every
 * supported provider sends — an `error` object, with a specific `code` before a
 * general `type` — and never returns the provider's `message`, which is free text
 * we do not trust. A malformed, non-JSON or empty body yields `null`.
 */
internal fun errorIdentifier(body: String): String? {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
    val error = root?.get("error") as? JsonObject
    return error?.get("code").textOrNull() ?: error?.get("type").textOrNull()
}

private fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

/**
 * The identifiers whose meaning is known well enough to name precisely. Keys are
 * lowercase; a value the provider sends that is not here falls back to the HTTP
 * status, so an unrecognised or hostile identifier can only ever produce one of
 * our own constants.
 */
private val KNOWN_ERRORS: Map<String, LlmConnectionResult> = mapOf(
    "autherror" to LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY),
    "invalid_api_key" to LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY),
    "authentication_error" to LlmConnectionResult.Failed(LlmConnectionResult.REJECTED_KEY),
    "modelerror" to LlmConnectionResult.Failed(LlmConnectionResult.UNKNOWN_MODEL),
    "model_not_found" to LlmConnectionResult.Failed(LlmConnectionResult.UNKNOWN_MODEL),
    "invalid_model" to LlmConnectionResult.Failed(LlmConnectionResult.UNKNOWN_MODEL),
    "missingsessionid" to LlmConnectionResult.Failed(LlmConnectionResult.MISSING_SESSION),
    "insufficient_quota" to LlmConnectionResult.Failed(LlmConnectionResult.NO_FUNDS),
    "insufficient_funds" to LlmConnectionResult.Failed(LlmConnectionResult.NO_FUNDS),
    "quota_exceeded" to LlmConnectionResult.Failed(LlmConnectionResult.NO_FUNDS),
    "ratelimiterror" to LlmConnectionResult.Failed(LlmConnectionResult.RATE_LIMITED),
    "rate_limit_exceeded" to LlmConnectionResult.Failed(LlmConnectionResult.RATE_LIMITED),
)

private const val HTTP_OK = 200
private const val HTTP_REDIRECT = 300
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_RATE_LIMITED = 429
private const val HTTP_SERVER_ERROR = 500
