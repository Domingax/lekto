package app.lekto.core.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import kotlinx.serialization.Serializable

/**
 * A word or phrase the user saved (CONTEXT.md, "Vocabulary entry"): its
 * [surface] spelling, the [translation] the lookup offered, the [contextSentence]
 * it was found in, and its [mastery] level.
 *
 * Identity is the [key] — the token's [WordKey], which is the lemma when the
 * dictionary pack knows one and the normalised surface form otherwise (ADR-0006).
 * Two spellings that share a key therefore share one entry, so saving an
 * inflection updates its lemma's entry rather than fragmenting it.
 */
@Serializable
data class VocabularyEntry(
    val key: WordKey,
    val surface: String,
    val translation: String? = null,
    val contextSentence: String? = null,
    val mastery: MasteryLevel = MasteryLevel.UNKNOWN,
)
