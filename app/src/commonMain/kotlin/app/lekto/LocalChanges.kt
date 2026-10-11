package app.lekto

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The signal that the user changed something in the local **Vault**, so sync can
 * push a run rather than wait for its timer (issue #121; ADR-0028).
 *
 * The composition root owns one instance and hands it to the writers — the
 * library's import, the vocabulary's save and delete, and the reading-progress
 * writer — and to the sync controller, which subscribes. It is deliberately
 * **not** emitted by the vault itself: the sync engine writes to the vault during
 * a run, so a vault-level signal would make every sync trigger the next one.
 *
 * A content change (a book imported, a word saved or deleted) is discrete, so
 * [content] is consumed after a short settle; a reading-progress change happens
 * on every page turn, so [progress] is consumed after a longer pause. They are
 * two flows because the two tempos differ and the sync controller applies a
 * different delay to each.
 */
class LocalChanges {

    private val contentEvents = MutableSharedFlow<Unit>(extraBufferCapacity = CONTENT_BUFFER)
    private val progressEvents = MutableSharedFlow<Unit>(extraBufferCapacity = PROGRESS_BUFFER)

    /** Emits after a book was imported, or a word was saved or deleted. */
    val content: SharedFlow<Unit> = contentEvents.asSharedFlow()

    /** Emits after a reading position was written to the vault. */
    val progress: SharedFlow<Unit> = progressEvents.asSharedFlow()

    /** Records a discrete content change: a book imported, a word saved or deleted. */
    fun contentChanged() {
        contentEvents.tryEmit(Unit)
    }

    /** Records a reading-progress change: the reader moved to a new position. */
    fun progressChanged() {
        progressEvents.tryEmit(Unit)
    }

    private companion object {
        /**
         * The emitters never suspend — `tryEmit` returns at once — so a buffer
         * only guards against a burst a subscriber has not drained yet; a full
         * buffer drops a value that would coalesce anyway.
         */
        const val CONTENT_BUFFER = 16
        const val PROGRESS_BUFFER = 64
    }
}
