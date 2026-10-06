package app.lekto.core.llm

/**
 * The outcome of a connection test against an **LLM provider** (issue #88): the
 * provider [Connected] and answered, or it [Failed] with an honest reason the
 * settings screen shows inline. A failure is a first-class outcome, not an
 * exception into the reading session (ADR-0022), and its [Failed.message] never
 * carries the **API key**.
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
 * Maps an HTTP [statusCode] from the chat-completions call to the reader-facing
 * outcome (issue #88). It is pure and lives in `commonMain`, so the two clients
 * map a provider's answer the same way and the mapping is tested without a
 * socket. The response body is deliberately never read: it can quote the request,
 * so mapping the status alone keeps a secret out of any message.
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

private const val HTTP_OK = 200
private const val HTTP_REDIRECT = 300
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_RATE_LIMITED = 429
private const val HTTP_SERVER_ERROR = 500
