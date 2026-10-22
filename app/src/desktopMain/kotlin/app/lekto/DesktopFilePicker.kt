package app.lekto

import app.lekto.text.AppTexts
import app.lekto.text.TextKey
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView

/**
 * The desktop file dialogs: the one desktop surface that cannot run on the JVM
 * test lane, because each opens a modal system dialog.
 *
 * Each of the three dialogs — import a Book, export the **Vault**, import a
 * **Vault** — is the desktop's own picker (issue #219): [FileDialog] draws GTK's
 * chooser on Linux, the native panel on macOS and the common dialog on Windows.
 * It is owned by the Compose window, so it is modal to the app, centred on it and
 * cannot open behind it. A dialog is **modal**: it pumps its own nested event
 * loop while the user answers. It must therefore not run on the composition's
 * dispatcher, whose flush the loop would re-enter mid-resume (issue #192) — the
 * desktop composition root hands these to its own dialog dispatcher, and
 * blocking there is what a file dialog does. `java.awt`/Swing is already present
 * on the desktop JVM, so this adds no dependency.
 *
 * The dialog's title is the app's own text, so it comes from the reader's
 * [AppTexts] (ADR-0034): the entry point resolves the **app language** at the
 * moment a dialog opens, so it follows the setting. The dialog's own labels and
 * buttons are the OS's, though: the system chooser follows the OS locale, not the
 * app language. That is the accepted price of the system dialog (issue #219).
 *
 * [FileDialog.setFilenameFilter] still narrows the listing to the extensions
 * asked for: GTK applies it as its custom filter, and the Windows common dialog as
 * an item-include hook, where its type dropdown keeps its own "All files" label
 * because AWT cannot add a labelled type. The Swing fallback does show the
 * description, labelled with the dialog's title. The filter is advisory either
 * way: a file the app cannot read still lands in the existing import-error path.
 *
 * Should constructing or showing the system dialog fail — GTK init failure, a
 * headless JVM, an AWT error — the dialog falls back to the Swing chooser it used
 * before (issue #219), so the behaviour never gets worse than that.
 */
fun pickFileToImport(owner: Frame, texts: AppTexts): PickedFile? {
    val title = texts[TextKey.PICK_BOOK_TITLE]
    val request = DialogRequest(title, FileNameExtensionFilter(title, "epub", "txt"), FileDialog.LOAD)
    return readPickedFile(owner, request)
}

/**
 * The desktop save dialog for a vault export (issue #20): writes [bytes] to the
 * file the user chooses and returns `true`, or `false` when they cancel. The
 * dialog pre-fills [fileName], and the system chooser asks before overwriting an
 * existing file. A write failure throws, so the settings screen can report it
 * rather than read as a cancel.
 */
fun saveVaultToFile(owner: Frame, texts: AppTexts, fileName: String, bytes: ByteArray): Boolean {
    val title = texts[TextKey.EXPORT_VAULT_TITLE]
    val request = DialogRequest(title, FileNameExtensionFilter(title, "json"), FileDialog.SAVE, fileName)
    val file = chooseFile(owner, request) ?: return false
    file.writeBytes(bytes)
    return true
}

/** The desktop open dialog for a vault export to import. `null` when the user cancels. */
fun pickVaultFileToImport(owner: Frame, texts: AppTexts): PickedFile? {
    val title = texts[TextKey.IMPORT_VAULT_TITLE]
    val request = DialogRequest(title, FileNameExtensionFilter(title, "json"), FileDialog.LOAD)
    return readPickedFile(owner, request)
}

private fun readPickedFile(owner: Frame, request: DialogRequest): PickedFile? {
    val file = chooseFile(owner, request) ?: return null
    return PickedFile(file.name, file.readBytes())
}

/**
 * One request the three entry points share. [filter] narrows the listing;
 * [initialName] pre-fills a save dialog; [mode] is [FileDialog]'s `LOAD` or
 * `SAVE`. The filter's description is the title, since it is never displayed and
 * there is no reason to keep a second string for it.
 */
private class DialogRequest(
    val title: String,
    val filter: FileNameExtensionFilter,
    val mode: Int,
    val initialName: String? = null,
)

private fun chooseFile(owner: Frame, request: DialogRequest): File? = runCatching {
    showSystemFileDialog(owner, request)
}.getOrElse {
    showSwingFileChooser(owner, request)
}

private fun showSystemFileDialog(owner: Frame, request: DialogRequest): File? {
    val dialog = FileDialog(owner, request.title, request.mode).apply {
        // The process's working directory is the install folder under jpackage,
        // which the reader cannot browse or write. Start where the Swing chooser
        // did: the platform's default document folder.
        directory = FileSystemView.getFileSystemView().defaultDirectory.absolutePath
        // Windows calls this with the item's parent directory; use it, so a folder
        // stays navigable instead of being resolved against the working directory.
        filenameFilter = FilenameFilter { parent, name ->
            val candidate = File(parent, name)
            candidate.isDirectory || request.filter.accept(candidate)
        }
        request.initialName?.let { file = it }
        isVisible = true
    }
    val name = dialog.file ?: return null
    val directory = dialog.directory
    return if (directory == null) File(name) else File(directory, name)
}

private fun showSwingFileChooser(owner: Frame, request: DialogRequest): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = request.title
        fileFilter = request.filter
        request.initialName?.let { selectedFile = File(it) }
    }
    val choice = if (request.mode == FileDialog.SAVE) chooser.showSaveDialog(owner) else chooser.showOpenDialog(owner)
    return if (choice == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/**
 * Loads the system FreeType before Skiko does, on Linux (issue #219).
 *
 * Skiko statically links FreeType and exports its symbols. When GTK — which AWT's
 * [FileDialog] uses on Linux — initialises after Skiko, cairo can bind its font
 * calls to Skiko's copy instead of the system one, and every GTK widget fails to
 * draw: the chooser opens black, with `drawing failure for widget ...: error
 * occurred in libfreetype` on stderr (CMP-6022). Loading the system library first
 * makes GTK's calls resolve to the copy it was built against.
 *
 * Best effort: with no discoverable library the dialogs still open, exactly as
 * they did before. Called once by the composition root, before Compose starts
 * Skiko. It is a no-op off Linux.
 */
internal fun preloadSystemFreeType() {
    if (!System.getProperty("os.name").lowercase().contains("linux")) return
    val library = findSystemFreeType() ?: return
    runCatching { System.load(library.absolutePath) }
}

/**
 * The library's usual homes, the multiarch one first. A fixed list rather than a
 * directory walk: on the development machine, listing `/usr/lib` and its children
 * cost 44 ms before the window could appear, and the file sits in one of a
 * handful of directories on every mainstream Linux layout.
 */
private fun findSystemFreeType(): File? = freeTypeDirectories()
    .asSequence()
    .map { File(it, "libfreetype.so.6") }
    .firstOrNull { it.isFile }

private fun freeTypeDirectories(): List<String> = buildList {
    multiarchDirectory(System.getProperty("os.arch").lowercase())?.let { architecture ->
        add("/usr/lib/$architecture")
        add("/lib/$architecture")
    }
    add("/usr/lib")
    add("/lib")
    add("/usr/lib64")
    add("/lib64")
}

/** Debian's architecture tuple, which is how a multiarch system names its library directory. */
private fun multiarchDirectory(architecture: String): String? = when (architecture) {
    "amd64", "x86_64" -> "x86_64-linux-gnu"
    "aarch64" -> "aarch64-linux-gnu"
    "arm", "armv7l" -> "arm-linux-gnueabihf"
    "x86", "i386", "i686" -> "i386-linux-gnu"
    "riscv64" -> "riscv64-linux-gnu"
    "ppc64le" -> "powerpc64le-linux-gnu"
    "s390x" -> "s390x-linux-gnu"
    else -> null
}
