package app.lekto.difficulty

import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.testkit.FakeDifficultyFiles
import app.lekto.testkit.InMemoryVaultFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The difficulty table's per-language state holder (ADR-0029; issue #148): each
 * offered language's state is read from its own installer, and a download runs
 * off the UI thread, reports ready or a failure, and recolours the reader on
 * success. The dispatcher is the test dispatcher, so the asynchronous work is
 * driven by virtual time rather than a wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One behaviour, one test; splitting the class hides the controller's contract.
class DifficultyControllerTest {

    private val derived = DerivedAssetStore(InMemoryVaultFileSystem())
    private val files = mutableMapOf<String, FakeDifficultyFiles>()
    private val services = DifficultyServices { language ->
        val fake = FakeDifficultyFiles(derived, language)
        files[language] = fake
        DifficultyInstaller(
            derived = derived,
            downloader = fake,
            databases = fake,
            language = language,
        )
    }

    /** The fake behind [language]'s installer, building it first so its flags can be set. */
    private fun file(language: String): FakeDifficultyFiles {
        services.installer(language)
        return files.getValue(language)
    }

    private fun TestScope.controller(): DifficultyController =
        DifficultyController(services, StandardTestDispatcher(testScheduler), this)

    @Test
    fun `refresh reads each offered language's installed state`() = runTest {
        val controller = controller()
        controller.install("en", artifact("en"))
        advanceUntilIdle()

        controller.refresh(listOf("en", "fr"))

        assertTrue(controller.states.value.getValue("en").status is DifficultyState.Ready)
        assertEquals(DifficultyState.NotInstalled, controller.states.value.getValue("fr").status)
    }

    @Test
    fun `an install downloads that language's table and reports it ready`() = runTest {
        val controller = controller()

        controller.install("en", artifact("en"))
        advanceUntilIdle()

        assertTrue(controller.states.value.getValue("en").status is DifficultyState.Ready)
        assertEquals(1, files.getValue("en").downloads)
        assertNull(controller.states.value.getValue("en").error)
    }

    @Test
    fun `installing one language leaves another untouched`() = runTest {
        val controller = controller()
        controller.refresh(listOf("en", "fr"))

        controller.install("en", artifact("en"))
        advanceUntilIdle()

        assertEquals(DifficultyState.NotInstalled, controller.states.value.getValue("fr").status)
        assertEquals(0, file("fr").downloads, "no download for a language that was not installed")
    }

    @Test
    fun `a failed download becomes a message and clears the installing flag`() = runTest {
        file("en").failure = IOException("offline")
        val controller = controller()

        controller.install("en", artifact("en"))
        advanceUntilIdle()

        assertEquals("offline", controller.states.value.getValue("en").error)
        assertTrue(!controller.states.value.getValue("en").installing)
    }

    @Test
    fun `a dismissed error is gone for that language only`() = runTest {
        file("en").failure = IOException("offline")
        file("fr").failure = IOException("down")
        val controller = controller()
        controller.install("en", artifact("en"))
        controller.install("fr", artifact("fr"))
        advanceUntilIdle()

        controller.dismissError("en")

        assertNull(controller.states.value.getValue("en").error)
        assertEquals("down", controller.states.value.getValue("fr").error)
    }

    @Test
    fun `a successful install notifies so the reader recolours`() = runTest {
        val controller = controller()
        var notified = false

        controller.install("en", artifact("en")) { notified = true }
        advanceUntilIdle()

        assertTrue(notified, "the mastery composition must recolour once the table is installed")
    }

    @Test
    fun `a failed install does not notify`() = runTest {
        file("en").failure = IOException("offline")
        val controller = controller()
        var notified = false

        controller.install("en", artifact("en")) { notified = true }
        advanceUntilIdle()

        assertTrue(!notified, "a failed download changed nothing, so nothing recolours")
    }

    @Test
    fun `an install already in progress is not started again`() = runTest {
        val controller = controller()

        controller.install("en", artifact("en"))
        controller.install("en", artifact("en"))
        advanceUntilIdle()

        assertEquals(1, files.getValue("en").downloads)
    }

    @Test
    fun `the installed table opens for the mastery composition`() = runTest {
        val controller = controller()

        controller.install("en", artifact("en"))
        advanceUntilIdle()

        assertTrue(services.table("en") != null, "an installed table must open")
        assertNull(services.table("en")?.levelOf(WordKey("en", "blorple")))
        assertNull(services.table("fr"), "a language with no table open reads nothing")
    }

    /** The asset name the manifest publishes a language's table under. */
    private fun artifact(language: String): String = DifficultyRelease.artifact(language)
}
