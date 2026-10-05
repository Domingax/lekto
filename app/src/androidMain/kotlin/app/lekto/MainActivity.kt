package app.lekto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.lekto.core.speech.AndroidPronouncer
import app.lekto.core.text.IcuTextSegmenter
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
        val speech = pronouncer
        setContent {
            val scope = rememberCoroutineScope()
            val segmenter = remember { IcuTextSegmenter() }
            val picker = remember { AndroidFilePicker() }
            val dictionary = remember { androidDictionary(this@MainActivity) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) {
                    picker.resolve(null)
                } else {
                    scope.launch {
                        picker.resolve(withContext(ioDispatcher()) { readPickedFile(this@MainActivity, uri) })
                    }
                }
            }
            val vaultTransfer = rememberAndroidVaultTransfer(this@MainActivity, scope)
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
                    openUrl = { url -> openInBrowser(this@MainActivity, url) },
                    vaultTransfer = vaultTransfer,
                ),
            )
        }
    }

    override fun onDestroy() {
        pronouncer?.shutdown()
        super.onDestroy()
    }
}

/**
 * The dispatcher the platform entry point reads picked files on. Kept as a
 * function so the entry point has one seam for its I/O dispatcher; a hard-coded
 * `Dispatchers.IO` inside the coroutine would be untestable.
 */
@Suppress("InjectDispatcher") // The composition root is where a real dispatcher belongs; tests inject their own.
internal fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
