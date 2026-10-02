package app.lekto

import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.core.vault.VaultStore
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

private fun appDataDirectory(appName: String): File {
    val home = File(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> File(System.getenv("APPDATA") ?: home.path, appName)
        os.contains("mac") -> File(home, "Library/Application Support/$appName")
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, appName)
    }
}
