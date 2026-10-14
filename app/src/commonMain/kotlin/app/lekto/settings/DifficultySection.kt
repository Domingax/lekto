package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.difficulty.DifficultyState
import app.lekto.difficulty.DifficultyUiState
import app.lekto.difficulty.summary

/** The test tag on the difficulty table's download button. */
internal const val DOWNLOAD_DIFFICULTY_TAG: String = "settings-difficulty-download"

/**
 * The **Difficulty table** section (issues #64, #147; ADR-0029): its state and
 * its download. The **Reader level** picker that shared this section now lives on
 * the **Languages** surface (issue #147); the table itself moves there in a later
 * ticket (#148). The table is a **Derived asset**, device-local and never synced.
 */
@Composable
internal fun DifficultySection(state: DifficultyUiState, actions: SettingsActions) {
    Text(state.status.summary(), style = MaterialTheme.typography.bodyMedium)

    if (state.installing) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Downloading reading-level data…", style = MaterialTheme.typography.bodyMedium)
    }
    state.error?.let { message -> DifficultyError(message, actions.onDismissDifficultyError) }

    Button(
        onClick = actions.onDownloadDifficulty,
        enabled = !state.installing,
        modifier = Modifier.fillMaxWidth().testTag(DOWNLOAD_DIFFICULTY_TAG),
    ) {
        Text(if (state.status is DifficultyState.Ready) "Download again" else "Download reading-level data")
    }
}

/** The section's inline failure, with its own dismiss. */
@Composable
private fun DifficultyError(message: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}
