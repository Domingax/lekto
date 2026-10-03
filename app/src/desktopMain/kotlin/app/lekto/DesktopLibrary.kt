package app.lekto

import app.lekto.core.Seams
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.VaultBookLibrary
import app.lekto.core.epub.EpubParser
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.TxtParser
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceIdFile
import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import java.io.File

/**
 * The desktop wiring of the reading loop: the vault-backed library.
 *
 * The vault and derived stores sit under the app-private data directory
 * (ADR-0010); the parser set and the device id are the two pieces the domain
 * cannot reach for. The file picker lives in `DesktopFilePicker.kt`, kept apart
 * so this testable wiring carries no Swing dependency.
 *
 * @param root the vault root, defaulting to [desktopVaultRoot]; the parameter is
 *   the test seam, so production never passes one.
 */
fun desktopBookLibrary(root: File = desktopVaultRoot()): BookLibrary = VaultBookLibrary(
    vault = JsonVaultStore(JvmVaultFileSystem(root)),
    derived = DerivedAssetStore(JvmVaultFileSystem(File(root.parentFile, "derived"))),
    parsers = desktopParsers(),
    seams = Seams.system(),
    deviceId = DeviceIdFile(File(root.parentFile, "device-id")).get(),
)

private fun desktopParsers(): Map<BookFormat, BookTextParser> = mapOf(
    BookFormat.EPUB to EpubParser(),
    BookFormat.TXT to TxtParser(),
)
