package app.lekto

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * The vault's export/import through the whole app on a **simulated Android
 * runtime** (issue #79): the same-named twin of `app/desktopTest`'s
 * `AppVaultSemanticsTest`, so the parity rule (issue #73) sees the two lanes
 * together. Settings exports the vault to the platform's save action, an import
 * restores the vault and refreshes the library that reads it, and a build with
 * no file picker says so rather than offering a dead action. The library is the
 * real vault-backed one, so the test proves the restored record is what the user
 * sees, on the runtime Android uses. Kept apart from [AppSemanticsTest] so each
 * suite stays a readable size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AppVaultSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exportsTheWholeVaultFromSettings() {
        var exported: ByteArray? = null
        val vault = InMemoryVaultStore().apply { put(testVaultRecord("a")) }
        val transfer = VaultTransfer(vault, save = { _, bytes ->
            exported = bytes
            true
        }, open = { null })
        compose.setContent { App(environment(libraryOver(vault), vaultTransfer = transfer)) }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Export vault").performScrollTo().performClick()

        compose.waitUntil { exported != null }
        val bundle = VaultCodec.decodeBundle(checkNotNull(exported).decodeToString())
        assertEquals(listOf("a"), bundle.records.map { it.id })
        compose.onNodeWithText("Vault exported", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun importsAVaultFromSettingsAndRefreshesTheLibrary() {
        val vault = InMemoryVaultStore()
        val source = InMemoryVaultStore().apply { put(bookRecord("restored", "The Restored Book")) }
        val transfer = VaultTransfer(
            vault,
            save = { _, _ -> true },
            open = { PickedFile("lekto-vault.json", source.exportBundle()) },
        )
        compose.setContent { App(environment(libraryOver(vault), vaultTransfer = transfer)) }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Import vault").performScrollTo().performClick()
        compose.waitUntil { vault.get("restored") != null }

        compose.onNodeWithText("Vault imported", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Library").performClick()

        compose.waitUntil { compose.onAllNodesWithText("The Restored Book").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("The Restored Book").assertIsDisplayed()
    }

    @Test
    fun settingsOfferNoVaultTransferWhenNoneIsWired() {
        compose.setContent { App(environment(libraryOver(InMemoryVaultStore()))) }

        compose.onNodeWithText("Settings").performClick()

        compose.onNodeWithText("Vault").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("aren't available", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
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
