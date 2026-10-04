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
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.WordLookup

/**
 * The minimal word lookup result (issue #18): definition, translation and
 * pronunciation from the offline pack, or an honest message when the word is
 * unknown or no pack is installed. The rich lookup panel — saving, mastery,
 * deep links — is issue #19.
 */
@Composable
fun WordLookupCard(result: WordLookup, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (result) {
                is WordLookup.Found -> Found(result)

                is WordLookup.NotInDictionary ->
                    Text(
                        "\"${result.query}\" isn't in the offline dictionary.",
                        style = MaterialTheme.typography.bodyLarge,
                    )

                is WordLookup.Unavailable -> Text(result.message, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun Found(found: WordLookup.Found) {
    Text(found.lemma, style = MaterialTheme.typography.titleLarge)
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
