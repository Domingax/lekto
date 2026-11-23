package app.lekto.vocabulary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.LocalChanges
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.Vocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.core.vocabulary.VocabularyEntryTime
import kotlin.time.Instant

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
 * recomposes. A delete (issue #23) is a vault write too, and it bumps the same
 * revision, so the reader drops the word's colour. Each landed save or delete
 * notifies [changes], so sync pushes the change (issue #121; ADR-0028).
 */
class VocabularyController(private val vocabulary: Vocabulary, private val changes: LocalChanges) {

    private val saved = mutableStateMapOf<WordKey, VocabularyEntry>()

    /** When each saved word's record last changed: the list's recency (ADR-0050). */
    private val changedAt = mutableStateMapOf<WordKey, Instant>()

    /** Bumped on every save or delete, so a reader memoised on it recolours at once. */
    var revision by mutableStateOf(0)
        private set

    init {
        adopt(vocabulary.allWithTimes())
    }

    /** The level to paint each word, defaulting an unsaved word to [MasteryLevel.UNKNOWN]. */
    val mastery: MasteryLookup = MasteryLookup { key -> saved[key]?.mastery ?: MasteryLevel.UNKNOWN }

    /** The saved entry for [key], or `null` when the word is unsaved. */
    fun entryFor(key: WordKey): VocabularyEntry? = saved[key]

    /**
     * Every saved entry, most recently modified first, so the list opens on what
     * the reader last touched. The record's update time orders it, with the word
     * itself breaking a tie; the screen freezes the order it opens on (issue #280).
     */
    fun all(): List<VocabularyEntry> = saved.values.sortedWith(entryOrder)

    /**
     * Saves [entry] to the vault and folds it into the reader's state, returning
     * whether the write landed (issue #279): the vault write is blocking and can
     * fail, and the reader's footer states the failure rather than claiming a save
     * that never happened. A refused write leaves the state — and the revision —
     * untouched, so the word stays unsaved and nothing recolours.
     *
     * [bookId] is the book the reader saved the word from (issue #280): it is
     * added to the entry's history once, keeping every book the record already
     * names. A save that names no book — a level change from the list — leaves the
     * history as it was.
     */
    fun save(entry: VocabularyEntry, bookId: String? = null): Boolean {
        val merged = entry.remembering(bookId)
        val written = runCatching { vocabulary.save(merged) }.getOrNull() ?: return false
        changedAt[merged.key] = written
        saved[merged.key] = merged
        revision++
        changes.contentChanged()
        return true
    }

    /**
     * Re-reads the vault and folds it into the reader's state, bumping [revision]
     * so a word the sync moved — added, removed or re-levelled on another device
     * — is shown without relaunching the app (issue #132). Called after a sync run;
     * the vault read is blocking, so the caller runs it off the UI thread.
     */
    fun refresh() {
        saved.clear()
        changedAt.clear()
        adopt(vocabulary.allWithTimes())
        revision++
    }

    /**
     * Deletes [key]'s entry from the vault and the reader's state, bumping
     * [revision] so the reader drops the word's colour at once. An absent key is
     * not an error.
     */
    fun delete(key: WordKey) {
        vocabulary.delete(key)
        saved.remove(key)
        changedAt.remove(key)
        revision++
        changes.contentChanged()
    }

    /** Folds a timed read of the vault into the live state. */
    private fun adopt(entries: List<VocabularyEntryTime>) {
        entries.forEach { saved ->
            this.saved[saved.entry.key] = saved.entry
            changedAt[saved.entry.key] = saved.updatedAt
        }
    }

    /**
     * [entry] with [bookId] added to its history, after every book the stored
     * entry already names. The same book twice is one id, so saving the word
     * again while reading the same book changes nothing (ADR-0050).
     */
    private fun VocabularyEntry.remembering(bookId: String?): VocabularyEntry {
        if (bookId == null) return this
        val history = saved[key]?.bookIds.orEmpty() + bookIds + bookId
        return copy(bookIds = history.distinct())
    }

    /** Most recently modified first, then by surface and identity so a tie never lands arbitrarily. */
    private val entryOrder = compareByDescending<VocabularyEntry> { entry ->
        changedAt[entry.key] ?: Instant.DISTANT_PAST
    }
        .thenBy { entry -> entry.surface.lowercase() }
        .thenBy { entry -> entry.key.language.orEmpty() }
        .thenBy { entry -> entry.key.key }
}
