package app.lekto

import app.lekto.core.Seams
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.VaultBookLibrary
import app.lekto.core.epub.EpubParser
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.TxtParser
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.DeviceIdFile
import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The desktop wiring of the reading loop: the vault-backed library and a Swing
 * file picker.
 *
 * The vault and derived stores sit under the app-private data directory
 * (ADR-0010); the parser set and the device id are the two pieces the domain
 * cannot reach for. `sun.awt`/Swing is already present on the desktop JVM, so
 * the picker adds no dependency.
 */
fun desktopBookLibrary(): BookLibrary {
    val root = desktopVaultRoot()
    return VaultBookLibrary(
        vault = JsonVaultStore(JvmVaultFileSystem(root)),
        derived = DerivedAssetStore(JvmVaultFileSystem(File(root.parentFile, "derived"))),
        parsers = desktopParsers(),
        seams = Seams.system(),
        deviceId = desktopDeviceId(),
    )
}

/** The installation's stable device id, stored beside the vault. */
fun desktopDeviceId(): DeviceId = DeviceIdFile(File(desktopVaultRoot().parentFile, "device-id")).get()

private fun desktopParsers(): Map<BookFormat, BookTextParser> = mapOf(
    BookFormat.EPUB to EpubParser(),
    BookFormat.TXT to TxtParser(),
)

/**
 * Reads the chosen file, or returns null when the dialog is cancelled.
 *
 * The dialog is modal and must run on the UI thread, so blocking here is
 * correct: the swing dialog suspends the compose frame until the user answers,
 * which is exactly what a file picker should do.
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
