package app.lekto.guide

import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.dictionary.PackDownloader
import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.language.defaultTranslationLanguage
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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
@Suppress("TooManyFunctions", "MagicNumber") // One behaviour, one test; the sizes and waits are the test's parameters.
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

    @Suppress("LongParameterList") // The test drives the manifest, its two services and the reader's language.
    private fun TestScope.rig(
        catalog: LanguageCatalog,
        translation: () -> String? = { "fr" },
        difficulty: DifficultyServices = difficultyServices,
        dictionary: DictionaryServices = dictionaryServices,
        onOffered: (List<OfferedLanguage>) -> Unit = {},
    ): Rig {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val difficultyController = DifficultyController(difficulty, dispatcher, this)
        val dictionaryController = DictionaryController(dictionary, dispatcher, this)
        return Rig(
            GuideDownloadController(
                catalog = catalog,
                difficulty = difficultyController,
                dictionary = dictionaryController,
                translationLanguage = translation,
                onOffered = onOffered,
                dispatcher = dispatcher,
                scope = this,
            ),
            difficultyController,
            dictionaryController,
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
            testOfferedDictionary(
                translation,
                "lekto-dictionary-en-$translation.sqlite.gz",
                sizeBytes = dictionarySize,
            ),
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
        assertEquals(
            listOf("lekto-difficulty-en.sqlite.gz", "lekto-dictionary-en-fr.sqlite.gz"),
            rig.download.state.artifacts.map { it.name },
        )
        assertTrue(rig.difficulty.states.value.getValue("en").status is DifficultyState.Ready)
        assertTrue(
            rig.dictionary.states.value.getValue("lekto-dictionary-en-fr.sqlite.gz").status is
                DictionaryPackState.Ready,
        )
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
        val rig = rig(catalog(language), translation = { "fr" })

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
    fun `a language the manifest does not publish says so and offers no retry`() = runTest {
        val catalog =
            InMemoryLanguageCatalog(LanguageCatalogState.Available(listOf(testOfferedLanguage("fr"))))
        val rig = rig(catalog)

        rig.download.start("en")
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.LANGUAGE_NOT_OFFERED, rig.download.state.phase)
        assertNull(rig.download.state.sizeBytes)

        rig.download.retry()
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.LANGUAGE_NOT_OFFERED, rig.download.state.phase, "there is no dead retry")
        assertEquals(0, catalog.reloads, "the cached manifest was read once and no fetch was attempted")
    }

    @Test
    fun `a manifest this build refuses says so and offers no retry`() = runTest {
        val catalog = InMemoryLanguageCatalog(LanguageCatalogState.Unavailable)
        val rig = rig(catalog)

        rig.download.start("en")
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.REFUSED, rig.download.state.phase)
        assertTrue(rig.difficulty.states.value.isEmpty(), "nothing was started")

        rig.download.retry()
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.REFUSED, rig.download.state.phase, "a refused handshake is not retried")
        assertEquals(1, catalog.reloads)
    }

    @Test
    fun `a fetch that throws is the connection case and retries`() = runTest {
        val catalog = InMemoryLanguageCatalog(LanguageCatalogState.Unavailable)
        catalog.failure = IllegalStateException("offline")
        val rig = rig(catalog)

        rig.download.start("en")
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.UNAVAILABLE, rig.download.state.phase)

        catalog.failure = null
        catalog.onReload = LanguageCatalogState.Available(listOf(english()))
        rig.download.retry()
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.INSTALLED, rig.download.state.phase)
    }

    @Test
    fun anUnreachableManifestRetriesIntoBothInstalls() = runTest {
        val catalog = InMemoryLanguageCatalog(LanguageCatalogState.Unavailable)
        catalog.failure = IllegalStateException("offline")
        val rig = rig(catalog)

        rig.download.start("en")
        advanceUntilIdle()
        assertEquals(GuideDownloadPhase.UNAVAILABLE, rig.download.state.phase)

        catalog.failure = null
        catalog.onReload = LanguageCatalogState.Available(listOf(english()))
        rig.download.retry()
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.INSTALLED, rig.download.state.phase)
        assertTrue(rig.difficulty.states.value.getValue("en").status is DifficultyState.Ready)
        assertTrue(
            rig.dictionary.states.value.getValue("lekto-dictionary-en-fr.sqlite.gz").status is
                DictionaryPackState.Ready,
        )
    }

    @Test
    fun aFailedArtefactRetriesOnceTheFailureIsGone() = runTest {
        val tables = FakeDifficultyFiles(derived, "en")
        tables.failure = IllegalStateException("the network dropped")
        val services = DifficultyServices { language -> DifficultyInstaller(derived, tables, tables, language) }
        val rig = rig(catalog(), difficulty = services)

        rig.download.start("en")
        advanceUntilIdle()
        assertEquals(GuideDownloadPhase.FAILED, rig.download.state.phase)

        tables.failure = null
        rig.download.retry()
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.INSTALLED, rig.download.state.phase)
        assertTrue(rig.difficulty.states.value.getValue("en").status is DifficultyState.Ready)
    }

    @Test
    fun aStoppedDownloadResumesThroughTheSamePreparation() = runTest {
        val rig = rig(catalog())

        rig.download.start("en")
        rig.download.stop()
        assertEquals(GuideDownloadPhase.STOPPED, rig.download.state.phase)

        rig.download.resume()
        advanceUntilIdle()

        assertEquals(GuideDownloadPhase.INSTALLED, rig.download.state.phase)
    }

    @Test
    fun theGuideFillsTheTranslationDefaultFromTheManifestBeforeChoosingThePack() = runTest {
        val language = testOfferedLanguage(
            "en",
            difficulty = testOfferedArtifact("lekto-difficulty-en.sqlite.gz"),
            dictionaries = listOf(
                testOfferedDictionary("es", "lekto-dictionary-en-es.sqlite.gz"),
                testOfferedDictionary("fr", "lekto-dictionary-en-fr.sqlite.gz"),
            ),
        )
        var translation: String? = null
        val rig = rig(
            catalog(language),
            translation = { translation },
            onOffered = { offered -> translation = defaultTranslationLanguage("fr", offered) },
        )

        rig.download.start("en")
        advanceUntilIdle()

        assertEquals("fr", translation, "the guide fills the reader's own language from the manifest")
        assertEquals(
            "lekto-dictionary-en-fr.sqlite.gz",
            rig.download.state.artifacts.last().name,
            "the pack follows the filled default, not the manifest's order",
        )
    }

    @Suppress("InjectDispatcher") // Two real threads, because the point is which one the work lands on.
    @Test
    fun `the manifest is read on the injected dispatcher, not the composition scope`() = runBlocking {
        // Android forbids blocking network on the main thread. The composition's
        // scope is the UI dispatcher, so the blocking read must hop to the
        // dispatcher the controller was built with.
        val ui = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "guide-ui-scope") }
            .asCoroutineDispatcher()
        val work = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "guide-download-work") }
            .asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + ui)
        val read = CountDownLatch(1)
        var readingThread: String? = null
        val catalog = InMemoryLanguageCatalog(LanguageCatalogState.Unavailable).apply {
            onReload = LanguageCatalogState.Available(listOf(english()))
            whileInFlight = {
                readingThread = Thread.currentThread().name
                read.countDown()
            }
        }
        val difficulty = DifficultyController(difficultyServices, work, scope)
        val dictionary = DictionaryController(dictionaryServices, work, scope)
        val download = GuideDownloadController(
            catalog = catalog,
            difficulty = difficulty,
            dictionary = dictionary,
            translationLanguage = { "fr" },
            dispatcher = work,
            scope = scope,
        )
        try {
            download.start("en")

            assertTrue(read.await(5, TimeUnit.SECONDS), "the manifest was never read")
            assertTrue(
                readingThread?.startsWith("guide-download-work") == true,
                "the blocking manifest read must not run on the composition's (UI) thread: $readingThread",
            )
        } finally {
            scope.cancel()
            ui.close()
            work.close()
        }
    }

    @Suppress("InjectDispatcher") // The blocking download must run on a real thread, not the test's virtual one.
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
        val download = GuideDownloadController(
            catalog = catalog(),
            difficulty = difficulty,
            dictionary = dictionary,
            translationLanguage = { "fr" },
            dispatcher = Dispatchers.IO,
            scope = scope,
        )

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
