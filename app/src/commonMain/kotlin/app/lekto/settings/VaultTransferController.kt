package app.lekto.settings

import app.lekto.core.vault.VaultFormatException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * What the settings screen shows about exporting and importing the vault.
 *
 * [available] is `false` only where no [VaultTransfer] is wired — a build
 * without a file picker — so the section can say so instead of offering a dead
 * action. It never gates the reading experience.
 */
data class VaultUiState(
    val available: Boolean = false,
    val transferring: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * The vault's export/import state holder (issue #20): it runs the whole-file
 * transfer without blocking the UI, treats a cancelled file dialog as a no-op,
 * and turns a bad file into an honest message rather than a crash.
 *
 * The [scope] carries the file actions, because choosing a file opens a platform
 * dialog that must run on the UI thread; the [dispatcher] carries the vault's
 * bytes, which may be large, off that thread. Both are injected because a
 * hard-coded one cannot be driven by virtual time (docs/testing.md,
 * "Deterministic seams").
 */
class VaultTransferController(
    private val transfer: VaultTransfer,
    private val dispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(VaultUiState(available = true))
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    /** Exports the whole vault to a file the user chooses, reporting the outcome. */
    @Suppress("TooGenericExceptionCaught") // A failed write becomes a message; the app stays usable.
    fun export() {
        if (_state.value.transferring) return
        begin()
        scope.launch {
            try {
                val bytes = withContext(dispatcher) { transfer.vault.exportBundle() }
                val saved = transfer.save(FILE_NAME, bytes)
                _state.update { current -> current.copy(transferring = false, message = if (saved) EXPORTED else null) }
            } catch (failure: Exception) {
                fail(failure.message ?: "The vault could not be exported.")
            }
        }
    }

    /** Replaces the vault with the file the user chooses, reporting the outcome. */
    @Suppress("TooGenericExceptionCaught") // A bad file becomes a message; the app stays usable.
    fun import(onImported: () -> Unit = {}) {
        if (_state.value.transferring) return
        begin()
        scope.launch {
            try {
                val file = transfer.open()
                if (file == null) {
                    _state.update { current -> current.copy(transferring = false) }
                } else {
                    withContext(dispatcher) { transfer.vault.importBundle(file.bytes) }
                    _state.update { current ->
                        current.copy(transferring = false, message = IMPORTED)
                    }
                    // The vault now holds different records, so anything reading it
                    // — the library — must reload rather than show the old list.
                    onImported()
                }
            } catch (failure: Exception) {
                fail(importProblem(failure))
            }
        }
    }

    /** Clears the last outcome, so the section returns to its resting state. */
    fun dismiss() {
        _state.update { current -> current.copy(message = null, error = null) }
    }

    private fun begin() {
        _state.update { current -> current.copy(transferring = true, message = null, error = null) }
    }

    private fun fail(message: String) {
        _state.update { current -> current.copy(transferring = false, error = message) }
    }

    /** A user-facing reason [failure] stopped an import. */
    private fun importProblem(failure: Exception): String = when (failure) {
        is VaultFormatException -> failure.message ?: "That vault file cannot be read by this version of the app."
        is SerializationException -> "That file isn't a Lekto vault export."
        else -> failure.message ?: "The vault could not be imported."
    }

    companion object {
        /** The name the save dialog suggests; the user may change it. */
        const val FILE_NAME: String = "lekto-vault.json"

        const val EXPORTED: String = "Vault exported. Keep that file somewhere safe."

        const val IMPORTED: String = "Vault imported. Your books and vocabulary are restored."
    }
}
