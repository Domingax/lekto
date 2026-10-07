package app.lekto.core.llm

import kotlinx.serialization.Serializable

/**
 * One chat turn the adapter transports (issue #89; ADR-0022): a [role] —
 * `system` or `user` — and the [content] the model reads. The prompt is built
 * here in the domain, so a provider change is data and the translation
 * behaviour is testable without a socket.
 */
@Serializable
data class LlmMessage(val role: String, val content: String)

/**
 * One event in a streamed **LLM provider** translation (issue #89; ADR-0022):
 * a [Delta] of newly arrived text, or a [Failed] the panel reports inline. A
 * failure is an event, not a thrown exception, so a dead provider cannot
 * interrupt the reading session.
 */
sealed interface LlmTranslationEvent {

    /** A chunk of the translation as it arrives from the provider. */
    data class Delta(val text: String) : LlmTranslationEvent

    /** The provider or the transport failed; [message] is safe to show and never carries the **API key**. */
    data class Failed(val message: String) : LlmTranslationEvent
}

/**
 * The request the adapter transports (issue #89): the active provider [config],
 * the **API key** it authenticates with, and the [messages] the domain built.
 * The key is carried only as far as the socket and never enters the **Vault**, a
 * log or a **Secret store** message (ADR-0021, ADR-0022).
 */
data class LlmTranslationRequest(val config: LlmProviderConfig, val apiKey: String, val messages: List<LlmMessage>)

/**
 * The prompt for a phrase translation, together with [context] — the exact text
 * that leaves the device — so the panel can be transparent about it (issue #89).
 */
data class LlmTranslationPrompt(val messages: List<LlmMessage>, val context: String)

/**
 * The cap on the context sent alongside the selection (issue #89). The model
 * receives the selection plus its containing sentence, truncated to this many
 * characters, so a long paragraph cannot send the whole chapter.
 */
const val MAX_TRANSLATION_CONTEXT: Int = 600

/**
 * Builds the messages that ask the **LLM provider** to translate [selection] in
 * the context of [sentence], into [targetLanguage] (issue #89; ADR-0022). The
 * prompt lives in the domain — the adapter only transports it — and the exact
 * [LlmTranslationPrompt.context] it sends is returned so the panel can show the
 * reader what leaves the device.
 */
fun translationPrompt(selection: String, sentence: String?, targetLanguage: String?): LlmTranslationPrompt {
    val context = translationContext(selection, sentence)
    val instruction = buildString {
        append("Translate the selected phrase")
        targetLanguage?.takeIf { it.isNotBlank() }?.let { language -> append(" into the target language '$language'") }
        append(". Answer with the translation only, with no commentary.")
    }
    return LlmTranslationPrompt(
        messages = listOf(
            LlmMessage(ROLE_SYSTEM, instruction),
            LlmMessage(ROLE_USER, context),
        ),
        context = context,
    )
}

/**
 * The exact user text sent to the provider (issue #89): the selection and its
 * containing sentence, capped by [MAX_TRANSLATION_CONTEXT] so the panel can
 * disclose precisely what leaves the device. A blank or absent sentence falls
 * back to the selection alone.
 */
fun translationContext(selection: String, sentence: String?): String {
    val context = buildString {
        append(SELECTION_LABEL).append(' ').append(selection.trim())
        sentence?.trim()?.takeIf { it.isNotBlank() }?.let { line ->
            append('\n').append(SENTENCE_LABEL).append(' ').append(line)
        }
    }
    return cap(context)
}

/** Truncates [text] to [MAX_TRANSLATION_CONTEXT] without splitting a surrogate pair. */
private fun cap(text: String): String {
    if (text.length <= MAX_TRANSLATION_CONTEXT) return text
    val head = text.take(MAX_TRANSLATION_CONTEXT)
    return if (head.last().isHighSurrogate()) head.dropLast(1) else head
}

private const val ROLE_SYSTEM = "system"
private const val ROLE_USER = "user"
private const val SELECTION_LABEL = "Selected phrase:"
private const val SENTENCE_LABEL = "Containing sentence:"
