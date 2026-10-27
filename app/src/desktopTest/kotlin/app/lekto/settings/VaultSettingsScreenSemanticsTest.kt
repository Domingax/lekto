package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.sync.SyncSettings
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The **Vault and sync** screen's behaviour through semantics (issues #20, #28,
 * #213; docs/design.md §7.5, §7.6): the vault's export and import, the WebDAV
 * endpoint, the masked application password, testing, turning sync on and off,
 * the manual run, the last outcome and disconnect. The screen was extracted from
 * Settings, so these are the two sections' own tests moved with it, still driven
 * through the existing actions.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per control; splitting hides the section.
class VaultSettingsScreenSemanticsTest {

    @Test
    fun theScreenNamesItselfAndGoesBack() = runComposeUiTest {
        var wentBack = false
        vault(onBack = { wentBack = true })

        onNodeWithTag(VAULT_SETTINGS_TITLE_TAG).assertIsDisplayed()
        onNodeWithTag(VAULT_BACK_TAG).performClick()

        assertTrue(wentBack)
    }

    @Test
    fun theVaultOffersExportAndImport() = runComposeUiTest {
        var exported = false
        var imported = false
        vault(
            vault = VaultUiState(available = true, message = AppMessage.core("Vault exported.")),
            actions = SettingsActions(onExportVault = { exported = true }, onImportVault = { imported = true }),
        )

        onNodeWithText("Vault exported.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Export vault").performScrollTo().performClick()
        onNodeWithText("Import vault").performScrollTo().performClick()

        assertTrue(exported)
        assertTrue(imported)
    }

    @Test
    fun anUnwiredVaultTransferSaysSoAndDisablesItsActions() = runComposeUiTest {
        vault(vault = VaultUiState(available = false))

        onNodeWithText("Vault export and import aren't available", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Import vault").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aVaultErrorRendersAndDismisses() = runComposeUiTest {
        var dismissed = false
        vault(
            vault = VaultUiState(available = true, error = AppMessage.core("That file isn't a Lekto vault.")),
            actions = SettingsActions(onDismissVaultMessage = { dismissed = true }),
        )

        onNodeWithText("That file isn't a Lekto vault.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Dismiss").performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun exportingInProgressDisablesTheActions() = runComposeUiTest {
        vault(vault = VaultUiState(available = true, transferring = true))

        onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Import vault").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun syncAlwaysWarnsAgainstFolderSyncTools() = runComposeUiTest {
        vault()

        onNodeWithText("folder-sync tool", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("corrupt", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anUnwiredSyncSaysSoAndHidesItsControls() = runComposeUiTest {
        vault(sync = SyncUiState(available = false))

        onNodeWithText("Sync isn't available", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SYNC_HOST_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun aConfiguredSyncShowsItsStatusAndEndpoint() = runComposeUiTest {
        vault(
            sync = SyncUiState(
                available = true,
                config = SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
            ),
        )

        onNodeWithText("Sync is off.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(SYNC_HOST_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theServerAndUserWriteOnChange() = runComposeUiTest {
        var host: String? = null
        var username: String? = null
        vault(
            sync = SyncUiState(available = true),
            actions = SettingsActions(
                onSyncHostChange = { typed -> host = typed },
                onSyncUsernameChange = { typed -> username = typed },
            ),
        )

        onNodeWithTag(SYNC_HOST_FIELD_TAG).performScrollTo().performTextInput("https://cloud.example.test/dav")
        assertEquals("https://cloud.example.test/dav", host)

        onNodeWithTag(SYNC_USERNAME_FIELD_TAG).performScrollTo().performTextInput("reader")
        assertEquals("reader", username)
    }

    @Test
    fun anEnabledSyncSaysSyncIsOn() = runComposeUiTest {
        vault(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                hasStoredPassword = true,
            ),
        )

        onNodeWithText("Sync is on.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun connectingPassesTheTypedPasswordToTheController() = runComposeUiTest {
        var connected: String? = null
        vault(
            sync = SyncUiState(available = true),
            actions = SettingsActions(onConnectSync = { password -> connected = password }),
        )

        onNodeWithTag(SYNC_PASSWORD_FIELD_TAG).performScrollTo().performTextInput("app-password")
        onNodeWithTag(SYNC_CONNECT_TAG).performScrollTo().performClick()

        assertEquals("app-password", connected)
    }

    @Test
    fun aStoredPasswordShowsAMaskedPlaceholder() = runComposeUiTest {
        vault(sync = SyncUiState(available = true, hasStoredPassword = true))

        onNodeWithText(SECRET_PLACEHOLDER).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLastRunTimeIsShown() = runComposeUiTest {
        val now = Instant.fromEpochMilliseconds(FIXED_NOW_MILLIS)
        vault(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                hasStoredPassword = true,
                lastRunAt = now.toEpochMilliseconds() - TWO_MINUTES_MILLIS,
            ),
            now = now,
        )

        onNodeWithText("Synced 2 minutes ago.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLastSyncOutcomeIsShown() = runComposeUiTest {
        vault(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                lastSync = SyncResult.Success(AppMessage.core("Synced: uploaded 1.")),
            ),
        )

        onNodeWithText("Synced: uploaded 1.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun syncNowIsOfferedOnlyWhenSyncIsOn() = runComposeUiTest {
        vault(sync = SyncUiState(available = true, config = configured()))

        onNodeWithTag(SYNC_NOW_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun syncNowRaisesTheActionWhenSyncIsOn() = runComposeUiTest {
        var synced = false
        vault(
            sync = SyncUiState(available = true, config = configured(enabled = true)),
            actions = SettingsActions(onSyncNow = { synced = true }),
        )

        onNodeWithTag(SYNC_NOW_TAG).performScrollTo().performClick()

        assertTrue(synced)
    }

    @Test
    fun aFailedSyncIsShownInPlainLanguage() = runComposeUiTest {
        vault(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                lastSync = SyncResult.Failure(
                    AppMessage.core("The last sync didn't finish: the server is unreachable"),
                ),
            ),
        )

        onNodeWithText("The last sync didn't finish", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSyncResultRendersAndDismisses() = runComposeUiTest {
        var dismissed = false
        vault(
            sync = SyncUiState(available = true, result = SyncResult.Success(AppMessage.core("Saved."))),
            actions = SettingsActions(onDismissSyncResult = { dismissed = true }),
        )

        onNodeWithText("Saved.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(SYNC_DISMISS_TAG).performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun disconnectingRaisesTheAction() = runComposeUiTest {
        var disconnected = false
        vault(
            sync = SyncUiState(available = true, config = configured()),
            actions = SettingsActions(onDisconnectSync = { disconnected = true }),
        )

        onNodeWithTag(SYNC_DISCONNECT_TAG).performScrollTo().performClick()

        assertTrue(disconnected)
    }

    @Test
    fun syncingInProgressDisablesTheControls() = runComposeUiTest {
        vault(sync = SyncUiState(available = true, config = configured(enabled = true), syncing = true))

        onNodeWithTag(SYNC_CONNECT_TAG).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(SYNC_NOW_TAG).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(SYNC_DISCONNECT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theScreenRendersInTheAppLanguage() = runComposeUiTest {
        vault(vault = VaultUiState(available = true), texts = appTextsFor("fr"))

        onNodeWithText("Coffre et synchronisation").assertIsDisplayed()
        onNodeWithText("Exporter le coffre").performScrollTo().assertIsDisplayed()
        onNodeWithText("Importer un coffre").performScrollTo().assertIsDisplayed()
    }

    /** Renders the vault screen in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun ComposeUiTest.vault(
        vault: VaultUiState = VaultUiState(),
        sync: SyncUiState = SyncUiState(),
        actions: SettingsActions = SettingsActions(),
        onBack: () -> Unit = {},
        texts: AppTexts? = null,
        now: Instant = Instant.fromEpochMilliseconds(FIXED_NOW_MILLIS),
    ) {
        val screen: @Composable () -> Unit = {
            VaultSettingsScreen(
                vault = vault,
                sync = sync,
                actions = actions,
                onBack = onBack,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
                now = now,
            )
        }
        setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}

private const val FIXED_NOW_MILLIS = 1_700_000_000_000L
private const val TWO_MINUTES_MILLIS = 2 * 60 * 1000L

/** A usable endpoint with the password stored, for the sync tests. */
private fun configured(enabled: Boolean = false): SyncSettings =
    SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = enabled)
