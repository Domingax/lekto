package app.lekto

import app.lekto.core.book.ReadingPosition
import app.lekto.core.text.TextBlock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Persists reading positions one at a time, keeping only the latest.
 *
 * Rapid page turns must not write the vault concurrently: a record is written
 * atomically through a fixed temporary file, so two writers racing the same
 * record can lose a write or throw. Here a single collector reads [latest] and
 * saves each position in turn, and a newer position set while a save runs
 * replaces the pending one — flipping fast coalesces to the last page instead of
 * queueing every turn. A save that fails is swallowed: losing one position is
 * better than bringing the reader down, and the next page turn writes again.
 *
 * Each turn carries the **ReaderDocument**'s blocks with its position, so the
 * save can store the **Resume snippet** beside the position without opening the
 * book again (issue #258). A caller without a document records none.
 *
 * A landed save notifies [changes], so sync pushes the new position after the
 * reader pauses rather than waiting for its timer (issue #121; ADR-0028).
 */
class ReadingProgressWriter(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    /** Notified after each position write, so sync pushes reading progress (issue #121). */
    private val changes: LocalChanges,
    private val save: suspend (ReadingPosition, List<TextBlock>) -> Unit,
) {
    private data class PendingSave(val position: ReadingPosition, val blocks: List<TextBlock>)

    private val latest = MutableStateFlow<PendingSave?>(null)

    init {
        scope.launch(dispatcher) {
            latest.filterNotNull().collect { pending ->
                runCatching { save(pending.position, pending.blocks) }.onSuccess { changes.progressChanged() }
            }
        }
    }

    /**
     * Records [position] as the latest, with the [blocks] the reader rendered so
     * its snippet can be stored; positions superseded before a save ran are
     * dropped. [blocks] defaults to empty for a caller that has no document.
     */
    fun record(position: ReadingPosition, blocks: List<TextBlock> = emptyList()) {
        latest.value = PendingSave(position, blocks)
    }
}
