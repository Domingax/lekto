package app.lekto.guide

import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.dictionary.PackDownloader
import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.dictionary.DictionaryController
import app.lekto.dictionary.DictionaryServices
import app.lekto.difficulty.DifficultyController
import app.lekto.difficulty.DifficultyServices
import app.lekto.testkit.FakeDictionaryPackFiles
import app.lekto.testkit.FakeDifficultyFiles
import app.lekto.testkit.InMemoryLanguageCatalog
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.testOfferedArtifact
import app.lekto.testkit.testOfferedDictionary
import app.lekto.testkit.testOfferedLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The guide's download (issue #277): after the language answer it starts the
 * **Difficulty table** and the **Dictionary pack** the manifest publishes for the
 * chosen language, announces their combined size, reports the installation and
 * can be stopped. It reads the manifest only after the choice, so the language
 * list never waits on a fetch. The dispatcher is the test dispatcher, so the
 * work is driven by virtual time rather than a wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One behaviour, one test; splitting the class hides the controller's contract.
class GuideDownloadControllerTest {

    private val derived = DerivedAssetStore(InMemoryVaultFileSystem())
    private val difficultyServices = DifficultyServices { language ->
        val fake = FakeDifficultyFiles(derived, language)
        DifficultyInstaller(derived, fake, fake, language)
    }
    private val dictionaryServices = DictionaryServices { reading, translation ->
        val fake = FakeDictionaryPackFiles(derived, reading, translation)
        DictionaryPackInstaller(derived, fake, fake, reading, translation)
    }

    /** The guide's two installers, wired over the fakes, plus their controller. */
    private class Rig(
        val download: GuideDownloadController,
        val difficulty: DifficultyController,
        val dictionary: DictionaryController,
    )

    private fun TestScope.rig(
        catalog: LanguageCatalog,
        translation: String? = "fr",
    ): Rig {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val difficulty = DifficultyController(difficultyServices, dispatcher, this)
        val dictionary = DictionaryController(dictionaryServices, dispatcher, this)
        return Rig(
            GuideDownloadController(catalog, difficulty, dictionary, { translation }, dispatcher, this),
            difficulty,
            dictionary,
        )
    }

    private fun english(
        difficultySize: Long? = 12_000_000,
        dictionarySize: Long? = 24_000_000,
        translation: String = "fr",
    ): OfferedLanguage = testOfferedLanguage(
        "en",
        difficulty = testOfferedArtifact("lekto-difficulty-en.sqlite.gz", sizeBytes = difficultySize),
        dictionaries = listOf(
            testOfferedDictionary(translation, "lekto-dictionary-en-$translation.sqlite.gz", sizeBytes = dictionarySize),
        ),
    )

    private fun catalog(language: OfferedLanguage = english()): LanguageCatalog =
        InMemoryLanguageCatalog(LanguageCatalogState.Available(listOf(language)))

    @Test
    fun `the choice starts both downloads and names their combined size`() = runTest {
        val rig = rig(catalog())

        rig.download.start("en")
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.INSTALLED, rig.download.state.phase)
        assertEquals(36_000_000L, rig.download.state.sizeBytes)
        assertEquals(listOf("lekto-difficulty-en.sqlite.gz", "lekto-dictionary-en-fr.sqlite.gz"), rig.download.state.artifacts.map { it.name })
        assertTrue(rig.difficulty.states.value.getValue("en").status is app.lekto.core.difficulty.DifficultyState.Ready)
        assertTrue(rig.dictionary.states.value.getValue("lekto-dictionary-en-fr.sqlite.gz").status is app.lekto.core.dictionary.DictionaryPackState.Ready)
    }

    @Test
    fun `the pack is the reader's translation language when the manifest publishes it`() = runTest {
        val language = testOfferedLanguage(
            "en",
            difficulty = testOfferedArtifact("lekto-difficulty-en.sqlite.gz", sizeBytes = 12_000_000),
            dictionaries = listOf(
                testOfferedDictionary("es", "lekto-dictionary-en-es.sqlite.gz", sizeBytes = 1_000_000),
                testOfferedDictionary("fr", "lekto-dictionary-en-fr.sqlite.gz", sizeBytes = 2_000_000),
            ),
        )
        val rig = rig(catalog(language), translation = "fr")

        rig.download.start("en")
        advanceUntilIdle()

        assertEquals(
            "lekto-dictionary-en-fr.sqlite.gz",
            rig.download.state.artifacts.last().name,
            "the reader's own translation language wins over the manifest's order",
        )
        assertEquals(14_000_000L, rig.download.state.sizeBytes)
    }

    @Test
    fun `a language the manifest does not offer downloads nothing and says so`() = runTest {
        val rig = rig(InMemoryLanguageCatalog(LanguageCatalogState.Available(listOf(testOfferedLanguage("fr")))))

        rig.download.start("en")
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.UNAVAILABLE, rig.download.state.phase)
        assertNull(rig.download.state.sizeBytes)
    }

    @Test
    fun `no readable manifest leaves the demonstration untouched and says so`() = runTest {
        val rig = rig(InMemoryLanguageCatalog(LanguageCatalogState.Unavailable))

        rig.download.start("en")
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.UNAVAILABLE, rig.download.state.phase)
        assertTrue(rig.difficulty.states.value.isEmpty(), "nothing was started")
    }

    @Test
    fun `a stopped download never finishes and the line follows the stop`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val databases = FakeDifficultyFiles(derived, "en")
        val services = DifficultyServices { language ->
            DifficultyInstaller(
                derived = derived,
                downloader = PackDownloader { _, _, _ ->
                    started.countDown()
                    release.await()
                },
                databases = databases,
                language = language,
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val difficulty = DifficultyController(services, Dispatchers.IO, scope)
        val dictionary = DictionaryController(dictionaryServices, Dispatchers.IO, scope)
        val download = GuideDownloadController(catalog(), difficulty, dictionary, { "fr" }, Dispatchers.IO, scope)

        download.start("en")
        assertTrue(started.await(5, TimeUnit.SECONDS), "the download did not start")
        download.stop()
        release.countDown()

        assertEquals(GuideDownloadPhase.STOPPED, download.state.phase)
        assertFalse(difficulty.states.value.getValue("en").installing)
        assertFalse(dictionary.states.value.getValue("lekto-dictionary-en-fr.sqlite.gz").installing)
        scope.cancel()
    }
}
