package app.lekto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import app.lekto.navigation.ShellSection
import app.lekto.navigation.shellSectionTag
import app.lekto.settings.VAULT_LINK_TAG
import app.lekto.settings.VaultTransfer
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import app.lekto.testkit.testVaultRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * The budget for a wait that follows work on the app's injected dispatcher.
 * That work crosses a real thread, so the wait has to outlast a busy machine
 * rather than the one-second Compose default (issue #194).
 */
private const val AWAIT_DISPATCHED_WORK_MILLIS: Long = 10_000

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

        openVault()
        onNodeWithText("Export vault").performScrollTo().performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { exported != null }
        val bundle = VaultCodec.decodeBundle(assertNotNull(exported).decodeToString())
        assertEquals(listOf("a"), bundle.records.map { it.id })
        onNodeWithText("Vault exported", substring = true).performScrollTo().assertIsDisplayed()
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

        openVault()
        onNodeWithText("Import vault").performScrollTo().performClick()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { vault.get("restored") != null }

        onNodeWithText("Vault imported", substring = true).performScrollTo().assertIsDisplayed()
        // The shell's Library item is outside Settings' own scroll, so it is
        // reachable however far down the reader scrolled (issue #185).
        onNodeWithTag(shellSectionTag(ShellSection.LIBRARY)).performClick()

        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("The Restored Book").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("The Restored Book").assertIsDisplayed()
    }

    @Test
    fun settingsOfferNoVaultTransferWhenNoneIsWired() = runComposeUiTest {
        setContent { App(environment(libraryOver(InMemoryVaultStore()))) }

        onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).performClick()
        onNodeWithTag(VAULT_LINK_TAG).performScrollTo().performClick()

        onNodeWithText("Vault and sync").assertIsDisplayed()
        onNodeWithText("aren't available", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
    }
}

/** Reaches the **Vault and sync** screen from Settings and its doorway (issue #213). */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.openVault() {
    onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).performClick()
    onNodeWithTag(VAULT_LINK_TAG).performScrollTo().performClick()
}

/** The real vault-backed library over [vault]; no parsers, because these tests only list books. */
private fun libraryOver(vault: VaultStore): BookLibrary = VaultBookLibrary(
    vault = vault,
    derived = DerivedAssetStore(InMemoryVaultFileSystem()),
    parsers = emptyMap(),
    tombstones = InMemoryTombstoneStore(),
    seams = deterministicSeams(),
    deviceId = DeviceId("test-device"),
)

/** A book record the way an import stores it, so the library can list it. */
private fun bookRecord(id: String, title: String): VaultRecord = BookRecord.of(
    Book(id, title, "en", BookFormat.TXT, "$id.txt"),
    Instant.fromEpochMilliseconds(0),
    DeviceId("test-device"),
)
