package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lekto.core.sync.SyncSettings
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * The **Vault and sync** screen on a **simulated Android runtime** (issue #213):
 * the same-named twin of `app/desktopTest`'s `VaultSettingsScreenSemanticsTest`,
 * so the parity rule (issue #73) sees the two lanes together. The vault's export
 * and import, the WebDAV endpoint, the masked password, testing, enabling,
 * running, the last outcome and disconnecting are the same behaviours on the
 * runtime the app ships on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per control; splitting hides the section.
class VaultSettingsScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theScreenNamesItselfAndGoesBack() {
        var wentBack = false
        render(onBack = { wentBack = true })

        compose.onNodeWithTag(VAULT_SETTINGS_TITLE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(VAULT_BACK_TAG).performClick()

        assertTrue(wentBack)
    }

    @Test
    fun theVaultOffersExportAndImport() {
        var exported = false
        var imported = false
        render(
            vault = VaultUiState(available = true, message = AppMessage.core("Vault exported.")),
            actions = SettingsActions(onExportVault = { exported = true }, onImportVault = { imported = true }),
        )

        compose.onNodeWithText("Vault exported.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export vault").performScrollTo().performClick()
        compose.onNodeWithText("Import vault").performScrollTo().performClick()

        assertTrue(exported)
        assertTrue(imported)
    }

    @Test
    fun anUnwiredVaultTransferSaysSoAndDisablesItsActions() {
        render(vault = VaultUiState(available = false))

        compose.onNodeWithText("Vault export and import aren't available", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Import vault").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aVaultErrorRendersAndDismisses() {
        var dismissed = false
        render(
            vault = VaultUiState(available = true, error = AppMessage.core("That file isn't a Lekto vault.")),
            actions = SettingsActions(onDismissVaultMessage = { dismissed = true }),
        )

        compose.onNodeWithText("That file isn't a Lekto vault.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun exportingInProgressDisablesTheActions() {
        render(vault = VaultUiState(available = true, transferring = true))

        compose.onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Import vault").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun syncAlwaysWarnsAgainstFolderSyncTools() {
        render()

        compose.onNodeWithText("folder-sync tool", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("corrupt", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anUnwiredSyncSaysSoAndHidesItsControls() {
        render(sync = SyncUiState(available = false))

        compose.onNodeWithText("Sync isn't available", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_HOST_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun aConfiguredSyncShowsItsStatusAndEndpoint() {
        render(
            sync = SyncUiState(
                available = true,
                config = SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
            ),
        )

        compose.onNodeWithText("Sync is off.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_HOST_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theServerAndUserWriteOnChange() {
        var host: String? = null
        var username: String? = null
        render(
            sync = SyncUiState(available = true),
            actions = SettingsActions(
                onSyncHostChange = { typed -> host = typed },
                onSyncUsernameChange = { typed -> username = typed },
            ),
        )

        compose.onNodeWithTag(SYNC_HOST_FIELD_TAG).performScrollTo().performTextInput("https://cloud.example.test/dav")
        assertEquals("https://cloud.example.test/dav", host)

        compose.onNodeWithTag(SYNC_USERNAME_FIELD_TAG).performScrollTo().performTextInput("reader")
        assertEquals("reader", username)
    }

    @Test
    fun anEnabledSyncSaysSyncIsOn() {
        render(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                hasStoredPassword = true,
            ),
        )

        compose.onNodeWithText("Sync is on.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun connectingPassesTheTypedPasswordToTheController() {
        var connected: String? = null
        render(
            sync = SyncUiState(available = true),
            actions = SettingsActions(onConnectSync = { password -> connected = password }),
        )

        compose.onNodeWithTag(SYNC_PASSWORD_FIELD_TAG).performScrollTo().performTextInput("app-password")
        compose.onNodeWithTag(SYNC_CONNECT_TAG).performScrollTo().performClick()

        assertEquals("app-password", connected)
    }

    @Test
    fun aStoredPasswordShowsAMaskedPlaceholder() {
        render(sync = SyncUiState(available = true, hasStoredPassword = true))

        compose.onNodeWithText(SECRET_PLACEHOLDER).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLastRunTimeIsShown() {
        val now = Instant.fromEpochMilliseconds(FIXED_NOW_MILLIS)
        render(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                hasStoredPassword = true,
                lastRunAt = now.toEpochMilliseconds() - TWO_MINUTES_MILLIS,
            ),
            now = now,
        )

        compose.onNodeWithText("Synced 2 minutes ago.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLastSyncOutcomeIsShown() {
        render(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                lastSync = SyncResult.Success(AppMessage.core("Synced: uploaded 1.")),
            ),
        )

        compose.onNodeWithText("Synced: uploaded 1.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun syncNowIsOfferedOnlyWhenSyncIsOn() {
        render(sync = SyncUiState(available = true, config = configured()))

        compose.onNodeWithTag(SYNC_NOW_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun syncNowRaisesTheActionWhenSyncIsOn() {
        var synced = false
        render(
            sync = SyncUiState(available = true, config = configured(enabled = true)),
            actions = SettingsActions(onSyncNow = { synced = true }),
        )

        compose.onNodeWithTag(SYNC_NOW_TAG).performScrollTo().performClick()

        assertTrue(synced)
    }

    @Test
    fun aFailedSyncIsShownInPlainLanguage() {
        render(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                lastSync = SyncResult.Failure(
                    AppMessage.core("The last sync didn't finish: the server is unreachable"),
                ),
            ),
        )

        compose.onNodeWithText("The last sync didn't finish", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSyncResultRendersAndDismisses() {
        var dismissed = false
        render(
            sync = SyncUiState(available = true, result = SyncResult.Success(AppMessage.core("Saved."))),
            actions = SettingsActions(onDismissSyncResult = { dismissed = true }),
        )

        compose.onNodeWithText("Saved.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_DISMISS_TAG).performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun disconnectingRaisesTheAction() {
        var disconnected = false
        render(
            sync = SyncUiState(available = true, config = configured()),
            actions = SettingsActions(onDisconnectSync = { disconnected = true }),
        )

        compose.onNodeWithTag(SYNC_DISCONNECT_TAG).performScrollTo().performClick()

        assertTrue(disconnected)
    }

    @Test
    fun syncingInProgressDisablesTheControls() {
        render(sync = SyncUiState(available = true, config = configured(enabled = true), syncing = true))

        compose.onNodeWithTag(SYNC_CONNECT_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SYNC_NOW_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SYNC_DISCONNECT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theScreenRendersInTheAppLanguage() {
        render(vault = VaultUiState(available = true), texts = appTextsFor("fr"))

        compose.onNodeWithText("Coffre et synchronisation").assertIsDisplayed()
        compose.onNodeWithText("Exporter le coffre").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Importer un coffre").performScrollTo().assertIsDisplayed()
    }

    /** Renders the vault screen in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun render(
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
        compose.setContent {
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
