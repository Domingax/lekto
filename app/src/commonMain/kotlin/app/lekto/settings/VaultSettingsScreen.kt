package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor
import app.lekto.ui.icons.Info
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.touchClient
import kotlin.time.Clock
import kotlin.time.Instant

/** The test tags the vault and sync screen's controls carry, so a UI test can click exactly one. */
internal const val SYNC_HOST_FIELD_TAG: String = "settings-sync-host"
internal const val SYNC_USERNAME_FIELD_TAG: String = "settings-sync-username"
internal const val SYNC_PASSWORD_FIELD_TAG: String = "settings-sync-password"
internal const val SYNC_CONNECT_TAG: String = "settings-sync-connect"
internal const val SYNC_NOW_TAG: String = "settings-sync-now"
internal const val SYNC_WARNING_INFO_TAG: String = "settings-sync-warning"
internal const val SYNC_PROGRESS_TAG: String = "settings-sync-progress"
internal const val SYNC_DISCONNECT_TAG: String = "settings-sync-disconnect"

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
 * address and username apply on change; the **application password** is written
 * only after **Connection** answers, and that same success turns sync on.
 * **Sync now** is the one filled button. **Disconnect** is red because it wipes
 * the stored password, and the warning about external folder-sync tools is always
 * shown, in the muted ink.
 */
@Composable
@Suppress("LongParameterList") // The vault, its sync, the shared actions, the way back and the layout.
fun VaultSettingsScreen(
    vault: VaultUiState,
    sync: SyncUiState,
    actions: SettingsActions = SettingsActions(),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.ScreenEdge),
    ) {
        SettingsFormHeader(
            title = texts[TextKey.VAULT_AND_SYNC],
            backTag = VAULT_BACK_TAG,
            titleTag = VAULT_SETTINGS_TITLE_TAG,
            onBack = onBack,
        )
        Text(
            texts[TextKey.VAULT_SECTION_DESCRIPTION],
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        VaultTransfer(vault, actions)
        SyncForm(sync, actions, now)
    }
}

/** The vault's export and import, with the last outcome inline. */
@Composable
private fun VaultTransfer(state: VaultUiState, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows)) {
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
}

/** The sync form: its state, the endpoint, the password and the actions. */
@Composable
@Suppress("LongMethod") // The form reads top to bottom in the order docs/design.md §7.6 names it.
private fun SyncForm(state: SyncUiState, actions: SettingsActions, now: Instant) {
    val texts = LocalAppTexts.current
    var showWarning by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows)) {
        FormGroupLabel(texts[TextKey.SECTION_SYNC])
        if (!state.available) {
            Text(texts[TextKey.SYNC_UNAVAILABLE], style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        // The state is always visible (CONTEXT.md, "Sync"), and the last run's time
        // joins it so the block says when it last happened as well as whether it is on.
        val status = texts.message(state.status)
        val age = state.lastRunAt?.let { at -> texts.message(lastSyncLabel(now, at)) }
        Text(
            if (age == null) status else "$status $age",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
        )
        // The time above already says when it last ran; only a failure needs detail.
        (state.lastSync as? SyncResult.Failure)?.let { outcome -> SyncOutcomeLine(outcome) }

        var password by remember { mutableStateOf("") }
        FormField(
            value = state.config.serverUrl,
            onValueChange = actions.onSyncHostChange,
            label = texts[TextKey.SERVER_ADDRESS],
            modifier = Modifier.fillMaxWidth().testTag(SYNC_HOST_FIELD_TAG),
            // The folder-sync caution lives behind this, not as a paragraph between
            // the fields (issue #213).
            labelTrailing = { ServerWarningButton { showWarning = true } },
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
            hasStoredSecret = state.hasStoredPassword,
        )

        // A connection test and a run both take time; the loader must cover both, not
        // only the sync that follows a successful test (issue #213).
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag(SYNC_PROGRESS_TAG))
        }
        // The actions, the answer and the way off are one group, so the result and
        // Disconnect sit right under the buttons (issue #213).
        Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
            SyncActions(state, password, actions)
            state.result?.let { result -> SyncResultLine(result) }
            Disconnect(state, actions)
        }
    }
    if (showWarning) FolderSyncWarningDialog { showWarning = false }
}

/**
 * The information affordance beside **Server address**: the folder-sync caution,
 * kept out of the form's reading flow until the reader asks for it.
 */
@Composable
private fun ServerWarningButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        // A thumb gets the 48 dp target; a pointer gets the compact label-row size.
        modifier = Modifier.size(if (touchClient) 48.dp else 24.dp)
            .handCursor()
            .testTag(SYNC_WARNING_INFO_TAG),
    ) {
        Icon(
            imageVector = LektoIcons.Info,
            contentDescription = LocalAppTexts.current[TextKey.SYNC_FOLDER_WARNING_TITLE],
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The folder-sync caution, raised only from the information icon. */
@Composable
private fun FolderSyncWarningDialog(onDismiss: () -> Unit) {
    val texts = LocalAppTexts.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts[TextKey.SYNC_FOLDER_WARNING_TITLE]) },
        text = { Text(texts[TextKey.SYNC_FOLDER_WARNING]) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text(texts[TextKey.CLOSE])
            }
        },
    )
}

/**
 * The sync actions: **Sync now** is the one filled button; **Connection** tests
 * the endpoint, stores the password and turns sync on. Both are disabled while a
 * test or a run is in flight.
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
        onClick = { actions.onConnectSync(password) },
        enabled = !state.busy,
        shape = LektoRadii.Control,
        modifier = Modifier.fillMaxWidth().handCursor(!state.busy).testTag(SYNC_CONNECT_TAG),
    ) { Text(texts[TextKey.CONNECTION]) }
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

/** The inline result under **Connection**; the next action clears it. */
@Composable
private fun SyncResultLine(result: SyncResult) {
    Text(
        LocalAppTexts.current.message(result.message),
        color = syncColor(result),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The time since the last run, in the reading language (issue #213): "Synced 2
 * minutes ago" at rest, so the sync block says when it last happened rather than
 * only whether it is on. A clock that went backwards reads as "just now".
 */
internal fun lastSyncLabel(now: Instant, atMillis: Long): AppMessage {
    val minutes = (now - Instant.fromEpochMilliseconds(atMillis)).inWholeMinutes
    return when {
        minutes < 1 -> AppMessage.Text(TextKey.SYNC_LAST_JUST_NOW)
        minutes == 1L -> AppMessage.Text(TextKey.SYNC_LAST_MINUTE)
        minutes < MINUTES_PER_HOUR -> AppMessage.of(TextKey.SYNC_LAST_MINUTES, minutes)
        minutes < MINUTES_PER_DAY -> hoursLabel(minutes / MINUTES_PER_HOUR)
        else -> daysLabel(minutes / MINUTES_PER_DAY)
    }
}

/** "1 hour ago" or "N hours ago", so the `when` above stays one line per branch. */
private fun hoursLabel(hours: Long): AppMessage =
    if (hours == 1L) AppMessage.Text(TextKey.SYNC_LAST_HOUR) else AppMessage.of(TextKey.SYNC_LAST_HOURS, hours)

/** "1 day ago" or "N days ago". */
private fun daysLabel(days: Long): AppMessage =
    if (days == 1L) AppMessage.Text(TextKey.SYNC_LAST_DAY) else AppMessage.of(TextKey.SYNC_LAST_DAYS, days)

/** The minute count in an hour and a day, so the relative label reads without magic numbers. */
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = MINUTES_PER_HOUR * 24

/** A failure reads in the error colour; a confirmation in the muted ink, like the rest of the prose. */
@Composable
private fun syncColor(result: SyncResult): Color =
    if (result is SyncResult.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

/** A vault outcome or failure, in the error colour only for a failure, with a way to dismiss it. */
@Composable
private fun VaultMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
        Text(
            message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
            Text(LocalAppTexts.current[TextKey.DISMISS])
        }
    }
}
