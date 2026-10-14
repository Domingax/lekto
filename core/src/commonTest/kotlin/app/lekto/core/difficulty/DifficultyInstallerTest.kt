package app.lekto.core.difficulty

import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JsonVaultStore
import app.lekto.testkit.FakeDifficultyFiles
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.assertFailsWith

/**
 * The **Difficulty table**'s lifecycle (ADR-0029): it downloads on demand into
 * the derived store, reports what is installed, refuses a table whose format it
 * does not understand (removing the refused file), and degrades honestly when the
 * download or the table fails.
 */
class DifficultyInstallerTest :
    FunSpec({

        val language = "en"
        val url = "https://example.test/lekto-difficulty-en.sqlite.gz"

        fun installer(
            derived: DerivedAssetStore = DerivedAssetStore(InMemoryVaultFileSystem()),
        ): Pair<DifficultyInstaller, FakeDifficultyFiles> {
            val files = FakeDifficultyFiles(derived, language)
            return DifficultyInstaller(
                derived = derived,
                downloader = files,
                databases = files,
                language = language,
            ) to files
        }

        test("an install downloads the table, reports it ready and opens it") {
            val (installed, files) = installer()

            val state = installed.install(url)

            state.shouldBeInstanceOf<DifficultyState.Ready>()
            installed.status().shouldBeInstanceOf<DifficultyState.Ready>()
            installed.open() shouldNotBe null
            files.downloads shouldBe 1
        }

        test("an uninstalled table reports not installed and cannot be opened") {
            val (installed, _) = installer()

            installed.status() shouldBe DifficultyState.NotInstalled
            installed.open() shouldBe null
        }

        test("a table of an unsupported format is refused, reported and removed") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (installed, files) = installer(derived)
            files.version = WordDifficulty.FORMAT_VERSION + 1

            installed.install(url) shouldBe
                DifficultyState.Incompatible(WordDifficulty.FORMAT_VERSION + 1, WordDifficulty.FORMAT_VERSION)
            installed.status() shouldBe
                DifficultyState.Incompatible(WordDifficulty.FORMAT_VERSION + 1, WordDifficulty.FORMAT_VERSION)
            // The refused file is gone, so a fresh installer sees nothing to refuse.
            installer(derived).first.status() shouldBe DifficultyState.NotInstalled
        }

        test("a corrupt table degrades to a corrupt state rather than throwing") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (installed, files) = installer(derived)
            files.openFailure = IllegalStateException("file is not a database")

            val state = installed.install(url)

            state.shouldBeInstanceOf<DifficultyState.Corrupt>()
            state.reason shouldBe "file is not a database"
            installer(derived).first.status() shouldBe DifficultyState.NotInstalled
        }

        test("a pre-existing incompatible table is refused and removed on inspection") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (installed, files) = installer(derived)
            derived.put(DifficultyInstaller.pathOf(language), byteArrayOf(1))
            files.version = WordDifficulty.FORMAT_VERSION + 1

            installed.status() shouldBe
                DifficultyState.Incompatible(WordDifficulty.FORMAT_VERSION + 1, WordDifficulty.FORMAT_VERSION)

            derived.paths() shouldBe emptyList()
            installed.open() shouldBe null
        }

        test("a pre-existing unreadable table is refused and removed on inspection") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (installed, files) = installer(derived)
            derived.put(DifficultyInstaller.pathOf(language), byteArrayOf(1))
            files.openFailure = IllegalStateException("file is not a database")

            installed.status() shouldBe DifficultyState.Corrupt("file is not a database")

            derived.paths() shouldBe emptyList()
            installed.open() shouldBe null
        }

        test("a failed download propagates and leaves nothing installed") {
            val (installed, files) = installer()
            files.failure = java.io.IOException("offline")

            assertFailsWith<java.io.IOException> { installed.install(url) }

            installed.status() shouldBe DifficultyState.NotInstalled
        }

        test("removing the table uninstalls it, and removing an absent one is harmless") {
            val (installed, _) = installer()
            installed.install(url)

            installed.remove() shouldBe DifficultyState.NotInstalled

            installed.status() shouldBe DifficultyState.NotInstalled
            installed.open() shouldBe null
        }

        test("closing the installer releases the open table") {
            val (installed, _) = installer()
            installed.install(url)

            installed.close()

            // The file is still installed, so a fresh installer opens it again.
            installed.status().shouldBeInstanceOf<DifficultyState.Ready>()
        }

        test("the installed table is a derived asset that never reaches the vault or its export") {
            val vault = JsonVaultStore(InMemoryVaultFileSystem())
            vault.put(testVaultRecord("a"))
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            val (installed, _) = installer(derived)

            installed.install(url)

            derived.paths() shouldBe listOf(DifficultyInstaller.pathOf(language))
            vault.exportBundle().decodeToString().contains("difficulty-table-bytes") shouldBe false
        }
    })
