package app.lekto.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.language.OfferedLanguage

/** The test tag on the **Reload list** action, so a UI test can click exactly it. */
const val LANGUAGES_RELOAD_TAG: String = "languages-reload"

/** The actions the **Languages** surface raises: refetch the manifest, or leave. */
data class LanguagesActions(val onReload: () -> Unit = {}, val onBack: () -> Unit = {})

/**
 * The **Languages** surface (issue #146): it lists the offered **Reading
 * language**s the **Language manifest** publishes, in the reader's priority
 * order. With no manifest and no cache it says so honestly and offers **Reload
 * list**, which refetches.
 *
 * The screen renders [state] and raises the [actions]; it owns no state, so the
 * whole surface is proved through semantics. The per-language controls — the
 * **Reader level**, the **Difficulty table**, the **Dictionary pack** — land
 * here in later tickets (#147, #148, #149).
 */
@Composable
fun LanguagesScreen(
    state: LanguagesUiState,
    actions: LanguagesActions = LanguagesActions(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Header(actions.onBack)

        if (state.reloading) Reloading()
        state.error?.let { message -> Error(message) }

        if (state.languages.isEmpty()) {
            EmptyState(available = state.available, onReload = actions.onReload, reloading = state.reloading)
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                items(state.languages, key = { offered -> offered.language }) { offered -> LanguageRow(offered) }
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

/** One offered **Reading language**, by the name the manifest implies. */
@Composable
private fun LanguageRow(offered: OfferedLanguage) {
    Text(
        text = offered.displayName,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

/** The honest empty state: no offered languages are known, with a way to refetch them. */
@Composable
private fun EmptyState(available: Boolean, onReload: () -> Unit, reloading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (available) {
                "Lekto doesn't offer any languages for reading yet."
            } else {
                "Lekto hasn't loaded the list of languages it offers yet. Reload the list, or check your connection."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onReload,
            enabled = !reloading,
            modifier = Modifier.fillMaxWidth().testTag(LANGUAGES_RELOAD_TAG),
        ) { Text("Reload list") }
    }
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
