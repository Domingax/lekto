package app.lekto.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.guide.GuideProgress
import app.lekto.core.guide.GuideProgressStore

/**
 * The first-launch guide's live completion fact (issue #277): whether the reader
 * has finished it, as Compose state, read and written through the device-local
 * [GuideProgressStore]. The store is the truth; this holder only keeps the fact
 * observable so the gate above the shell recomposes when the guide finishes.
 *
 * Nothing is written before completion, so a mid-guide relaunch reads the same
 * not-completed document and opens on the first screen.
 *
 * It is device-local app configuration (ADR-0023), never a **Vault** record, so
 * it is never exported and never synced.
 */
class GuideProgressController(private val store: GuideProgressStore) {

    /** Whether the reader has seen the guide; `false` on a fresh install. */
    var seen by mutableStateOf(store.load().seen)
        private set

    /** Records that the reader reached the end or skipped: the guide opens no more. */
    fun markSeen() {
        if (seen) return
        seen = true
        store.save(GuideProgress.SEEN)
    }
}
