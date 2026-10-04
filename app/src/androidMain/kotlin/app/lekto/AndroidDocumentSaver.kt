package app.lekto

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges Android's `CreateDocument` activity result to the suspend save the
 * vault export expects (issue #20).
 *
 * Like [AndroidFilePicker], the deferred is created by [begin] before the
 * activity is launched, and the result is the chosen [Uri] — or `null` when the
 * user cancels — so the caller writes the bytes and reports the outcome.
 */
internal class AndroidDocumentSaver {

    private var pending: CompletableDeferred<Uri?>? = null

    /** Arms the saver; call before launching the activity, so no result races [await]. */
    fun begin() {
        pending = CompletableDeferred()
    }

    /** Suspends until the armed saver answers; `null` when the user cancels. */
    suspend fun await(): Uri? {
        val deferred = pending ?: CompletableDeferred<Uri?>().also { pending = it }
        return deferred.await()
    }

    /** Completes the pending save with [uri] (or `null` on cancel). */
    fun resolve(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }
}
