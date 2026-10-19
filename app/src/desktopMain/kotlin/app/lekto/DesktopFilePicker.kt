package app.lekto

import app.lekto.text.AppTexts
import app.lekto.text.TextKey
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The desktop file dialogs: the one desktop surface that cannot run on the JVM
 * test lane, because each opens a modal Swing dialog.
 *
 * The dialogs are deliberately their own file so the testable wiring beside them
 * (`DesktopLibrary.kt`, `DesktopVault.kt`) is not dragged into a coverage
 * exclusion. A dialog is **modal**: it pumps its own nested event loop while the
 * user answers. It must therefore not run on the composition's dispatcher, whose
 * flush the loop would re-enter mid-resume (issue #192) — the desktop
 * composition root hands these to its own dialog dispatcher, and blocking there
 * is what a file dialog does. `sun.awt`/Swing is already present on the desktop
 * JVM, so this adds no dependency.
 *
 * The dialog's own chrome — its title and its file-type filter — is visible text,
 * so it comes from the reader's [AppTexts] (ADR-0034): the entry point resolves
 * the **app language** at the moment a dialog opens, so it follows the setting.
 */
fun pickFileToImport(texts: AppTexts): PickedFile? =
    chooseFile(texts[TextKey.PICK_BOOK_TITLE], texts[TextKey.BOOKS_FILTER], "epub", "txt")

/**
 * The desktop save dialog for a vault export (issue #20): writes [bytes] to the
 * file the user chooses and returns `true`, or `false` when they cancel. A write
 * failure throws, so the settings screen can report it rather than read as a
 * cancel.
 */
fun saveVaultToFile(texts: AppTexts, fileName: String, bytes: ByteArray): Boolean {
    val chooser = JFileChooser().apply {
        dialogTitle = texts[TextKey.EXPORT_VAULT_TITLE]
        selectedFile = File(fileName)
        fileFilter = FileNameExtensionFilter(texts[TextKey.VAULT_FILTER], "json")
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return false
    val file = chooser.selectedFile
    if (file != null) file.writeBytes(bytes)
    return file != null
}

/** The desktop open dialog for a vault export to import. `null` when the user cancels. */
fun pickVaultFileToImport(texts: AppTexts): PickedFile? =
    chooseFile(texts[TextKey.IMPORT_VAULT_TITLE], texts[TextKey.VAULT_FILTER], "json")

private fun chooseFile(title: String, description: String, vararg extensions: String): PickedFile? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(description, *extensions)
    }
    val approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
    val file = chooser.selectedFile
    return if (approved && file != null) PickedFile(file.name, file.readBytes()) else null
}
