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

fun main() = application {
    val library = remember { desktopBookLibrary() }
    val vocabulary = remember { desktopVocabulary() }
    val dictionary = remember { desktopDictionary() }
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
                pickFile = ::pickFileToImport,
                dictionary = dictionary,
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
