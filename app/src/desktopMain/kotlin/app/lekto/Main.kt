package app.lekto

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.lekto.core.speech.JvmPronouncer
import app.lekto.core.text.IcuTextSegmenter

fun main() = application {
    val library = remember { desktopBookLibrary() }
    val vocabulary = remember { desktopVocabulary() }
    val dictionary = remember { desktopDictionary() }
    val pronouncer = remember { JvmPronouncer() }
    val vaultTransfer = remember { desktopVaultTransfer(save = ::saveVaultToFile, open = ::pickVaultFileToImport) }
    val secrets = remember { desktopSecretStore() }
    Window(onCloseRequest = ::exitApplication, title = "Lekto") {
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
            ),
        )
    }
}
