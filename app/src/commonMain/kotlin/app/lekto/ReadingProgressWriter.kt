package app.lekto

import app.lekto.core.book.ReadingPosition
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
 */
class ReadingProgressWriter(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val save: suspend (ReadingPosition) -> Unit,
) {
    private val latest = MutableStateFlow<ReadingPosition?>(null)

    init {
        scope.launch(dispatcher) {
            latest.filterNotNull().collect { position -> runCatching { save(position) } }
        }
    }

    /** Records [position] as the latest; positions superseded before a save ran are dropped. */
    fun record(position: ReadingPosition) {
        latest.value = position
    }
}
