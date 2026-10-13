package app.lekto

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.lekto.core.speech.JvmPronouncer
import app.lekto.core.text.IcuTextSegmenter
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

@Suppress("LongMethod") // The desktop composition root wires each independent seam once; splitting scatters it.
fun main() = application {
    val library = remember { desktopBookLibrary() }
    val vocabulary = remember { desktopVocabulary() }
    val readerLevels = remember { desktopReaderLevels() }
    val dictionary = remember { desktopDictionary() }
    val difficulty = remember { desktopDifficulty() }
    val languages = remember { desktopLanguage() }
    val pronouncer = remember { JvmPronouncer() }
    val vaultTransfer = remember { desktopVaultTransfer(save = ::saveVaultToFile, open = ::pickVaultFileToImport) }
    val secrets = remember { desktopSecretStore() }
    val llm = remember { desktopLlm() }
    val sync = remember { desktopSync() }
    // The desktop "the app came back" signal (issue #121): a shared flow the
    // window's focus listener raises, so sync runs when the user returns to the
    // app rather than only on its bounded timer.
    val resumes = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    Window(onCloseRequest = ::exitApplication, title = "Lekto") {
        DisposableEffect(window, resumes) {
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(event: WindowEvent?) {
                    resumes.tryEmit(Unit)
                }

                override fun windowLostFocus(event: WindowEvent?) = Unit
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        App(
            AppEnvironment(
                segmenter = IcuTextSegmenter(),
                library = library,
                lemmas = dictionary.lemmas,
                vocabulary = vocabulary,
                readerLevels = readerLevels,
                pickFile = ::pickFileToImport,
                dictionary = dictionary,
                difficulty = difficulty,
                languages = languages,
                pronouncer = pronouncer,
                openUrl = ::openInBrowser,
                vaultTransfer = vaultTransfer,
                secrets = secrets,
                llm = llm,
                sync = sync,
                onResume = resumes,
            ),
        )
    }
}
