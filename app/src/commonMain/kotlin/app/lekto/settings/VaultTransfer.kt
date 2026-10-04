package app.lekto.settings

import app.lekto.PickedFile
import app.lekto.core.vault.VaultStore

/**
 * The vault's whole-file transfer seam (issue #20): the [vault] to export and
 * import, plus the two platform actions that carry its bytes to and from a file
 * the user chooses.
 *
 * Choosing a file is a platform action — a Swing dialog on desktop, an
 * `ActivityResult` on Android — so it stays behind this seam, exactly as the
 * book import's picker does. [save] returns `false` when the user cancels the
 * save dialog; [open] returns `null` when the user cancels the open dialog. Both
 * are called on the caller's (UI) thread, so a platform implementation may show
 * a dialog, and both may throw when the file cannot be written or read.
 */
class VaultTransfer(
    val vault: VaultStore,
    val save: suspend (fileName: String, bytes: ByteArray) -> Boolean,
    val open: suspend () -> PickedFile?,
)
