package app.lekto

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.lekto.core.MasteryLookup
import app.lekto.core.text.IcuTextSegmenter

fun main() = application {
    val library = remember { desktopBookLibrary() }
    Window(onCloseRequest = ::exitApplication, title = "Lekto") {
        App(
            AppEnvironment(
                segmenter = IcuTextSegmenter(),
                library = library,
                mastery = MasteryLookup.AllKnown,
                pickFile = ::pickFileToImport,
            ),
        )
    }
}
