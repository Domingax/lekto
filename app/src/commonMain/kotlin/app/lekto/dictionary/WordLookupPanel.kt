package app.lekto.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionaryShortcut
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.WordLookup

/** The test tag on a source's shortcut, so a UI test can click exactly one. */
internal fun sourceShortcutTag(source: DictionarySource): String = "lookup-source-${source.name}"

/**
 * The word lookup panel (issue #19): the signature interaction. It opens on the
 * word the reader tapped with the offline result already in it, and always
 * offers the reference-site shortcuts beside that result — so with no pack and
 * no network it still degrades to the shortcuts and an honest message rather
 * than an empty panel. The reader stays in place behind it, so closing returns
 * to the exact reading position (docs/ux-design-specification.md, "Success
 * Criteria").
 */
@Suppress("LongParameterList") // The panel's inputs are the result, the word and its shortcuts; a bundle adds a type.
@Composable
fun WordLookupPanel(
    result: WordLookup,
    term: String,
    shortcuts: List<DictionaryShortcut>,
    onOpenShortcut: (DictionaryShortcut) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Header(term, onDismiss)
            when (result) {
                is WordLookup.Found -> Found(result, term)

                is WordLookup.NotInDictionary -> Text(
                    "\"${result.query}\" isn't in the offline dictionary.",
                    style = MaterialTheme.typography.bodyLarge,
                )

                is WordLookup.Unavailable -> Text(result.message, style = MaterialTheme.typography.bodyLarge)
            }
            Shortcuts(shortcuts, onOpenShortcut)
        }
    }
}

@Composable
private fun Header(term: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(term, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

@Composable
private fun Found(found: WordLookup.Found, term: String) {
    if (found.lemma != term) Text(found.lemma, style = MaterialTheme.typography.titleMedium)
    found.entries.forEach { entry -> Entry(entry) }
}

@Composable
private fun Entry(entry: DictionaryEntry) {
    entry.partOfSpeech?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    entry.pronunciation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    entry.senses.forEachIndexed { index, sense ->
        Text("${index + 1}. ${sense.definition}", style = MaterialTheme.typography.bodyMedium)
        sense.translations.forEach { translation ->
            Text("   → $translation", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Shortcuts(shortcuts: List<DictionaryShortcut>, onOpenShortcut: (DictionaryShortcut) -> Unit) {
    if (shortcuts.isEmpty()) return
    Text("Look it up online", style = MaterialTheme.typography.labelLarge)
    shortcuts.forEach { shortcut ->
        TextButton(
            onClick = { onOpenShortcut(shortcut) },
            modifier = Modifier.testTag(sourceShortcutTag(shortcut.source)),
        ) {
            Text(shortcut.source.label)
        }
    }
}
