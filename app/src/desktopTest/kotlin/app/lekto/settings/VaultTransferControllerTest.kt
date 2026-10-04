package app.lekto.settings

import app.lekto.PickedFile
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.testVaultRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The vault's export/import state holder (issue #20): it runs the whole-file
 * transfer off the UI thread, treats a cancelled file dialog as a no-op, and
 * turns a bad file into an honest message rather than a crash. The dispatcher is
 * the test dispatcher, so the asynchronous work runs on virtual time
 * (docs/testing.md, "Deterministic seams").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultTransferControllerTest {

    @Test
    fun `export writes the vault bundle through the save action`() = runTest {
        val vault = InMemoryVaultStore().apply { put(testVaultRecord("a")) }
        var savedName: String? = null
        var savedBytes: ByteArray? = null
        val controller = controller(vault, save = { name, bytes ->
            savedName = name
            savedBytes = bytes
            true
        })

        controller.export()
        advanceUntilIdle()

        val bytes = assertNotNull(savedBytes, "the export must hand the bundle to the save action")
        val bundle = VaultCodec.decodeBundle(bytes.decodeToString())
        assertEquals(listOf("a"), bundle.records.map { it.id })
        val name = assertNotNull(savedName)
        assertTrue(name.contains("vault"), "the suggested name must be recognisable: $name")
        assertNotNull(controller.state.value.message)
        assertNull(controller.state.value.error)
        assertTrue(!controller.state.value.transferring)
    }

    @Test
    fun `a cancelled export reports neither success nor an error`() = runTest {
        val controller = controller(InMemoryVaultStore(), save = { _, _ -> false })

        controller.export()
        advanceUntilIdle()

        assertNull(controller.state.value.message)
        assertNull(controller.state.value.error)
        assertTrue(!controller.state.value.transferring)
    }

    @Test
    fun `import restores the vault from the chosen file`() = runTest {
        val source = InMemoryVaultStore().apply {
            put(testVaultRecord("a"))
            put(testVaultRecord("b"))
        }
        val bundle = source.exportBundle()
        val target = InMemoryVaultStore()
        val controller = controller(target, open = { PickedFile("lekto-vault.json", bundle) })

        controller.import()
        advanceUntilIdle()

        assertEquals(setOf("a", "b"), target.all().map { it.id }.toSet())
        assertNotNull(controller.state.value.message)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a cancelled import leaves the vault untouched`() = runTest {
        val target = InMemoryVaultStore().apply { put(testVaultRecord("a")) }
        val controller = controller(target, open = { null })

        controller.import()
        advanceUntilIdle()

        assertEquals(listOf("a"), target.all().map { it.id })
        assertNull(controller.state.value.message)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a file that is not a vault becomes an honest error and touches nothing`() = runTest {
        val target = InMemoryVaultStore()
        val controller = controller(target, open = { PickedFile("notes.json", "not a vault".encodeToByteArray()) })

        controller.import()
        advanceUntilIdle()

        val error = assertNotNull(controller.state.value.error)
        assertTrue(error.contains("vault"), "the message must say it is not a vault: $error")
        assertTrue(target.all().isEmpty(), "a rejected file must not touch the vault")
    }

    @Test
    fun `a file that cannot be read reports the read failure`() = runTest {
        val controller = controller(InMemoryVaultStore(), open = { throw IllegalStateException("permission denied") })

        controller.import()
        advanceUntilIdle()

        assertEquals("permission denied", controller.state.value.error)
    }

    @Test
    fun `a vault from a newer format reports the unsupported version`() = runTest {
        val future = """{"formatVersion":99,"manifest":{"formatVersion":99,"records":[]},"records":[]}"""
        val controller = controller(
            InMemoryVaultStore(),
            open = { PickedFile("future.json", future.encodeToByteArray()) },
        )

        controller.import()
        advanceUntilIdle()

        val error = assertNotNull(controller.state.value.error)
        assertTrue(error.contains("99"), "the message must name the version it cannot read: $error")
    }

    @Test
    fun `a failed export becomes an error`() = runTest {
        val controller = controller(InMemoryVaultStore(), save = { _, _ -> throw IllegalStateException("disk full") })

        controller.export()
        advanceUntilIdle()

        assertEquals("disk full", controller.state.value.error)
        assertTrue(!controller.state.value.transferring)
    }

    @Test
    fun `dismiss clears the message and the error`() = runTest {
        val controller = controller(InMemoryVaultStore(), save = { _, _ -> true })
        controller.export()
        advanceUntilIdle()
        assertNotNull(controller.state.value.message)

        controller.dismiss()

        assertNull(controller.state.value.message)
        assertNull(controller.state.value.error)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        vault: VaultStore,
        save: suspend (String, ByteArray) -> Boolean = { _, _ -> true },
        open: suspend () -> PickedFile? = { null },
    ) = VaultTransferController(
        transfer = VaultTransfer(vault, save, open),
        dispatcher = StandardTestDispatcher(testScheduler),
        scope = this,
    )
}
