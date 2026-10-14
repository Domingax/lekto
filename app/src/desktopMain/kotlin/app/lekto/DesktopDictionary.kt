package app.lekto

import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.JvmPackDownloader
import app.lekto.core.dictionary.SqlitePackDatabaseFactory
import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.dictionary.DictionaryRelease
import app.lekto.dictionary.DictionaryServices
import app.lekto.difficulty.DifficultyServices
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

/**
 * The desktop wiring of the difficulty table (issue #64; ADR-0029): one table per
 * reading language, each living under the same app-private derived root as the
 * pack, opened by `sqlite-jdbc`, and downloaded from the published release. A
 * language's installer is built on demand from the language the **Language
 * manifest** offers a table for (issue #148). Being a derived asset, the table is
 * structurally outside the vault and any export (ADR-0005).
 *
 * @param root the vault root, defaulting to [desktopVaultRoot]; the parameter is
 *   the test seam, so production never passes one.
 */
fun desktopDifficulty(root: File = desktopVaultRoot()): DifficultyServices {
    val derived = DerivedAssetStore(JvmVaultFileSystem(File(root.parentFile, "derived")))
    return DifficultyServices { language ->
        DifficultyInstaller(
            derived = derived,
            downloader = JvmPackDownloader(),
            databases = SqlitePackDatabaseFactory,
            language = language,
        )
    }
}
