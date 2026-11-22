package app.lekto.core.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import kotlinx.serialization.Serializable

/**
 * A word or phrase the user saved (CONTEXT.md, "Vocabulary entry"): its
 * [surface] spelling, the [translation] the lookup offered, the [contextSentence]
 * it was found in, its [mastery] level, and the [bookIds] of the books it was
 * saved from.
 *
 * Identity is the [key] — the token's [WordKey], which is the lemma when the
 * dictionary pack knows one and the normalised surface form otherwise (ADR-0006).
 * Two spellings that share a key therefore share one entry, so saving an
 * inflection updates its lemma's entry rather than fragmenting it.
 *
 * The [bookIds] are the book history (issue #280; ADR-0050): the books whose
 * reading sessions saved this word, each named **once**, newest last. An entry
 * written before the history existed reads with an empty list, and deleting a
 * book never removes an id: only deleting the word does.
 */
@Serializable
data class VocabularyEntry(
    val key: WordKey,
    val surface: String,
    val translation: String? = null,
    val contextSentence: String? = null,
    val mastery: MasteryLevel = MasteryLevel.UNKNOWN,
    val bookIds: List<String> = emptyList(),
)
