package app.lekto.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.TranslationShortcut
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey

/** The test tag on the translation shortcut, so a UI test can click exactly it. */
internal const val TRANSLATION_SHORTCUT_TAG: String = "lookup-translation-shortcut"

/** The test tag on the streamed translation (or its inline failure), so a test can watch it. */
internal const val TRANSLATION_RESULT_TAG: String = "lookup-translation-result"

/** The test tag on the no-provider prompt's link to settings. */
internal const val TRANSLATION_SETTINGS_TAG: String = "lookup-translation-settings"

/** The test tag on the control that reveals what is sent to the provider. */
internal const val TRANSLATION_DISCLOSURE_TAG: String = "lookup-translation-disclosure"

/**
 * The phrase panel's actions (issue #87): [onOpenShortcut] hands the service's
 * prefilled page to the platform browser, and [onDismiss] closes the panel.
 */
data class PhraseLookupActions(val onOpenShortcut: (TranslationShortcut) -> Unit, val onDismiss: () -> Unit)

/**
 * The lookup panel in **phrase mode** (issues #87, #89): it opens on the phrase
 * the reader selected, streams a translation from the connected **LLM provider**
 * into the panel, and always offers the zero-configuration **Translation
 * shortcut** — the service's prefilled page, handed to the platform browser.
 *
 * The panel is honest about the two working modes: the provider branch keeps the
 * translation the focus and discloses exactly what leaves the device
 * ([PhraseTranslationUiState.context]) behind a control the reader can open, and
 * without a provider it shows a non-blocking prompt beside the shortcut, never a
 * dead action. A provider failure is rendered inline, so reading is never
 * interrupted (ADR-0022). Like a Dictionary shortcut, Lekto never embeds or
 * scrapes the service.
 */
@Suppress("LongParameterList") // The panel's inputs are independent; a bundle would only hide them.
@Composable
fun PhraseLookupPanel(
    phrase: String,
    shortcut: TranslationShortcut?,
    actions: PhraseLookupActions,
    translation: PhraseTranslationUiState = PhraseTranslationUiState(),
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(phrase, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = actions.onDismiss) { Text(LocalAppTexts.current[TextKey.CLOSE]) }
            }
            TranslationSection(translation, onOpenSettings)
            ShortcutSection(shortcut, actions)
        }
    }
}

/** The provider branch of the panel: a streamed translation, its inline failure, or the no-provider prompt. */
@Composable
private fun TranslationSection(translation: PhraseTranslationUiState, onOpenSettings: () -> Unit) {
    when (val state = translation.state) {
        PhraseTranslationState.NoProvider -> NoProviderPrompt(onOpenSettings)

        is PhraseTranslationState.Streaming -> {
            StreamingResult(state.text)
            TranslationDisclosure(translation)
        }

        is PhraseTranslationState.Done -> {
            DoneResult(state.text)
            TranslationDisclosure(translation)
        }

        is PhraseTranslationState.Failed -> {
            FailureResult(state.message)
            // The phrase was still sent, so keep the disclosure reachable.
            TranslationDisclosure(translation)
        }
    }
}

/** The non-blocking prompt shown with no usable provider, with a link to settings beside the shortcut. */
@Composable
private fun NoProviderPrompt(onOpenSettings: () -> Unit) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.CONNECT_PROVIDER_PROMPT],
        style = MaterialTheme.typography.bodyLarge,
    )
    TextButton(
        onClick = onOpenSettings,
        modifier = Modifier.testTag(TRANSLATION_SETTINGS_TAG),
    ) {
        Text(texts[TextKey.OPEN_SETTINGS])
    }
}

/** The in-progress translation, or a placeholder until the first delta arrives. */
@Composable
private fun StreamingResult(text: String) {
    if (text.isEmpty()) {
        Text(LocalAppTexts.current[TextKey.TRANSLATING], style = MaterialTheme.typography.bodyLarge)
    } else {
        TranslationResult(text)
    }
}

/** The finished translation, or an honest message when the provider streamed an empty answer. */
@Composable
private fun DoneResult(text: String) {
    if (text.isBlank()) {
        TranslationResult(LocalAppTexts.current[TextKey.PROVIDER_RETURNED_NO_TRANSLATION])
    } else {
        TranslationResult(text)
    }
}

/** The inline failure, tagged so a test can read it apart from the phrase title. */
@Composable
private fun FailureResult(message: AppMessage) {
    Text(
        LocalAppTexts.current.message(message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.testTag(TRANSLATION_RESULT_TAG),
    )
}

/**
 * The disclosure of what leaves the device, collapsed to a single control so the
 * translation stays the panel's focus: on demand it names the provider and shows
 * the exact text sent (issue #89). It resets to collapsed for a new selection.
 */
@Composable
private fun TranslationDisclosure(translation: PhraseTranslationUiState) {
    val texts = LocalAppTexts.current
    var expanded by remember(translation.context) { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.testTag(TRANSLATION_DISCLOSURE_TAG),
    ) {
        Text(texts[if (expanded) TextKey.HIDE_WHAT_IS_SENT else TextKey.WHAT_IS_SENT])
    }
    if (expanded) {
        Text(
            texts.message(
                AppMessage.of(
                    TextKey.SENDS_SELECTION_TO,
                    translation.provider ?: texts[TextKey.THE_PROVIDER],
                ),
            ),
            style = MaterialTheme.typography.labelMedium,
        )
        if (translation.context.isNotBlank()) {
            Text(translation.context, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The streamed translation text, tagged so a test can read it apart from the phrase title. */
@Composable
private fun TranslationResult(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.testTag(TRANSLATION_RESULT_TAG),
    )
}

/** The zero-configuration Translation shortcut, or an honest message when the phrase cannot be addressed. */
@Composable
private fun ShortcutSection(shortcut: TranslationShortcut?, actions: PhraseLookupActions) {
    val texts = LocalAppTexts.current
    if (shortcut == null) {
        Text(
            texts[TextKey.NO_TRANSLATION_SERVICE],
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        Text(texts[TextKey.TRANSLATE_ONLINE], style = MaterialTheme.typography.labelLarge)
        TextButton(
            onClick = { actions.onOpenShortcut(shortcut) },
            modifier = Modifier.testTag(TRANSLATION_SHORTCUT_TAG),
        ) {
            Text(shortcut.source.label)
        }
    }
}
