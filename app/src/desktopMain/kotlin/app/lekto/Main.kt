package app.lekto

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.lekto.core.text.IcuTextSegmenter

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Lekto") {
        App(segmenter = IcuTextSegmenter())
    }
}
