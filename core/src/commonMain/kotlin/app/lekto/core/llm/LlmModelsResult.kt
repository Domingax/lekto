package app.lekto.core.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The outcome of listing a provider's models from the OpenAI-compatible
 * `GET {baseUrl}/models` (issue #93): the provider [Loaded] the identifiers it
 * offers, or it [Failed] with an honest inline reason. A list is device-local UI
 * state — it is never stored, so it enters neither the vault nor a log — and a
 * failure is a first-class outcome, never a thrown exception, so an unreachable
 * provider cannot interrupt the reader (ADR-0022).
 */
sealed interface LlmModelsResult {

    /** The provider listed the models it offers, in the order it sent them. */
    data class Loaded(val models: List<String>) : LlmModelsResult

    /** The list could not be loaded; [message] is safe to show and never carries provider text. */
    data class Failed(val message: String) : LlmModelsResult

    companion object {
        /** Shown before a request is sent: no base URL has been chosen. */
        const val INCOMPLETE: String = "Choose a provider before loading its models."

        /** The provider authenticates its model list and no key is set. */
        const val MISSING_KEY: String = "Enter your API key to load this provider's models."

        /** HTTP 404 or an unparseable body: the provider does not implement `/models`. */
        const val UNSUPPORTED: String = "This provider doesn't list its models. Type the model name instead."

        /** The provider answered `/models` but offered nothing. */
        const val EMPTY: String = "This provider listed no models. Type the model name instead."

        /** The request never reached the provider — offline, a wrong host, or a refused connection. */
        const val UNREACHABLE: String = LlmConnectionResult.UNREACHABLE
    }
}

/**
 * The model identifiers in a `GET /models` body (issue #93), or `null` when the
 * body is not the OpenAI-compatible envelope every supported provider sends — a
 * non-JSON body, a missing `data`, or a `data` that is not an array. The caller
 * treats `null` as "this provider does not implement `/models`" and leaves manual
 * entry in place, so an unknown shape degrades rather than blocks.
 *
 * The result is bounded twice, so a hostile or broken provider cannot exhaust the
 * picker: at most [MAX_MODELS] identifiers are kept, and an identifier longer than
 * [MAX_MODEL_ID_LENGTH] is dropped as junk. Blank and duplicate identifiers — and
 * entries whose `id` is not a string — are dropped too.
 */
@Suppress("ReturnCount") // Two guards rule out a shape; a flat `when` would be less readable.
internal fun modelIds(body: String): List<String>? {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    val data = root["data"] as? JsonArray ?: return null
    return data.asSequence()
        .mapNotNull { entry ->
            ((entry as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
        }
        .map(String::trim)
        .filter { id -> id.isNotEmpty() && id.length <= MAX_MODEL_ID_LENGTH }
        .distinct()
        .take(MAX_MODELS)
        .toList()
}

/** The most model identifiers the picker keeps, so a huge catalogue cannot exhaust it. */
internal const val MAX_MODELS: Int = 500

/** The longest model identifier accepted; a longer one is dropped as junk. */
internal const val MAX_MODEL_ID_LENGTH: Int = 200
