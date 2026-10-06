package app.lekto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.lekto.core.book.BookLibrary
import app.lekto.core.secret.AndroidSecretStore
import app.lekto.core.speech.AndroidPronouncer
import app.lekto.core.text.IcuTextSegmenter
import app.lekto.core.vocabulary.Vocabulary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var pronouncer: AndroidPronouncer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val library = androidBookLibrary(this)
        val vocabulary = androidVocabulary(this)
        pronouncer = AndroidPronouncer(this)
        setContent { LektoApp(library, vocabulary, pronouncer) }
    }

    override fun onDestroy() {
        pronouncer?.shutdown()
        super.onDestroy()
    }
}

/**
 * The Android composition root: the platform pieces the [MainActivity] supplies
 * to the shared [App], wired where the Activity's `Context` is in scope.
 */
@Composable
private fun LektoApp(library: BookLibrary, vocabulary: Vocabulary, speech: AndroidPronouncer?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val segmenter = remember { IcuTextSegmenter() }
    val picker = remember { AndroidFilePicker() }
    val dictionary = remember { androidDictionary(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            picker.resolve(null)
        } else {
            scope.launch {
                picker.resolve(withContext(ioDispatcher()) { readPickedFile(context, uri) })
            }
        }
    }
    val vaultTransfer = rememberAndroidVaultTransfer(context, scope)
    App(
        AppEnvironment(
            segmenter = segmenter,
            library = library,
            lemmas = dictionary.lemmas,
            vocabulary = vocabulary,
            pickFile = {
                // Arm the picker before launching, then park the import until
                // it answers — the order closes the resolve-before-await race.
                picker.begin()
                launcher.launch(picker.mimeTypes)
                picker.await()
            },
            dictionary = dictionary,
            pronouncer = speech,
            openUrl = { url -> openInBrowser(context, url) },
            vaultTransfer = vaultTransfer,
            secrets = AndroidSecretStore(context),
            llm = androidLlm(context),
        ),
    )
}

/**
 * The dispatcher the platform entry point reads picked files on. Kept as a
 * function so the entry point has one seam for its I/O dispatcher; a hard-coded
 * `Dispatchers.IO` inside the coroutine would be untestable.
 */
@Suppress("InjectDispatcher") // The composition root is where a real dispatcher belongs; tests inject their own.
internal fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
