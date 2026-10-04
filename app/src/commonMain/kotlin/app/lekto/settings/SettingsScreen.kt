package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * The settings screen (issue #18): the dictionary pack's state with a download
 * action and a way to the attribution screen. It is deliberately small — the
 * vault export/import settings of issue #20 land here later.
 */
@Composable
fun SettingsScreen(
    state: DictionaryUiState,
    actions: SettingsActions = SettingsActions(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(actions.onBack)
        Text("Dictionary", style = MaterialTheme.typography.titleLarge)
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
