package app.lekto

import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The desktop file picker: the one desktop entry point that cannot run on the
 * JVM test lane, because it opens a modal Swing dialog.
 *
 * The chooser is deliberately its own file so the testable wiring beside it
 * (`DesktopLibrary.kt`) is not dragged into a coverage exclusion. The dialog is
 * modal and must run on the UI thread, so blocking here is correct: it suspends
 * the compose frame until the user answers, which is what a file picker does.
 * `sun.awt`/Swing is already present on the desktop JVM, so this adds no
 * dependency.
 */
fun pickFileToImport(): PickedFile? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import a book"
        fileFilter = FileNameExtensionFilter("Books (EPUB, TXT)", "epub", "txt")
    }
    val approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
    val file = chooser.selectedFile
    return if (approved && file != null) PickedFile(file.name, file.readBytes()) else null
}
