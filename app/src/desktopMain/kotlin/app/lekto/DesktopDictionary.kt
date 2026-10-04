package app.lekto

import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.JvmPackDownloader
import app.lekto.core.dictionary.SqlitePackDatabaseFactory
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.dictionary.DictionaryRelease
import app.lekto.dictionary.DictionaryServices
import java.io.File

/**
 * The desktop wiring of the dictionary (issue #18): the pack lives under the
 * app-private derived root, is opened by `sqlite-jdbc`, and is downloaded from
 * the published release. The `derived` root is the sibling of the vault the
 * reading loop already uses, so the pack is structurally outside the vault and
 * any export (ADR-0005).
 *
 * @param root the vault root, defaulting to [desktopVaultRoot]; the parameter is
 *   the test seam, so production never passes one.
 */
fun desktopDictionary(root: File = desktopVaultRoot()): DictionaryServices {
    val derived = DerivedAssetStore(JvmVaultFileSystem(File(root.parentFile, "derived")))
    return DictionaryServices(
        DictionaryPackInstaller(
            derived = derived,
            downloader = JvmPackDownloader(),
            databases = SqlitePackDatabaseFactory,
            url = DictionaryRelease.URL,
        ),
    )
}
