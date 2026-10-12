package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.CefrLevel
import app.lekto.core.difficulty.DifficultyState
import app.lekto.difficulty.summary

/** The test tag on a language's level picker, so a UI test can click exactly one. */
internal fun readerLevelPickerTag(language: String): String = "settings-level-$language"

/** The test tag on a level option in a language's picker. */
internal fun readerLevelOptionTag(language: String, level: CefrLevel): String = "settings-level-$language-${level.name}"

/** The test tag on the difficulty table's download button. */
internal const val DOWNLOAD_DIFFICULTY_TAG: String = "settings-difficulty-download"

/**
 * The **Reading level** section (issue #64; ADR-0030): one CEFR picker per
 * language the reader has a book in, and the **Difficulty table**'s state with
 * its download. The level is user-authored data kept in the vault, so it travels
 * with an export and a sync; the table is a derived asset, device-local and never
 * synced.
 */
@Composable
internal fun ReaderLevelSection(state: ReaderLevelUiState, actions: SettingsActions) {
    Text(
        "Set the level you read at in each language. A word at or below it reads as normal text; " +
            "a harder word stays highlighted.",
        style = MaterialTheme.typography.bodyMedium,
    )

    if (state.languages.isEmpty()) {
        Text("Import a book to set your reading level.", style = MaterialTheme.typography.bodyMedium)
    } else {
        state.languages.forEach { language -> LevelRow(language, state.levels[language], actions) }
    }

    Text(state.difficulty.summary(), style = MaterialTheme.typography.bodyMedium)
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
        Text(if (state.difficulty is DifficultyState.Ready) "Download again" else "Download reading-level data")
    }
}

/** One language's level: its tag and a picker of the six CEFR bands. */
@Composable
private fun LevelRow(language: String, level: CefrLevel?, actions: SettingsActions) {
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(language, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag(readerLevelPickerTag(language)),
            ) {
                Text(level?.name ?: "Not set")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CefrLevel.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            expanded = false
                            actions.onSetLevel(language, option)
                        },
                        modifier = Modifier.testTag(readerLevelOptionTag(language, option)),
                    )
                }
            }
        }
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
