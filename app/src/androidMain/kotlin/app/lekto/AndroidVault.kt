package app.lekto

import android.content.Context
import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.core.vault.VaultStore
import java.io.File

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
