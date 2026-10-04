package app.lekto.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.lekto.core.speech.SpeechResult

/** The test tag on a source's shortcut, so a UI test can click exactly one. */
internal fun sourceShortcutTag(source: DictionarySource): String = "lookup-source-${source.name}"

/** The test tag on the pronunciation button, so a UI test can click it unambiguously. */
internal const val PRONOUNCE_TAG: String = "lookup-pronounce"

/**
 * The word lookup panel (issue #19): the signature interaction. It opens on the
 * word the reader tapped with the offline result already in it, and always
 * offers the reference-site shortcuts beside that result — so with no pack and
 * no network it still degrades to the shortcuts and an honest message rather
 * than an empty panel. The reader stays in place behind it, so closing returns
 * to the exact reading position (docs/ux-design-specification.md, "Success
 * Criteria").
 *
 * Since issue #21 it also carries the pronunciation control: [onSpeak] speaks
 * the tapped word and [speech] is the honest result of the last attempt, so a
 * language with no installed voice says so instead of failing.
 */
@Suppress("LongParameterList") // The panel's inputs are its result, word, shortcuts and speech; a bundle adds a type.
@Composable
fun WordLookupPanel(
    result: WordLookup,
    term: String,
    shortcuts: List<DictionaryShortcut>,
    onOpenShortcut: (DictionaryShortcut) -> Unit,
    onDismiss: () -> Unit,
    speech: SpeechResult? = null,
    onSpeak: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Header(term, onSpeak, onDismiss)
            SpeechMessage(speech)
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
private fun Header(term: String, onSpeak: (() -> Unit)?, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(term, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onSpeak?.let { speak ->
                TextButton(onClick = speak, modifier = Modifier.testTag(PRONOUNCE_TAG)) { Text("Listen") }
            }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

/** The honest outcome of the last pronunciation attempt: a failure is a message, success is silent. */
@Composable
private fun SpeechMessage(speech: SpeechResult?) {
    val message = when (speech) {
        null, is SpeechResult.Spoken -> null

        is SpeechResult.NoVoice ->
            speech.language?.let { language -> "No voice is installed for \"$language\"." }
                ?: "No voice is installed for this language."

        is SpeechResult.Unavailable -> speech.message
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
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
