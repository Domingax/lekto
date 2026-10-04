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
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.Vocabulary
import java.io.File

/**
 * The desktop wiring of the reading loop: the vault-backed library and the
 * user's vocabulary.
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

/**
 * The desktop vocabulary (issue #22) over [root], defaulting to
 * [desktopVaultRoot]. The manifest is derived from the record files, so this
 * store is the same vault [desktopBookLibrary] writes to even though each builds
 * its own view of it.
 */
fun desktopVocabulary(root: File = desktopVaultRoot()): Vocabulary = VaultVocabulary(
    vault = JsonVaultStore(JvmVaultFileSystem(root)),
    seams = Seams.system(),
    deviceId = DeviceIdFile(File(root.parentFile, "device-id")).get(),
)

private fun desktopParsers(): Map<BookFormat, BookTextParser> = mapOf(
    BookFormat.EPUB to EpubParser(),
    BookFormat.TXT to TxtParser(),
)
