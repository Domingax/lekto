package app.lekto.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.reading.ReadingPreferencesStore

/**
 * The reader's live **Reading preference** (ADR-0035): the face, the size and the
 * margin the current book's text is typeset in, as Compose state, read and written
 * through the device-local [ReadingPreferencesStore], so the **Aa** panel shows a
 * choice at once and the reader repaginates as it moves.
 *
 * The store is the truth; this holder only keeps the current value observable and
 * writes each one back. The transitions — a step, a clamp — are the value's own
 * ([ReadingPreferences.smaller], [ReadingPreferences.larger]), so the panel and
 * the reader cannot drift on what "bigger" means. It is device-local
 * configuration, so it is never synced and never reaches the vault.
 */
class ReadingPreferencesController(private val store: ReadingPreferencesStore) {

    /** The preference in effect; [ReadingPreferences.DEFAULT] on a fresh install. */
    var preferences by mutableStateOf(store.load())
        private set

    /** Applies [next] whole — the value the **Aa** panel handed back — and stores it. */
    fun update(next: ReadingPreferences) {
        preferences = next
        store.save(next)
    }
}
