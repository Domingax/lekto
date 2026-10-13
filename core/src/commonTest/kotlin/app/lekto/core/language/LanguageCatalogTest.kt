package app.lekto.core.language

import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JsonVaultStore
import app.lekto.testkit.FakeManifestDownloader
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.testLanguageManifest
import app.lekto.testkit.testOfferedLanguage
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.test.assertFailsWith

/**
 * The **Language manifest**'s lifecycle (issue #146): it reports the cached
 * catalogue, fetches the published manifest on demand into the derived store,
 * refuses a manifest whose format it does not understand, and leaves the store
 * clean when a fetch fails.
 */
class LanguageCatalogTest :
    FunSpec({

        fun catalog(
            derived: DerivedAssetStore = DerivedAssetStore(InMemoryVaultFileSystem()),
            downloader: FakeManifestDownloader = FakeManifestDownloader(),
        ): Pair<ManifestLanguageCatalog, FakeManifestDownloader> =
            ManifestLanguageCatalog(derived, downloader, URL) to downloader

        test("with no cache the catalogue is unavailable and fetches nothing") {
            val (catalog, downloader) = catalog()

            catalog.status() shouldBe LanguageCatalogState.Unavailable
            downloader.downloads shouldBe 0
        }

        test("a reload fetches the manifest and caches it") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val offered = listOf(testOfferedLanguage("en", difficulty = "lekto-difficulty-en.sqlite.gz"))
            val bytes = testLanguageManifest(offered)
            val (catalog, _) = catalog(derived = derived, downloader = FakeManifestDownloader(bytes))

            catalog.reload() shouldBe LanguageCatalogState.Available(offered)
            derived.get(ManifestLanguageCatalog.PATH) shouldBe bytes
        }

        test("a relaunch reads the cache without fetching again") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val offered = listOf(testOfferedLanguage("en"))
            val (catalog, _) = catalog(
                derived = derived,
                downloader = FakeManifestDownloader(testLanguageManifest(offered)),
            )
            catalog.reload()

            val fresh = FakeManifestDownloader(byteArrayOf())
            ManifestLanguageCatalog(derived, fresh, URL).status() shouldBe LanguageCatalogState.Available(offered)
            fresh.downloads shouldBe 0
        }

        test("a manifest this build cannot read is refused and reported unavailable") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val good = testLanguageManifest(listOf(testOfferedLanguage("en")))
            val (catalog, downloader) = catalog(derived = derived, downloader = FakeManifestDownloader(good))
            catalog.reload() shouldBe LanguageCatalogState.Available(listOf(testOfferedLanguage("en")))
            downloader.bytes = testLanguageManifest(
                listOf(testOfferedLanguage("en")),
                formatVersion = LanguageManifest.FORMAT_VERSION + 1,
            )

            catalog.reload() shouldBe LanguageCatalogState.Unavailable
            // The refused fetch is not cached, so the good catalogue survives it.
            catalog.status() shouldBe LanguageCatalogState.Available(listOf(testOfferedLanguage("en")))
        }

        test("a malformed fetch is refused and leaves the cached catalogue intact") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val good = testLanguageManifest(listOf(testOfferedLanguage("en")))
            val (catalog, downloader) = catalog(derived = derived, downloader = FakeManifestDownloader(good))
            catalog.reload()
            downloader.bytes = "not a manifest".encodeToByteArray()

            catalog.reload() shouldBe LanguageCatalogState.Unavailable
            catalog.status() shouldBe LanguageCatalogState.Available(listOf(testOfferedLanguage("en")))
        }

        test("a cached manifest of a refused format reads as unavailable, never misread") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            derived.put(
                ManifestLanguageCatalog.PATH,
                testLanguageManifest(
                    listOf(testOfferedLanguage("en")),
                    formatVersion =
                    LanguageManifest.FORMAT_VERSION + 1,
                ),
            )
            val (catalog, _) = catalog(derived = derived)

            catalog.status() shouldBe LanguageCatalogState.Unavailable
        }

        test("a failed download propagates and leaves nothing installed") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val downloader = FakeManifestDownloader().apply { failure = java.io.IOException("offline") }
            val (catalog, _) = catalog(derived = derived, downloader = downloader)

            assertFailsWith<java.io.IOException> { catalog.reload() }

            catalog.status() shouldBe LanguageCatalogState.Unavailable
        }

        test("the cached manifest is a derived asset that never reaches the vault or its export") {
            val vault = JsonVaultStore(InMemoryVaultFileSystem())
            vault.put(testVaultRecord("a"))
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val marker = """{"formatVersion":1,"languages":[{"language":"en"}],"marker":"manifest-marker"}"""
            val (catalog, _) = catalog(
                derived = derived,
                downloader = FakeManifestDownloader(marker.encodeToByteArray()),
            )

            catalog.reload()

            derived.paths() shouldBe listOf(ManifestLanguageCatalog.PATH)
            vault.exportBundle().decodeToString().contains("manifest-marker") shouldBe false
        }
    })

private const val URL = "https://example.test/language-manifest.json"
