package app.lekto

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges Android's `OpenDocument` activity result to the suspend [PickedFile]
 * the [App] expects.
 *
 * Launching a picker is an activity action that returns through a callback, not
 * a suspend call, so a pick is a one-shot deferred. The deferred is created by
 * [begin] *before* the activity is launched, so the result callback can never
 * race a later [await]. A cancelled or unreadable pick resolves to `null`, so the
 * app stays usable.
 */
class AndroidFilePicker {

    private var pending: CompletableDeferred<PickedFile?>? = null

    /** Arms the picker; call before launching the activity, so no result races [await]. */
    fun begin() {
        pending = CompletableDeferred()
    }

    /** Suspends until the armed picker answers; `null` when the user cancels. */
    suspend fun await(): PickedFile? {
        val deferred = pending ?: CompletableDeferred<PickedFile?>().also { pending = it }
        return deferred.await()
    }

    /** Completes the pending pick with [file] (or `null` on cancel). */
    fun resolve(file: PickedFile?) {
        pending?.complete(file)
        pending = null
    }

    /** The MIME types the picker offers: EPUB, plain text, and everything else. */
    val mimeTypes: Array<String> = arrayOf("application/epub+zip", "text/plain", "*/*")
}

/** Reads the picked [uri] through the content resolver, or `null` when it cannot be read. */
fun readPickedFile(context: Context, uri: Uri): PickedFile? = runCatching {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment ?: "book"
    val bytes = context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
    bytes?.let { data -> PickedFile(name, data) }
}.getOrNull()
