package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp

/** The test tags the sync section's controls carry, so a UI test can click exactly one. */
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

/**
 * The warning the sync section always carries (issue #28): pointing an external
 * folder-sync tool at the vault puts two writers on one set of files, which
 * corrupts it — Lekto's own engine merges changes instead.
 */
internal const val SYNC_FOLDER_WARNING: String =
    "Don't point a folder-sync tool at your vault. Syncthing, Dropbox, iCloud Drive and the like " +
        "would put a second writer on the same files and can corrupt them. Use Lekto's own sync instead."

/**
 * The **sync** section (issue #28): configure the user's own WebDAV server, test
 * it, turn sync on as a deliberate act, see the last outcome, and disconnect.
 * The **application password** is entered in a masked field and handed to the
 * controller only to be stored or tested — it never enters this screen's state.
 * The warning about external folder-sync tools is always shown.
 */
@Composable
internal fun SyncSection(state: SyncUiState, actions: SettingsActions) {
    Text(
        "Sync your vault between your devices through your own WebDAV server — Nextcloud, ownCloud, " +
            "Synology or any WebDAV host. Your password is kept in this device's secure storage.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(SYNC_FOLDER_WARNING, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    if (!state.available) {
        Text("Sync isn't available in this build.", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Text(state.status, style = MaterialTheme.typography.titleSmall)
    state.lastSync?.let { outcome -> SyncOutcomeLine(outcome) }

    var password by remember { mutableStateOf("") }
    SyncFields(state, password, { typed -> password = typed }, actions)
    SyncActions(state, password, actions)
    state.result?.let { result -> SyncResultLine(result, actions.onDismissSyncResult) }
    SyncToggle(state, actions)
    if (state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
    Disconnect(state, actions)
}

/** The endpoint fields, with the password masked and owned by the screen. */
@Composable
private fun SyncFields(
    state: SyncUiState,
    password: String,
    onPasswordChange: (String) -> Unit,
    actions: SettingsActions,
) {
    SyncField(state.config.serverUrl, actions.onSyncHostChange, "Server address", SYNC_HOST_FIELD_TAG)
    SyncField(state.config.username, actions.onSyncUsernameChange, "Username", SYNC_USERNAME_FIELD_TAG)
    SecretField(
        value = password,
        onValueChange = onPasswordChange,
        label = "Application password",
        modifier = Modifier.fillMaxWidth().testTag(SYNC_PASSWORD_FIELD_TAG),
    )
}

/** The section's two immediate actions: persist the endpoint and password, or test the connection. */
@Composable
private fun SyncActions(state: SyncUiState, password: String, actions: SettingsActions) {
    val busy = state.testing || state.syncing
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { actions.onSaveSync(password) },
            enabled = !busy,
            modifier = Modifier.testTag(SYNC_SAVE_TAG),
        ) { Text("Save") }
        Button(
            onClick = { actions.onTestSync(password) },
            enabled = !busy,
            modifier = Modifier.testTag(SYNC_TEST_TAG),
        ) { Text("Test connection") }
    }
}

/** Turning sync on is a deliberate act, and the manual run is offered only once it is on. */
@Composable
private fun SyncToggle(state: SyncUiState, actions: SettingsActions) {
    val busy = state.testing || state.syncing
    if (state.config.enabled) {
        Button(
            onClick = actions.onDisableSync,
            enabled = !busy,
            modifier = Modifier.testTag(SYNC_DISABLE_TAG),
        ) { Text("Turn off sync") }
    } else {
        Button(
            onClick = actions.onEnableSync,
            enabled = !busy,
            modifier = Modifier.testTag(SYNC_ENABLE_TAG),
        ) { Text("Enable sync") }
    }
    Button(
        onClick = actions.onSyncNow,
        enabled = state.config.enabled && !busy,
        modifier = Modifier.testTag(SYNC_NOW_TAG),
    ) { Text("Sync now") }
}

/** Disconnecting clears the endpoint and password; the vault stays on this device. */
@Composable
private fun Disconnect(state: SyncUiState, actions: SettingsActions) {
    TextButton(
        onClick = actions.onDisconnectSync,
        enabled = !state.testing && !state.syncing,
        modifier = Modifier.testTag(SYNC_DISCONNECT_TAG),
    ) { Text("Disconnect") }
}

/** One labelled single-line field (the server address, or the username). */
@Composable
private fun SyncField(value: String, onValueChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** The last sync's outcome in plain language, kept visible at rest. */
@Composable
private fun SyncOutcomeLine(outcome: SyncResult) {
    Text(outcome.message, color = syncColor(outcome), style = MaterialTheme.typography.bodyMedium)
}

/** The inline result of the section's own actions, with a way to dismiss it. */
@Composable
private fun SyncResultLine(result: SyncResult, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(result.message, color = syncColor(result), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss, modifier = Modifier.testTag(SYNC_DISMISS_TAG)) { Text("Dismiss") }
    }
}

/** A failure reads in the error colour; a confirmation in the primary one. */
@Composable
private fun syncColor(result: SyncResult): Color =
    if (result is SyncResult.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
