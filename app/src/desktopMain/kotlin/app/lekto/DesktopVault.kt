package app.lekto

import app.lekto.core.Seams
import app.lekto.core.language.JsonLanguageSettingsStore
import app.lekto.core.language.LanguageSettingsStore
import app.lekto.core.reading.JsonReadingPreferencesStore
import app.lekto.core.reading.ReadingPreferencesStore
import app.lekto.core.readinglevel.ReaderLevels
import app.lekto.core.readinglevel.VaultReaderLevels
import app.lekto.core.sync.JsonTombstoneStore
import app.lekto.core.vault.DeviceIdFile
import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.core.vault.VaultStore
import app.lekto.settings.VaultTransfer
import java.io.File

/**
 * The desktop vault's root and store.
 *
 * The root is an ordinary folder in the OS app-data directory — `~/.local/share`
 * (or `$XDG_DATA_HOME`) on Linux, `~/Library/Application Support` on macOS,
 * `%APPDATA%` on Windows. Being an ordinary folder is why it is not a
 * user-chosen one: ADR-0010 keeps the vault app-private and makes portability an
 * export, not a shared directory.
 */
fun desktopVaultRoot(): File = File(appDataDirectory("lekto"), "vault")

/**
 * The vault over [root], defaulting to [desktopVaultRoot]. The [root] parameter
 * is the test seam: production never passes one, a test points it at a
 * temporary directory.
 */
fun desktopVaultStore(root: File = desktopVaultRoot()): VaultStore = JsonVaultStore(JvmVaultFileSystem(root))

/**
 * The desktop **Reader levels** (issue #64; ADR-0030) over [root], defaulting to
 * [desktopVaultRoot]. A level is a vault record, so this store is the same vault
 * [desktopBookLibrary] writes to; its stable device id is the one the library
 * stamps its records with. Its tombstones live under the same `sync/` directory
 * the engine uses (ADR-0015), so clearing a level (issue #147) is propagated
 * rather than resurrected.
 */
fun desktopReaderLevels(root: File = desktopVaultRoot()): ReaderLevels = VaultReaderLevels(
    vault = JsonVaultStore(JvmVaultFileSystem(root)),
    tombstones = JsonTombstoneStore(JvmVaultFileSystem(File(root.parentFile, "sync"))),
    seams = Seams.system(),
    deviceId = DeviceIdFile(File(root.parentFile, "device-id")).get(),
)

/**
 * The desktop language settings (ADR-0034) over [root], defaulting to an
 * app-private folder beside the vault: the device's **app language** and
 * **Translation language**. They are device-local app configuration (ADR-0023's
 * pattern), not vault records, so they never enter an export.
 *
 * @param root the settings root, defaulting to the app-data directory; the
 *   parameter is the test seam, so production never passes one.
 */
fun desktopLanguageSettings(root: File = File(appDataDirectory("lekto"), "settings")): LanguageSettingsStore =
    JsonLanguageSettingsStore(JvmVaultFileSystem(root))

/**
 * The desktop reading preference (ADR-0035) over [root], defaulting to an
 * app-private folder beside the vault: the device's face, size and margin for a
 * book's text. It is device-local app configuration (ADR-0023's pattern), not a
 * vault record, so it never enters an export.
 *
 * @param root the settings root, defaulting to the app-data directory; the
 *   parameter is the test seam, so production never passes one.
 */
fun desktopReadingPreferences(root: File = File(appDataDirectory("lekto"), "settings")): ReadingPreferencesStore =
    JsonReadingPreferencesStore(JvmVaultFileSystem(root))

/**
 * The vault's whole-file transfer (issue #20) over [root], with the platform's
 * [save] and [open] dialogs. The store is rooted the same as
 * [desktopBookLibrary]'s, so an import is what the library next lists and an
 * export carries what the library wrote. The dialogs live in
 * `DesktopFilePicker.kt`; they are parameters so this wiring is testable.
 */
fun desktopVaultTransfer(
    root: File = desktopVaultRoot(),
    save: suspend (String, ByteArray) -> Boolean,
    open: suspend () -> PickedFile?,
): VaultTransfer = VaultTransfer(desktopVaultStore(root), save, open)

internal fun appDataDirectory(appName: String): File {
    val home = File(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> File(System.getenv("APPDATA") ?: home.path, appName)
        os.contains("mac") -> File(home, "Library/Application Support/$appName")
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, appName)
    }
}
