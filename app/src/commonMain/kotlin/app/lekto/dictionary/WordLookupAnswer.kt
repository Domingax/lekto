package app.lekto.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.speech.SpeechResult
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing

/** The minimum column a sense's number occupies, so definitions start on the same line. */
private val NumberWidth = 24.dp

/** The test tag on a sense's number, so a layout test can read it apart from the level chips. */
internal fun senseNumberTag(number: Int): String = "lookup-sense-$number"

/** The honest outcome of the last pronunciation attempt: a failure is a sentence, success is silent. */
@Composable
internal fun SpeechMessage(speech: SpeechResult?) {
    val texts = LocalAppTexts.current
    val message = when (speech) {
        null, is SpeechResult.Spoken -> null

        is SpeechResult.NoVoice -> speech.language?.let { language ->
            texts.message(AppMessage.of(TextKey.NO_VOICE_FOR_LANGUAGE, language))
        } ?: texts[TextKey.NO_VOICE_FOR_UNKNOWN_LANGUAGE]

        is SpeechResult.Unavailable -> texts.coreMessage(speech.message)
    }
    message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.InsideLabelPair),
        )
    }
}

/** The offline answer, or the honest message when there is none. */
@Composable
internal fun Answer(result: WordLookup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.BetweenRows),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        when (result) {
            is WordLookup.Found -> Senses(result.entries)

            is WordLookup.NotInDictionary -> Text(
                text = LocalAppTexts.current.message(AppMessage.of(TextKey.NOT_IN_DICTIONARY, result.query)),
                style = MaterialTheme.typography.bodyLarge,
            )

            is WordLookup.Unavailable -> Text(
                text = LocalAppTexts.current.coreMessage(result.message),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** The senses, numbered across the entries, each with its translation under it when the pack has one. */
@Composable
private fun Senses(entries: List<DictionaryEntry>) {
    var number = 0
    entries.forEach { entry ->
        if (entries.size > 1) entry.partOfSpeech?.let { PartOfSpeech(it) }
        entry.senses.forEach { sense ->
            number += 1
            Sense(number, sense)
        }
    }
}

/** The one place a multi-entry word says which reading the senses below it belong to. */
@Composable
private fun PartOfSpeech(partOfSpeech: String) {
    Text(
        text = partOfSpeech,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One sense: its number, the definition, and the pack's translation under it in the muted ink. */
@Composable
private fun Sense(number: Int, sense: DictionarySense) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.widthIn(min = NumberWidth).testTag(senseNumberTag(number)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
            Text(sense.definition, style = MaterialTheme.typography.bodyLarge)
            sense.translations.forEach { translation ->
                Text(
                    text = "→ $translation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
