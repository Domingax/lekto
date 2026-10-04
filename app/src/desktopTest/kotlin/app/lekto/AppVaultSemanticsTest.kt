package app.lekto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.BookRecord
import app.lekto.core.book.VaultBookLibrary
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultRecord
import app.lekto.core.vault.VaultStore
import app.lekto.settings.VaultTransfer
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import app.lekto.testkit.testVaultRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * The vault's export/import through the whole app (issue #20): settings exports
 * the vault to the platform's save action, an import restores the vault and
 * refreshes the library that reads it, and a build with no file picker says so
 * rather than offering a dead action. The library is the real vault-backed one,
 * so the test proves the restored record is what the user sees. Kept apart from
 * [AppSemanticsTest] so each suite stays a readable size.
 */
@OptIn(ExperimentalTestApi::class)
class AppVaultSemanticsTest {

    @Test
    fun exportsTheWholeVaultFromSettings() = runComposeUiTest {
        var exported: ByteArray? = null
        val vault = InMemoryVaultStore().apply { put(testVaultRecord("a")) }
        val transfer = VaultTransfer(vault, save = { _, bytes ->
            exported = bytes
            true
        }, open = { null })
        setContent { App(environment(libraryOver(vault), vaultTransfer = transfer)) }

        onNodeWithText("Settings").performClick()
        onNodeWithText("Export vault").performClick()

        waitUntil { exported != null }
        val bundle = VaultCodec.decodeBundle(assertNotNull(exported).decodeToString())
        assertEquals(listOf("a"), bundle.records.map { it.id })
        onNodeWithText("Vault exported", substring = true).assertIsDisplayed()
    }

    @Test
    fun importsAVaultFromSettingsAndRefreshesTheLibrary() = runComposeUiTest {
        val vault = InMemoryVaultStore()
        val source = InMemoryVaultStore().apply { put(bookRecord("restored", "The Restored Book")) }
        val transfer = VaultTransfer(
            vault,
            save = { _, _ -> true },
            open = { PickedFile("lekto-vault.json", source.exportBundle()) },
        )
        setContent { App(environment(libraryOver(vault), vaultTransfer = transfer)) }

        onNodeWithText("Settings").performClick()
        onNodeWithText("Import vault").performClick()
        waitUntil { vault.get("restored") != null }

        onNodeWithText("Vault imported", substring = true).assertIsDisplayed()
        onNodeWithText("Library").performClick()

        waitUntil { onAllNodesWithText("The Restored Book").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("The Restored Book").assertIsDisplayed()
    }

    @Test
    fun settingsOfferNoVaultTransferWhenNoneIsWired() = runComposeUiTest {
        setContent { App(environment(libraryOver(InMemoryVaultStore()))) }

        onNodeWithText("Settings").performClick()

        onNodeWithText("Vault").assertIsDisplayed()
        onNodeWithText("aren't available", substring = true).assertIsDisplayed()
        onNodeWithText("Export vault").assertIsNotEnabled()
    }
}

/** The real vault-backed library over [vault]; no parsers, because these tests only list books. */
private fun libraryOver(vault: VaultStore): BookLibrary = VaultBookLibrary(
    vault = vault,
    derived = DerivedAssetStore(InMemoryVaultFileSystem()),
    parsers = emptyMap(),
    seams = deterministicSeams(),
    deviceId = DeviceId("test-device"),
)

/** A book record the way an import stores it, so the library can list it. */
private fun bookRecord(id: String, title: String): VaultRecord = BookRecord.of(
    Book(id, title, "en", BookFormat.TXT, "$id.txt"),
    Instant.fromEpochMilliseconds(0),
    DeviceId("test-device"),
)
