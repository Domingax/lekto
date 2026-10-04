package app.lekto

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The desktop file dialogs: the one desktop surface that cannot run on the JVM
 * test lane, because each opens a modal Swing dialog.
 *
 * The dialogs are deliberately their own file so the testable wiring beside them
 * (`DesktopLibrary.kt`, `DesktopVault.kt`) is not dragged into a coverage
 * exclusion. A dialog is modal and must run on the UI thread, so blocking here
 * is correct: it suspends the compose frame until the user answers, which is what
 * a file dialog does. `sun.awt`/Swing is already present on the desktop JVM, so
 * this adds no dependency.
 */
fun pickFileToImport(): PickedFile? = chooseFile("Import a book", "Books (EPUB, TXT)", "epub", "txt")

/**
 * The desktop save dialog for a vault export (issue #20): writes [bytes] to the
 * file the user chooses and returns `true`, or `false` when they cancel. A write
 * failure throws, so the settings screen can report it rather than read as a
 * cancel.
 */
fun saveVaultToFile(fileName: String, bytes: ByteArray): Boolean {
    val chooser = JFileChooser().apply {
        dialogTitle = "Export vault"
        selectedFile = File(fileName)
        fileFilter = FileNameExtensionFilter("Lekto vault (JSON)", "json")
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return false
    val file = chooser.selectedFile
    if (file != null) file.writeBytes(bytes)
    return file != null
}

/** The desktop open dialog for a vault export to import. `null` when the user cancels. */
fun pickVaultFileToImport(): PickedFile? = chooseFile("Import a vault", "Lekto vault (JSON)", "json")

private fun chooseFile(title: String, description: String, vararg extensions: String): PickedFile? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(description, *extensions)
    }
    val approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
    val file = chooser.selectedFile
    return if (approved && file != null) PickedFile(file.name, file.readBytes()) else null
}
