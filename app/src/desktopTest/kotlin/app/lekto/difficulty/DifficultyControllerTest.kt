package app.lekto.difficulty

import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.testkit.FakeDifficultyFiles
import app.lekto.testkit.InMemoryVaultFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The difficulty table's state holder (ADR-0029): the download runs off the UI
 * thread and reports ready or a failure. The dispatcher is the test dispatcher,
 * so the asynchronous work is driven by virtual time rather than a wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DifficultyControllerTest {

    private fun services(): Pair<DifficultyServices, FakeDifficultyFiles> {
        val derived = DerivedAssetStore(InMemoryVaultFileSystem())
        val files = FakeDifficultyFiles(derived)
        val installer = DifficultyInstaller(
            derived = derived,
            downloader = files,
            databases = files,
            language = "en",
            url = "https://example.test/lekto-difficulty-en.sqlite.gz",
        )
        return DifficultyServices(installer) to files
    }

    @Test
    fun `an install downloads the table and reports it ready`() = runTest {
        val (services, files) = services()
        val controller = DifficultyController(services, StandardTestDispatcher(testScheduler), this)

        controller.install()
        advanceUntilIdle()

        assertTrue(controller.state.value.status is DifficultyState.Ready)
        assertEquals(1, files.downloads)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a failed download becomes a message and clears the installing flag`() = runTest {
        val (services, files) = services()
        files.failure = IOException("offline")
        val controller = DifficultyController(services, StandardTestDispatcher(testScheduler), this)

        controller.install()
        advanceUntilIdle()

        assertEquals("offline", controller.state.value.error)
        assertTrue(!controller.state.value.installing)
    }

    @Test
    fun `a dismissed error is gone`() = runTest {
        val (services, files) = services()
        files.failure = IOException("offline")
        val controller = DifficultyController(services, StandardTestDispatcher(testScheduler), this)
        controller.install()
        advanceUntilIdle()

        controller.dismissError()

        assertNull(controller.state.value.error)
    }

    @Test
    fun `a successful install notifies so the reader recolours`() = runTest {
        val (services, _) = services()
        val controller = DifficultyController(services, StandardTestDispatcher(testScheduler), this)
        var notified = false

        controller.install { notified = true }
        advanceUntilIdle()

        assertTrue(notified, "the mastery composition must recolour once the table is installed")
    }

    @Test
    fun `the installed table opens for the mastery composition`() = runTest {
        val (services, _) = services()
        val controller = DifficultyController(services, StandardTestDispatcher(testScheduler), this)

        controller.install()
        advanceUntilIdle()

        assertTrue(services.table() != null, "an installed table must open")
        assertNull(services.table()?.levelOf(WordKey("en", "blorple")))
    }
}
