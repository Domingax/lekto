package app.lekto.dictionary

import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.testkit.FakeDictionaryPackFiles
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
 * The dictionary's state holder (issue #18): the download runs off the UI thread
 * and reports ready or a failure, and a lookup degrades honestly with no pack.
 * The dispatcher is the test dispatcher, so the asynchronous work is driven by
 * virtual time rather than a wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryControllerTest {

    private fun services(): Pair<DictionaryServices, FakeDictionaryPackFiles> {
        val derived = DerivedAssetStore(InMemoryVaultFileSystem())
        val files = FakeDictionaryPackFiles(derived)
        val installer = DictionaryPackInstaller(derived, files, files, "https://example.test/pack.sqlite.gz")
        return DictionaryServices(installer) to files
    }

    @Test
    fun `an install downloads the pack and reports it ready`() = runTest {
        val (services, files) = services()
        val controller = DictionaryController(services, StandardTestDispatcher(testScheduler), this)

        controller.install()
        advanceUntilIdle()

        assertTrue(controller.state.value.status is DictionaryPackState.Ready)
        assertEquals(1, files.downloads)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a failed download becomes a message and clears the installing flag`() = runTest {
        val (services, files) = services()
        files.failure = IOException("offline")
        val controller = DictionaryController(services, StandardTestDispatcher(testScheduler), this)

        controller.install()
        advanceUntilIdle()

        assertEquals("offline", controller.state.value.error)
        assertTrue(!controller.state.value.installing)
    }

    @Test
    fun `a dismissed error is gone`() = runTest {
        val (services, files) = services()
        files.failure = IOException("offline")
        val controller = DictionaryController(services, StandardTestDispatcher(testScheduler), this)
        controller.install()
        advanceUntilIdle()

        controller.dismissError()

        assertNull(controller.state.value.error)
    }

    @Test
    fun `a lookup with no pack degrades to unavailable`() = runTest {
        val (services, _) = services()
        val controller = DictionaryController(services, StandardTestDispatcher(testScheduler), this)

        assertTrue(controller.lookUp("blorple", "en") is WordLookup.Unavailable)
    }

    @Test
    fun `a lookup in an installed pack that does not know the word reports it`() = runTest {
        val (services, _) = services()
        val controller = DictionaryController(services, StandardTestDispatcher(testScheduler), this)
        controller.install()
        advanceUntilIdle()

        assertTrue(controller.lookUp("blorple", "en") is WordLookup.NotInDictionary)
    }
}
