package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor

/** The test tags the vault and sync screen's controls carry, so a UI test can click exactly one. */
internal const val SYNC_HOST_FIELD_TAG: String = "settings-sync-host"
internal const val SYNC_USERNAME_FIELD_TAG: String = "settings-sync-username"
internal const val SYNC_PASSWORD_FIELD_TAG: String = "settings-sync-password"
internal const val SYNC_SAVE_TAG: String = "settings-sync-save"
internal const val SYNC_TEST_TAG: String = "settings-sync-test"
internal const val SYNC_ENABLE_TAG: String = "settings-sync-enable"
internal const val SYNC_DISABLE_TAG: String = "settings-sync-disable"
internal const val SYNC_NOW_TAG: String = "settings-sync-now"
internal const val SYNC_DISCONNECT_TAG: String = "settings-sync-disconnect"
internal const val SYNC_DISMISS_TAG: String = "settings-sync-dismiss"

/** The test tag on the way back to Settings, so a UI test can click exactly it. */
internal const val VAULT_BACK_TAG: String = "vault-settings-back"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar. */
internal const val VAULT_SETTINGS_TITLE_TAG: String = "vault-settings-title"

/**
 * The **Vault and sync** screen (issues #20, #28, #213; docs/design.md §7.5,
 * §7.6): the vault's export and import, then the user's own WebDAV server, the
 * application password, the state and the actions — a screen of its own rather
 * than two forms buried in Settings.
 *
 * The screen is **app-private** (ADR-0010): there is no folder to choose, and a
 * "change location" control would describe an app that does not exist. The server
 * address and username apply on change; the **application password** keeps an
 * explicit **Save**, because writing it is a secret write. **Sync now** is the one
 * filled button. **Disconnect** is red because it wipes the stored password, and
 * the warning about external folder-sync tools is always shown.
 */
@Composable
@Suppress("LongParameterList") // The vault, its sync, the shared actions, the way back and the layout.
fun VaultSettingsScreen(
    vault: VaultUiState,
    sync: SyncUiState,
    actions: SettingsActions = SettingsActions(),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenBlocks),
    ) {
        SettingsFormHeader(
            title = texts[TextKey.VAULT_AND_SYNC],
            backTag = VAULT_BACK_TAG,
            titleTag = VAULT_SETTINGS_TITLE_TAG,
            onBack = onBack,
        )
        Text(texts[TextKey.VAULT_SECTION_DESCRIPTION], style = MaterialTheme.typography.bodyMedium)
        VaultTransfer(vault, actions)
        SyncForm(sync, actions)
    }
}

/** The vault's export and import, with the last outcome inline. */
@Composable
private fun VaultTransfer(state: VaultUiState, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    if (!state.available) {
        Text(texts[TextKey.VAULT_UNAVAILABLE], style = MaterialTheme.typography.bodyMedium)
    }
    state.message?.let { message ->
        VaultMessage(texts.message(message), isError = false, onDismiss = actions.onDismissVaultMessage)
    }
    state.error?.let { message ->
        VaultMessage(texts.message(message), isError = true, onDismiss = actions.onDismissVaultMessage)
    }
    if (state.transferring) LinearProgressIndicator(Modifier.fillMaxWidth())

    val enabled = state.available && !state.transferring
    Row(horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows)) {
        OutlinedButton(
            onClick = actions.onExportVault,
            enabled = enabled,
            shape = LektoRadii.Control,
            modifier = Modifier.weight(1f).handCursor(enabled),
        ) { Text(texts[TextKey.EXPORT_VAULT]) }
        OutlinedButton(
            onClick = actions.onImportVault,
            enabled = enabled,
            shape = LektoRadii.Control,
            modifier = Modifier.weight(1f).handCursor(enabled),
        ) { Text(texts[TextKey.IMPORT_VAULT]) }
    }
}

/** The sync form: its state, the endpoint, the password and the actions. */
@Composable
private fun SyncForm(state: SyncUiState, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    FormGroupLabel(texts[TextKey.SECTION_SYNC])
    Text(texts[TextKey.SYNC_SECTION_DESCRIPTION], style = MaterialTheme.typography.bodyMedium)
    // An advisory, not an act: red belongs to the irreversible actions (docs/design.md §2.4, §7.5).
    Text(
        texts[TextKey.SYNC_FOLDER_WARNING],
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!state.available) {
        Text(texts[TextKey.SYNC_UNAVAILABLE], style = MaterialTheme.typography.bodyMedium)
        return
    }

    Text(texts.message(state.status), style = MaterialTheme.typography.titleSmall)
    state.lastSync?.let { outcome -> SyncOutcomeLine(outcome) }

    var password by remember { mutableStateOf("") }
    FormField(
        value = state.config.serverUrl,
        onValueChange = actions.onSyncHostChange,
        label = texts[TextKey.SERVER_ADDRESS],
        modifier = Modifier.fillMaxWidth().testTag(SYNC_HOST_FIELD_TAG),
    )
    FormField(
        value = state.config.username,
        onValueChange = actions.onSyncUsernameChange,
        label = texts[TextKey.USERNAME],
        modifier = Modifier.fillMaxWidth().testTag(SYNC_USERNAME_FIELD_TAG),
    )
    SecretField(
        value = password,
        onValueChange = { typed -> password = typed },
        label = texts[TextKey.APPLICATION_PASSWORD],
        modifier = Modifier.fillMaxWidth().testTag(SYNC_PASSWORD_FIELD_TAG),
    )

    state.result?.let { result -> SyncResultLine(result, actions.onDismissSyncResult) }
    if (state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
    SyncActions(state, password, actions)
    SyncToggle(state, actions)
    Disconnect(state, actions)
}

/**
 * The sync actions: **Sync now** is the one filled button; testing the
 * connection and the password's explicit **Save** are outlined.
 */
@Composable
private fun SyncActions(state: SyncUiState, password: String, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    val canSync = state.config.enabled && !state.busy
    Button(
        onClick = actions.onSyncNow,
        enabled = canSync,
        shape = LektoRadii.Control,
        modifier = Modifier.fillMaxWidth().handCursor(canSync).testTag(SYNC_NOW_TAG),
    ) { Text(texts[TextKey.SYNC_NOW]) }
    OutlinedButton(
        onClick = { actions.onTestSync(password) },
        enabled = !state.busy,
        shape = LektoRadii.Control,
        modifier = Modifier.fillMaxWidth().handCursor(!state.busy).testTag(SYNC_TEST_TAG),
    ) { Text(texts[TextKey.TEST_CONNECTION]) }
    OutlinedButton(
        onClick = { actions.onSaveSync(password) },
        enabled = !state.busy,
        shape = LektoRadii.Control,
        modifier = Modifier.fillMaxWidth().handCursor(!state.busy).testTag(SYNC_SAVE_TAG),
    ) { Text(texts[TextKey.SAVE]) }
}

/** Turning sync on is a deliberate act, and the manual run is offered only once it is on. */
@Composable
private fun SyncToggle(state: SyncUiState, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    if (state.config.enabled) {
        OutlinedButton(
            onClick = actions.onDisableSync,
            enabled = !state.busy,
            shape = LektoRadii.Control,
            modifier = Modifier.fillMaxWidth().handCursor(!state.busy).testTag(SYNC_DISABLE_TAG),
        ) { Text(texts[TextKey.TURN_OFF_SYNC]) }
    } else {
        OutlinedButton(
            onClick = actions.onEnableSync,
            enabled = !state.busy,
            shape = LektoRadii.Control,
            modifier = Modifier.fillMaxWidth().handCursor(!state.busy).testTag(SYNC_ENABLE_TAG),
        ) { Text(texts[TextKey.ENABLE_SYNC]) }
    }
}

/** Disconnecting clears the endpoint and password; the vault stays on this device. */
@Composable
private fun Disconnect(state: SyncUiState, actions: SettingsActions) {
    IrreversibleAction(
        text = LocalAppTexts.current[TextKey.DISCONNECT],
        onClick = actions.onDisconnectSync,
        modifier = Modifier.testTag(SYNC_DISCONNECT_TAG),
        enabled = !state.busy,
    )
}

/** The last sync's outcome in plain language, kept visible at rest. */
@Composable
private fun SyncOutcomeLine(outcome: SyncResult) {
    Text(
        LocalAppTexts.current.message(outcome.message),
        color = syncColor(outcome),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The inline result of the screen's own actions, with a way to dismiss it. */
@Composable
private fun SyncResultLine(result: SyncResult, onDismiss: () -> Unit) {
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
        Text(texts.message(result.message), color = syncColor(result), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss, modifier = Modifier.handCursor().testTag(SYNC_DISMISS_TAG)) {
            Text(texts[TextKey.DISMISS])
        }
    }
}

/** A failure reads in the error colour; a confirmation in the primary one. */
@Composable
private fun syncColor(result: SyncResult): Color =
    if (result is SyncResult.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

/** A vault outcome or failure, in the error colour only for a failure, with a way to dismiss it. */
@Composable
private fun VaultMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
        Text(
            message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
            Text(LocalAppTexts.current[TextKey.DISMISS])
        }
    }
}
