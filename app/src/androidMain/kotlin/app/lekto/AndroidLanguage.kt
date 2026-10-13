package app.lekto

import android.content.Context
import app.lekto.core.language.JvmManifestDownloader
import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.ManifestLanguageCatalog
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.language.LanguageRelease
import java.io.File

/**
 * The Android wiring of the **Language manifest** (issue #146): it lives under
 * the same app-private derived root as the pack and the difficulty table, and is
 * fetched from the published release. It is a derived asset, so it sits beside
 * the vault rather than inside it and never reaches an export (ADR-0005).
 */
fun androidLanguage(context: Context): LanguageCatalog {
    val derived = DerivedAssetStore(JvmVaultFileSystem(File(context.filesDir, "derived")))
    return ManifestLanguageCatalog(
        derived = derived,
        downloader = JvmManifestDownloader(),
        url = LanguageRelease.URL,
    )
}
