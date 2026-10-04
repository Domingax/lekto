package app.lekto

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.lekto.settings.VaultTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Android wiring of the vault's export/import (issue #20): an
 * `OpenDocument` picker and a `CreateDocument` saver, both armed before they are
 * launched so a result can never race the `await`, behind the platform's I/O
 * dispatcher. Kept out of the activity so its `onCreate` stays a composition
 * root rather than a long function.
 */
@Composable
internal fun rememberAndroidVaultTransfer(context: Context, scope: CoroutineScope): VaultTransfer {
    val vault = remember { androidVaultStore(context) }
    val picker = remember { AndroidFilePicker(arrayOf(VAULT_MIME_TYPE, "*/*")) }
    val saver = remember { AndroidDocumentSaver() }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            picker.resolve(null)
        } else {
            scope.launch { picker.resolve(withContext(ioDispatcher()) { readPickedFile(context, uri) }) }
        }
    }
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(VAULT_MIME_TYPE),
    ) { uri -> saver.resolve(uri) }
    return VaultTransfer(
        vault = vault,
        save = { name, bytes ->
            saver.begin()
            save.launch(name)
            val uri = saver.await()
            if (uri == null) {
                false
            } else {
                withContext(ioDispatcher()) { writeVaultFile(context, uri, bytes) }
                true
            }
        },
        open = {
            picker.begin()
            open.launch(picker.mimeTypes)
            picker.await()
        },
    )
}
