package app.lekto

import android.content.Context
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
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.Vocabulary
import java.io.File

/**
 * The Android wiring of the reading loop: the vault-backed library and the
 * user's vocabulary, over `Context.filesDir` (ADR-0010).
 *
 * The parser set and the stable device id are the pieces the domain cannot reach
 * for; the vault and derived stores sit in app-private storage, so nothing here
 * needs a permission or a user-chosen folder.
 */
fun androidBookLibrary(context: Context): BookLibrary = VaultBookLibrary(
    vault = JsonVaultStore(JvmVaultFileSystem(androidVaultRoot(context))),
    derived = DerivedAssetStore(JvmVaultFileSystem(File(context.filesDir, "derived"))),
    parsers = androidParsers(),
    seams = Seams.system(),
    deviceId = androidDeviceId(context),
)

/**
 * The Android vocabulary (issue #22) over `Context.filesDir`. The manifest is
 * derived from the record files, so this store is the same vault
 * [androidBookLibrary] writes to even though each builds its own view of it.
 */
fun androidVocabulary(context: Context): Vocabulary = VaultVocabulary(
    vault = JsonVaultStore(JvmVaultFileSystem(androidVaultRoot(context))),
    seams = Seams.system(),
    deviceId = androidDeviceId(context),
)

private fun androidDeviceId(context: Context): DeviceId = DeviceIdFile(File(context.filesDir, "device-id")).get()

private fun androidParsers(): Map<BookFormat, BookTextParser> = mapOf(
    BookFormat.EPUB to EpubParser(),
    BookFormat.TXT to TxtParser(),
)
