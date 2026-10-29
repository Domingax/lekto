package app.lekto.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.OfferedDictionary
import app.lekto.core.language.OfferedLanguage
import app.lekto.dictionary.DictionaryUiState
import app.lekto.difficulty.DifficultyUiState
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.TextAction

// The per-language **Derived asset** controls of the **Languages** surface: the
// **Difficulty table** (issue #148) and the **Dictionary pack** (issue #149).
// Since issue #215 each is a quiet line of state with its own text action, so a
// language row carries all its controls without a column of full-width buttons.

/** The test tag on a language's **Difficulty table** download (issue #148). */
internal fun languageDifficultyDownloadTag(language: String): String = "languages-difficulty-download-$language"

/** The test tag on a language's **Difficulty table** state (issue #215). */
internal fun languageDifficultyStateTag(language: String): String = "languages-difficulty-state-$language"

/** The test tag on a language's difficulty failure dismiss (issue #148). */
internal fun languageDifficultyDismissTag(language: String): String = "languages-difficulty-dismiss-$language"

/** The test tag on a pair's **Dictionary pack** download (issue #149). */
internal fun languageDictionaryDownloadTag(artifact: String): String = "languages-dictionary-download-$artifact"

/** The test tag on a pair's **Dictionary pack** state (issue #215). */
internal fun languageDictionaryStateTag(artifact: String): String = "languages-dictionary-state-$artifact"

/** The test tag on a pair's dictionary failure dismiss (issue #149). */
internal fun languageDictionaryDismissTag(artifact: String): String = "languages-dictionary-dismiss-$artifact"

/**
 * The **Difficulty table** control for one language (issue #148): its state in
 * the muted ink and its download where the manifest publishes a table, an honest
 * sentence and no action where it does not.
 */
@Composable
internal fun DifficultyControl(offered: OfferedLanguage, difficulty: DifficultyUiState?, actions: LanguagesActions) {
    val texts = LocalAppTexts.current
    val artifact = offered.difficulty
    if (artifact == null) {
        ArtifactLine(
            status = texts[TextKey.DIFFICULTY_NOT_PUBLISHED],
            stateTag = languageDifficultyStateTag(offered.language),
        )
        return
    }
    val state = difficulty ?: DifficultyUiState()
    ArtifactLine(
        status = texts.difficultyStatus(state.status),
        stateTag = languageDifficultyStateTag(offered.language),
        installing = state.installing,
        action = {
            DownloadAction(
                label = downloadLabel(
                    texts,
                    state.installing,
                    state.status !is DifficultyState.NotInstalled,
                    offered.difficultySizeBytes,
                ),
                enabled = !state.installing,
                tag = languageDifficultyDownloadTag(offered.language),
                onClickLabel = texts[TextKey.DOWNLOAD_DIFFICULTY],
                onClick = { actions.onDownloadDifficulty(offered.language, artifact) },
            )
        },
    )
    state.error?.let { message ->
        ArtifactError(texts.message(message), languageDifficultyDismissTag(offered.language)) {
            actions.onDismissDifficultyError(offered.language)
        }
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
        ArtifactLine(
            status = LocalAppTexts.current[TextKey.DICTIONARY_NOT_PUBLISHED],
            stateTag = languageDictionaryStateTag(offered.language),
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
    val pair = "${texts.languageName(readingLanguage)} → ${texts.languageName(dictionary.translationLanguage)}"
    ArtifactLine(
        status = "$pair · ${texts.dictionaryStatus(current.status)}",
        stateTag = languageDictionaryStateTag(dictionary.name),
        installing = current.installing,
        action = {
            DownloadAction(
                label = downloadLabel(
                    texts,
                    current.installing,
                    current.status !is DictionaryPackState.NotInstalled,
                    dictionary.sizeBytes,
                ),
                enabled = !current.installing,
                tag = languageDictionaryDownloadTag(dictionary.name),
                onClickLabel = texts[TextKey.DOWNLOAD_DICTIONARY],
                onClick = {
                    actions.onDownloadDictionary(readingLanguage, dictionary.translationLanguage, dictionary.name)
                },
            )
        },
    )
    current.error?.let { message ->
        ArtifactError(texts.message(message), languageDictionaryDismissTag(dictionary.name)) {
            actions.onDismissDictionaryError(dictionary.name)
        }
    }
}

/**
 * One artifact's line: its state in the muted ink, its download action at the
 * trailing edge, and the progress under it while a download runs. A dictionary
 * names its pair before the state; the difficulty table is the language's own,
 * so it needs no label.
 */
@Composable
private fun ArtifactLine(
    status: String,
    stateTag: String,
    action: (@Composable () -> Unit)? = null,
    installing: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag(stateTag),
            )
            action?.invoke()
        }
        if (installing) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = LektoSpacing.InsideLabelPair))
        }
    }
}

/** One artifact's download as a quiet text action (issue #215): no filled button, no full-width bar. */
@Composable
@Suppress("LongParameterList") // One artifact's word, its state, its tag and its accessible name are independent.
private fun DownloadAction(
    label: String,
    enabled: Boolean,
    tag: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    TextAction(
        onClick = onClick,
        enabled = enabled,
        onClickLabel = onClickLabel,
        modifier = Modifier.testTag(tag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** The action's word: in flight, a re-download, or the first one with its size when the manifest names it. */
private fun downloadLabel(texts: AppTexts, installing: Boolean, reinstall: Boolean, sizeBytes: Long?): String = when {
    installing -> texts[TextKey.DOWNLOADING]
    reinstall -> texts[TextKey.DOWNLOAD_AGAIN]
    sizeBytes != null -> texts.message(AppMessage.of(TextKey.DOWNLOAD_SIZE, artifactSizeText(texts, sizeBytes)))
    else -> texts[TextKey.DOWNLOAD]
}

/** One artifact's inline download failure, with its own dismiss. */
@Composable
private fun ArtifactError(message: String, tag: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextAction(onClick = onDismiss, modifier = Modifier.testTag(tag)) {
            Text(LocalAppTexts.current[TextKey.DISMISS], style = MaterialTheme.typography.bodySmall)
        }
    }
}
