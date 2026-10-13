package app.lekto.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.text.baseLanguage

/** The test tag on the **Reload list** action, so a UI test can click exactly it. */
const val LANGUAGES_RELOAD_TAG: String = "languages-reload"

/** The test tag on a language's level picker on the **Languages** surface. */
internal fun languageLevelPickerTag(language: String): String = "languages-level-$language"

/** The test tag on a CEFR option in a language's level picker. */
internal fun languageLevelOptionTag(language: String, level: CefrLevel): String =
    "languages-level-$language-${level.name}"

/** The test tag on a language's **not set** option, which clears the level. */
internal fun languageLevelClearTag(language: String): String = "languages-level-$language-none"

/**
 * The actions the **Languages** surface raises: refetch the manifest, set or
 * clear a language's **Reader level** (issue #147), or leave.
 */
data class LanguagesActions(
    val onReload: () -> Unit = {},
    val onSetLevel: (String, CefrLevel) -> Unit = { _, _ -> },
    val onClearLevel: (String) -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The **Languages** surface (issues #146, #147): it lists the offered **Reading
 * language**s the **Language manifest** publishes, in the reader's priority
 * order, and carries the reader's **Reader level** picker per language. With no
 * manifest and no cache it says so honestly. **Reload list** is always offered,
 * so a reader can refetch at any time — after a failed fetch, or when the
 * pipeline has published a language since the cached copy.
 *
 * The screen renders [state] and [levels] and raises the [actions]; it owns no
 * state, so the whole surface is proved through semantics. The other per-language
 * controls — the **Difficulty table**, the **Dictionary pack** — land here in
 * later tickets (#148, #149).
 */
@Composable
fun LanguagesScreen(
    state: LanguagesUiState,
    levels: Map<String, CefrLevel> = emptyMap(),
    actions: LanguagesActions = LanguagesActions(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Header(actions.onBack)

        ReloadAction(onReload = actions.onReload, reloading = state.reloading)
        if (state.reloading) Reloading()
        state.error?.let { message -> Error(message) }

        if (state.languages.isEmpty()) {
            EmptyState(available = state.available)
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                items(state.languages, key = { offered -> offered.language }) { offered ->
                    // The level is keyed by the language's base tag, as the
                    // ordering and naming already reduce the offered tag
                    // (CONTEXT.md, "Reading language").
                    LanguageRow(offered, levels[baseLanguage(offered.language)], actions)
                }
            }
        }
    }
}

/** The surface's chrome: the way back and its title. */
@Composable
private fun Header(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Languages", style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * The **Reload list** action, always visible so the reader can refetch the
 * manifest at any time (issue #146). It is disabled while a fetch is in flight.
 */
@Composable
private fun ReloadAction(onReload: () -> Unit, reloading: Boolean) {
    Button(
        onClick = onReload,
        enabled = !reloading,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(LANGUAGES_RELOAD_TAG),
    ) { Text("Reload list") }
}

/**
 * One offered **Reading language**: its name, and the **Reader level** the reader
 * declares in it (issue #147). The level is user-authored **Vault** data, so the
 * picker writes to the vault; **not set** clears it.
 */
@Composable
private fun LanguageRow(offered: OfferedLanguage, level: CefrLevel?, actions: LanguagesActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = offered.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        LevelPicker(offered.language, level, actions)
    }
}

/**
 * The **Reader level** picker (issue #147): the six CEFR bands A1–C2 and **not
 * set**, which clears the level. It shows the current band, or "Not set".
 */
@Composable
private fun LevelPicker(language: String, level: CefrLevel?, actions: LanguagesActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(languageLevelPickerTag(language)),
        ) {
            Text(level?.name ?: NOT_SET)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(NOT_SET) },
                onClick = {
                    expanded = false
                    actions.onClearLevel(language)
                },
                modifier = Modifier.testTag(languageLevelClearTag(language)),
            )
            CefrLevel.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        actions.onSetLevel(language, option)
                    },
                    modifier = Modifier.testTag(languageLevelOptionTag(language, option)),
                )
            }
        }
    }
}

/** The picker's label for a language with no level declared. */
private const val NOT_SET: String = "Not set"

/** The honest empty state: no offered languages are known. */
@Composable
private fun EmptyState(available: Boolean) {
    Text(
        text = if (available) {
            "Lekto doesn't offer any languages for reading yet."
        } else {
            "Lekto hasn't loaded the list of languages it offers yet. Reload the list, or check your connection."
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    )
}

/** The inline fetch progress, so the surface never blocks on the network. */
@Composable
private fun Reloading() {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Loading the language list…", style = MaterialTheme.typography.bodyMedium)
    }
}

/** The inline fetch failure, with no dismiss: the **Reload list** action is the way out. */
@Composable
private fun Error(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}
