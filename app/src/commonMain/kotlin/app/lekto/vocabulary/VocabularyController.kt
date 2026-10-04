package app.lekto.vocabulary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.Vocabulary
import app.lekto.core.vocabulary.VocabularyEntry

/**
 * The reader's live view of the user's saved words (issue #22).
 *
 * It loads the vault once, keeps the entries in Compose snapshot state, and
 * exposes two things the reader reads during composition: [mastery], so a word
 * is coloured by its saved level, and [revision], which is bumped on every save.
 * The reader's page word layer is memoised, so a plain state read would not force
 * it to rebuild; the revision is the explicit key that recolours the word the
 * instant it is saved — the only confirmation the product gives.
 *
 * The save itself is blocking (it is a vault write), so the caller runs it off
 * the UI thread; once it returns, the in-memory map is updated and the reader
 * recomposes.
 */
class VocabularyController(private val vocabulary: Vocabulary) {

    private val saved = mutableStateMapOf<WordKey, VocabularyEntry>()

    /** Bumped on every save, so a reader memoised on it recolours at once. */
    var revision by mutableStateOf(0)
        private set

    init {
        vocabulary.all().forEach { entry -> saved[entry.key] = entry }
    }

    /** The level to paint each word, defaulting an unsaved word to [MasteryLevel.UNKNOWN]. */
    val mastery: MasteryLookup = MasteryLookup { key -> saved[key]?.mastery ?: MasteryLevel.UNKNOWN }

    /** The saved entry for [key], or `null` when the word is unsaved. */
    fun entryFor(key: WordKey): VocabularyEntry? = saved[key]

    /** Saves [entry] to the vault and folds it into the reader's state. */
    fun save(entry: VocabularyEntry) {
        vocabulary.save(entry)
        saved[entry.key] = entry
        revision++
    }
}
