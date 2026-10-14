package app.lekto.core.dictionary

import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JsonVaultStore
import app.lekto.testkit.FakeDictionaryPackFiles
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.assertFailsWith

/**
 * The dictionary pack's lifecycle (issue #18), one per (Reading language ×
 * **Translation language**) pair (issue #149): it downloads on demand into the
 * derived store, reports what is installed, refuses a pack whose format it does
 * not understand, and degrades honestly when the download or the pack fails.
 */
class DictionaryPackInstallerTest :
    FunSpec({

        fun installer(
            derived: DerivedAssetStore = DerivedAssetStore(InMemoryVaultFileSystem()),
            reading: String = "en",
            translation: String = "fr",
        ): Pair<DictionaryPackInstaller, FakeDictionaryPackFiles> {
            val files = FakeDictionaryPackFiles(derived, reading, translation)
            return DictionaryPackInstaller(
                derived = derived,
                downloader = files,
                databases = files,
                readingLanguage = reading,
                translationLanguage = translation,
            ) to files
        }

        test("an install downloads the pack, reports it ready and opens it") {
            val (packInstaller, files) = installer()

            val state = packInstaller.install(URL)

            state.shouldBeInstanceOf<DictionaryPackState.Ready>()
            packInstaller.status().shouldBeInstanceOf<DictionaryPackState.Ready>()
            packInstaller.open() shouldNotBe null
            files.downloads shouldBe 1
        }

        test("an uninstalled pack reports not installed and cannot be opened") {
            val (packInstaller, _) = installer()

            packInstaller.status() shouldBe DictionaryPackState.NotInstalled
            packInstaller.open() shouldBe null
        }

        test("a pack of an unsupported format is refused, reported and removed") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (packInstaller, files) = installer(derived)
            files.version = DictionaryPack.FORMAT_VERSION + 1

            packInstaller.install(URL) shouldBe
                DictionaryPackState.Incompatible(DictionaryPack.FORMAT_VERSION + 1, DictionaryPack.FORMAT_VERSION)
            packInstaller.status() shouldBe
                DictionaryPackState.Incompatible(DictionaryPack.FORMAT_VERSION + 1, DictionaryPack.FORMAT_VERSION)
            // The refused file is gone, so a fresh installer sees nothing to refuse.
            installer(derived).first.status() shouldBe DictionaryPackState.NotInstalled
        }

        test("a corrupt pack degrades to a corrupt state rather than throwing") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (packInstaller, files) = installer(derived)
            files.openFailure = IllegalStateException("file is not a database")

            val state = packInstaller.install(URL)

            state.shouldBeInstanceOf<DictionaryPackState.Corrupt>()
            state.reason shouldBe "file is not a database"
            installer(derived).first.status() shouldBe DictionaryPackState.NotInstalled
        }

        test("a failed download propagates and leaves nothing installed") {
            val (packInstaller, files) = installer()
            files.failure = java.io.IOException("offline")

            assertFailsWith<java.io.IOException> { packInstaller.install(URL) }

            packInstaller.status() shouldBe DictionaryPackState.NotInstalled
        }

        test("removing the pack uninstalls it") {
            val (packInstaller, _) = installer()
            packInstaller.install(URL)

            packInstaller.remove()

            packInstaller.status() shouldBe DictionaryPackState.NotInstalled
            packInstaller.open() shouldBe null
        }

        test("removing an absent pack is harmless") {
            val (packInstaller, _) = installer()

            packInstaller.remove() shouldBe DictionaryPackState.NotInstalled
            packInstaller.status() shouldBe DictionaryPackState.NotInstalled
        }

        test("a pre-existing unreadable pack reads as corrupt without an install") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (packInstaller, files) = installer(derived)
            derived.put(DictionaryPackInstaller.pathOf("en", "fr"), byteArrayOf(1))
            files.openFailure = IllegalStateException("file is not a database")

            packInstaller.status() shouldBe DictionaryPackState.Corrupt("file is not a database")
            packInstaller.open() shouldBe null
        }

        test("each pair owns its own pack, so installing one leaves the other alone") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (english, _) = installer(derived, reading = "en", translation = "fr")
            val (french, _) = installer(derived, reading = "fr", translation = "en")

            english.install(URL)

            english.status().shouldBeInstanceOf<DictionaryPackState.Ready>()
            french.status() shouldBe DictionaryPackState.NotInstalled
        }

        test("the pair's path collapses each half to its base tag") {
            DictionaryPackInstaller.pathOf("en-US", "fr-CA") shouldBe "dictionary/en-fr/pack.sqlite"
        }

        test("the installed pack is a derived asset that never reaches the vault or its export") {
            val vault = JsonVaultStore(InMemoryVaultFileSystem())
            vault.put(testVaultRecord("a"))
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (packInstaller, _) = installer(derived)

            packInstaller.install(URL)

            derived.paths() shouldBe listOf(DictionaryPackInstaller.pathOf("en", "fr"))
            vault.exportBundle().decodeToString().contains("dictionary-pack-bytes") shouldBe false
        }
    })

/** A stand-in download address; the installer only hands it to the downloader. */
private const val URL: String = "https://example.test/pack.sqlite.gz"
