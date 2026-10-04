package app.lekto.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionaryShortcut
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.speech.SpeechResult
import app.lekto.core.vocabulary.VocabularyEntry

/** The test tag on a source's shortcut, so a UI test can click exactly one. */
internal fun sourceShortcutTag(source: DictionarySource): String = "lookup-source-${source.name}"

/** The test tag on the pronunciation button, so a UI test can click it unambiguously. */
internal const val PRONOUNCE_TAG: String = "lookup-pronounce"

/** The test tag on the Save button. */
internal const val SAVE_TAG: String = "lookup-save"

/** The test tag on a mastery level's button, so a UI test can click exactly one. */
internal fun masteryTag(level: MasteryLevel): String = "lookup-mastery-${level.name}"

/**
 * The lookup panel's pronunciation control (issue #21): the action the Listen
 * button runs and the last honest [result], so a language with no voice renders
 * its message instead of staying silent.
 */
data class Pronunciation(val onSpeak: () -> Unit, val result: SpeechResult? = null)

/**
 * The lookup panel's vocabulary controls (issue #22): the saved [entry] for the
 * tapped word (`null` until it is saved) and the [onSave] that saves it or moves
 * it to another level. [defaultLevel] is the level a fresh save starts at, so
 * one tap on Save visibly promotes an unknown word.
 */
data class VocabularyPanel(
    val entry: VocabularyEntry?,
    val onSave: (MasteryLevel) -> Unit,
    val defaultLevel: MasteryLevel = MasteryLevel.FAMILIAR,
) {
    /** The level the selector marks: the saved one, or the fresh default. */
    val level: MasteryLevel get() = entry?.mastery ?: defaultLevel
}

/**
 * The panel's outbound actions (issues #19 and #22), bundled so the panel's
 * signature fits the parameter bound: [onOpenShortcut] hands a reference page to
 * the platform browser and [onDismiss] closes the panel.
 */
data class WordLookupActions(val onOpenShortcut: (DictionaryShortcut) -> Unit, val onDismiss: () -> Unit)

/**
 * The word lookup panel (issue #19): the signature interaction. It opens on the
 * word the reader tapped with the offline result already in it, and always
 * offers the reference-site shortcuts beside that result — so with no pack and
 * no network it still degrades to the shortcuts and an honest message rather
 * than an empty panel. The reader stays in place behind it, so closing returns
 * to the exact reading position (docs/ux-design-specification.md, "Success
 * Criteria").
 *
 * Since issue #21 it also carries the pronunciation control, and since issue #22
 * the [vocabulary] controls: one tap on Save stores the word with its
 * translation, context sentence and level, and the selector moves a saved word
 * between levels — the reader's colour updating is the only confirmation.
 */
@Suppress("LongParameterList") // The panel's inputs are its result, word, shortcuts, actions and the two controls.
@Composable
fun WordLookupPanel(
    result: WordLookup,
    term: String,
    shortcuts: List<DictionaryShortcut>,
    actions: WordLookupActions,
    pronunciation: Pronunciation,
    vocabulary: VocabularyPanel = VocabularyPanel(entry = null, onSave = {}),
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Header(term, pronunciation.onSpeak, vocabulary, actions.onDismiss)
            SpeechMessage(pronunciation.result)
            when (result) {
                is WordLookup.Found -> Found(result, term)

                is WordLookup.NotInDictionary -> Text(
                    "\"${result.query}\" isn't in the offline dictionary.",
                    style = MaterialTheme.typography.bodyLarge,
                )

                is WordLookup.Unavailable -> Text(result.message, style = MaterialTheme.typography.bodyLarge)
            }
            Shortcuts(shortcuts, actions.onOpenShortcut)
            MasterySelector(vocabulary)
        }
    }
}

@Composable
private fun Header(term: String, onSpeak: () -> Unit, vocabulary: VocabularyPanel, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(term, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSpeak, modifier = Modifier.testTag(PRONOUNCE_TAG)) { Text("Listen") }
            TextButton(
                onClick = { vocabulary.onSave(vocabulary.level) },
                modifier = Modifier.testTag(SAVE_TAG),
            ) {
                Text(if (vocabulary.entry == null) "Save" else "Saved")
            }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

/**
 * The five-point selector (issue #22): tapping a level saves the word there, or
 * moves an already-saved word, so mastery can be changed after saving. The
 * current level is the filled chip. The chips are compact — the level's number
 * and a check for known — so all five fit one row (docs/ux-design-specification.md,
 * `MasterySelector`).
 */
@Composable
private fun MasterySelector(vocabulary: VocabularyPanel) {
    Text("Mastery", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MasteryLevel.entries.forEach { level ->
            if (level == vocabulary.level) {
                FilledTonalButton(
                    onClick = { vocabulary.onSave(level) },
                    modifier = Modifier.testTag(masteryTag(level)),
                ) { Text(level.label()) }
            } else {
                TextButton(
                    onClick = { vocabulary.onSave(level) },
                    modifier = Modifier.testTag(masteryTag(level)),
                ) { Text(level.label()) }
            }
        }
    }
}

/** A mastery level's compact chip label: its number, and a check for the known level. */
private fun MasteryLevel.label(): String = when (this) {
    MasteryLevel.UNKNOWN -> "0"
    MasteryLevel.FAMILIAR -> "1"
    MasteryLevel.RECOGNIZED -> "2"
    MasteryLevel.MASTERED -> "3"
    MasteryLevel.KNOWN -> "4"
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
