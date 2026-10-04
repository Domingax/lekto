package app.lekto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.lekto.core.MasteryLookup
import app.lekto.core.text.IcuTextSegmenter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val library = androidBookLibrary(this)
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
            App(
                AppEnvironment(
                    segmenter = segmenter,
                    library = library,
                    mastery = MasteryLookup.AllKnown,
                    pickFile = {
                        // Arm the picker before launching, then park the import until
                        // it answers — the order closes the resolve-before-await race.
                        picker.begin()
                        launcher.launch(picker.mimeTypes)
                        picker.await()
                    },
                    dictionary = dictionary,
                    openUrl = { url -> openInBrowser(this@MainActivity, url) },
                ),
            )
        }
    }
}

/**
 * The dispatcher the platform entry point reads picked files on. Kept as a
 * function so the entry point has one seam for its I/O dispatcher; a hard-coded
 * `Dispatchers.IO` inside the coroutine would be untestable.
 */
@Suppress("InjectDispatcher") // The composition root is where a real dispatcher belongs; tests inject their own.
private fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
