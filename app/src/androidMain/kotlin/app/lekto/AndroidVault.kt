package app.lekto

import android.content.Context
import android.net.Uri
import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.core.vault.VaultStore
import app.lekto.settings.VaultTransfer
import java.io.File

/** The MIME type a vault export is written and read as; it is a JSON document. */
const val VAULT_MIME_TYPE: String = "application/json"

/**
 * The Android vault's root and store.
 *
 * `Context.filesDir` is `/data/data/app.lekto/files`, private to the app and
 * never a user-visible folder — ADR-0010's app-private vault, with portability
 * left to export/import. The same store implementation backs the desktop
 * client; only this root differs.
 */
fun androidVaultRoot(context: Context): File = File(context.filesDir, "vault")

fun androidVaultStore(context: Context): VaultStore = JsonVaultStore(JvmVaultFileSystem(androidVaultRoot(context)))

/**
 * The vault's whole-file transfer (issue #20) over `Context.filesDir`, with the
 * platform's [save] and [open] actions. The store is rooted the same as
 * [androidBookLibrary]'s, so an import is what the library next lists and an
 * export carries what the library wrote.
 */
fun androidVaultTransfer(
    context: Context,
    save: suspend (String, ByteArray) -> Boolean,
    open: suspend () -> PickedFile?,
): VaultTransfer = VaultTransfer(androidVaultStore(context), save, open)

/**
 * Writes [bytes] to the [uri] the save dialog returned. Throws when the content
 * resolver cannot open it, so the caller reports a failure rather than a cancel.
 */
fun writeVaultFile(context: Context, uri: Uri, bytes: ByteArray) {
    val stream = context.contentResolver.openOutputStream(uri, "wt")
        ?: error("The chosen file could not be opened for writing.")
    stream.use { output -> output.write(bytes) }
}
