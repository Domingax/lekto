package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.dictionary.DictionaryUiState
import app.lekto.dictionary.summary

/**
 * The settings screen: the dictionary pack (issue #18) and the vault's
 * export/import (issue #20). Each is its own section so later settings land
 * without disturbing the ones already here, and nothing on the screen gates the
 * reader — the vault actions exist only where a file picker is wired.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions = SettingsActions(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Header(actions.onBack)
        Section("Dictionary") { DictionarySection(state.dictionary, actions) }
        Section("Vault") { VaultSection(state.vault, actions) }
    }
}

/** One settings section: a title and its content, so every section is spaced alike. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun DictionarySection(state: DictionaryUiState, actions: SettingsActions) {
    Text(state.status.summary(), style = MaterialTheme.typography.bodyMedium)

    if (state.installing) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Downloading the offline dictionary…", style = MaterialTheme.typography.bodyMedium)
    }
    state.error?.let { message -> Error(message, actions.onDismissError) }

    Button(onClick = actions.onDownload, enabled = !state.installing, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.status is DictionaryPackState.Ready) "Download again" else "Download")
    }
    TextButton(onClick = actions.onOpenAttribution) { Text("Attribution") }
}

@Composable
private fun VaultSection(state: VaultUiState, actions: SettingsActions) {
    Text(
        "Your vault holds everything you've imported and saved. Export it to a file you keep, " +
            "or import one to restore your books and vocabulary on this device. Importing replaces " +
            "the vault already on this device.",
        style = MaterialTheme.typography.bodyMedium,
    )

    if (!state.available) {
        Text("Vault export and import aren't available in this build.", style = MaterialTheme.typography.bodyMedium)
    } else {
        state.message?.let { message -> Notice(message, actions.onDismissVaultMessage) }
        state.error?.let { message -> Error(message, actions.onDismissVaultMessage) }
        if (state.transferring) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    Button(
        onClick = actions.onExportVault,
        enabled = state.available && !state.transferring,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Export vault") }
    Button(
        onClick = actions.onImportVault,
        enabled = state.available && !state.transferring,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Import vault") }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onBack) { Text("Library") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun Error(message: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}
