package app.lekto.language

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
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.OfferedDictionary
import app.lekto.core.language.OfferedLanguage
import app.lekto.dictionary.DictionaryUiState
import app.lekto.difficulty.DifficultyUiState
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey

// The per-language **Derived asset** controls of the **Languages** surface: the
// **Difficulty table** (issue #148) and the **Dictionary pack** (issue #149).
// Split from the surface itself so each control's state and its honest
// no-action case sit together, and the surface file stays within the rules.

/** The test tag on a language's **Difficulty table** download (issue #148). */
internal fun languageDifficultyDownloadTag(language: String): String = "languages-difficulty-download-$language"

/** The test tag on a language's difficulty failure dismiss (issue #148). */
internal fun languageDifficultyDismissTag(language: String): String = "languages-difficulty-dismiss-$language"

/** The test tag on a pair's **Dictionary pack** download (issue #149). */
internal fun languageDictionaryDownloadTag(artifact: String): String = "languages-dictionary-download-$artifact"

/** The test tag on a pair's dictionary failure dismiss (issue #149). */
internal fun languageDictionaryDismissTag(artifact: String): String = "languages-dictionary-dismiss-$artifact"

/**
 * The **Difficulty table** control for one language (issue #148): its state and
 * its download where the manifest publishes a table, an honest sentence and no
 * action where it does not.
 */
@Composable
internal fun DifficultyControl(offered: OfferedLanguage, difficulty: DifficultyUiState?, actions: LanguagesActions) {
    val texts = LocalAppTexts.current
    val artifact = offered.difficulty
    if (artifact == null) {
        Text(
            text = texts[TextKey.DIFFICULTY_NOT_PUBLISHED],
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val state = difficulty ?: DifficultyUiState()
    Text(texts.difficultyStatus(state.status), style = MaterialTheme.typography.bodyMedium)

    if (state.installing) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(texts[TextKey.DOWNLOADING_DIFFICULTY], style = MaterialTheme.typography.bodyMedium)
    }
    state.error?.let { message ->
        ArtifactError(texts.message(message), languageDifficultyDismissTag(offered.language)) {
            actions.onDismissDifficultyError(offered.language)
        }
    }

    Button(
        onClick = { actions.onDownloadDifficulty(offered.language, artifact) },
        enabled = !state.installing,
        modifier = Modifier.fillMaxWidth().testTag(languageDifficultyDownloadTag(offered.language)),
    ) {
        Text(
            if (state.status is DifficultyState.Ready) {
                texts[TextKey.DOWNLOAD_AGAIN]
            } else {
                texts[TextKey.DOWNLOAD_DIFFICULTY]
            },
        )
    }
}

/**
 * One language's **Dictionary pack** controls (issue #149): its state and its
 * download per published (Reading language × **Translation language**) pair, an
 * honest sentence and no action where the manifest publishes no pair.
 */
@Composable
internal fun DictionaryControls(
    offered: OfferedLanguage,
    dictionaries: Map<String, DictionaryUiState>,
    actions: LanguagesActions,
) {
    if (offered.dictionaries.isEmpty()) {
        Text(
            text = LocalAppTexts.current[TextKey.DICTIONARY_NOT_PUBLISHED],
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    offered.dictionaries.forEach { dictionary ->
        DictionaryControl(offered.language, dictionary, dictionaries[dictionary.name], actions)
    }
}

/**
 * One **Dictionary pack** (issue #149): labelled by its pair, naming both
 * languages, with its state and its download. The label is built here from the
 * two languages, so the surface never invents a target the manifest did not
 * publish.
 */
@Composable
private fun DictionaryControl(
    readingLanguage: String,
    dictionary: OfferedDictionary,
    state: DictionaryUiState?,
    actions: LanguagesActions,
) {
    val texts = LocalAppTexts.current
    val current = state ?: DictionaryUiState()
    Text(
        text = "${texts.languageName(readingLanguage)} → ${texts.languageName(dictionary.translationLanguage)}",
        style = MaterialTheme.typography.titleSmall,
    )
    Text(texts.dictionaryStatus(current.status), style = MaterialTheme.typography.bodyMedium)

    if (current.installing) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(texts[TextKey.DOWNLOADING_DICTIONARY], style = MaterialTheme.typography.bodyMedium)
    }
    current.error?.let { message ->
        ArtifactError(texts.message(message), languageDictionaryDismissTag(dictionary.name)) {
            actions.onDismissDictionaryError(dictionary.name)
        }
    }

    Button(
        onClick = {
            actions.onDownloadDictionary(readingLanguage, dictionary.translationLanguage, dictionary.name)
        },
        enabled = !current.installing,
        modifier = Modifier.fillMaxWidth().testTag(languageDictionaryDownloadTag(dictionary.name)),
    ) {
        Text(
            if (current.status is DictionaryPackState.Ready) {
                texts[TextKey.DOWNLOAD_AGAIN]
            } else {
                texts[TextKey.DOWNLOAD_DICTIONARY]
            },
        )
    }
}

/** One artifact's inline download failure, with its own dismiss. */
@Composable
private fun ArtifactError(message: String, tag: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss, modifier = Modifier.testTag(tag)) {
            Text(LocalAppTexts.current[TextKey.DISMISS])
        }
    }
}
