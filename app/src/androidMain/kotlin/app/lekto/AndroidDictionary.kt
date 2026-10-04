package app.lekto

import android.content.Context
import app.lekto.core.dictionary.AndroidPackDatabaseFactory
import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.JvmPackDownloader
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.dictionary.DictionaryRelease
import app.lekto.dictionary.DictionaryServices
import java.io.File

/**
 * The Android wiring of the dictionary (issue #18): the pack lives under the
 * app-private derived root, is opened with the framework's SQLite, and is
 * downloaded from the published release. The pack is a derived asset, so it sits
 * beside the vault rather than inside it and never reaches an export (ADR-0005).
 */
fun androidDictionary(context: Context): DictionaryServices {
    val derived = DerivedAssetStore(JvmVaultFileSystem(File(context.filesDir, "derived")))
    return DictionaryServices(
        DictionaryPackInstaller(
            derived = derived,
            downloader = JvmPackDownloader(),
            databases = AndroidPackDatabaseFactory,
            url = DictionaryRelease.URL,
        ),
    )
}
