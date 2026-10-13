package app.lekto

import app.lekto.core.language.JvmManifestDownloader
import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.ManifestLanguageCatalog
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.language.LanguageRelease
import java.io.File

/**
 * The desktop wiring of the **Language manifest** (issue #146): it lives under
 * the same app-private derived root as the pack and the difficulty table, and is
 * fetched from the published release. Being a derived asset, it is structurally
 * outside the vault and any export (ADR-0005).
 *
 * @param root the vault root, defaulting to [desktopVaultRoot]; the parameter is
 *   the test seam, so production never passes one.
 */
fun desktopLanguage(root: File = desktopVaultRoot()): LanguageCatalog {
    val derived = DerivedAssetStore(JvmVaultFileSystem(File(root.parentFile, "derived")))
    return ManifestLanguageCatalog(
        derived = derived,
        downloader = JvmManifestDownloader(),
        url = LanguageRelease.URL,
    )
}
