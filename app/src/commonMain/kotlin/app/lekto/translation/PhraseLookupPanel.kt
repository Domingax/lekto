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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.TranslationShortcut

/** The test tag on the translation shortcut, so a UI test can click exactly it. */
internal const val TRANSLATION_SHORTCUT_TAG: String = "lookup-translation-shortcut"

/** The test tag on the streamed translation (or its inline failure), so a test can watch it. */
internal const val TRANSLATION_RESULT_TAG: String = "lookup-translation-result"

/** The test tag on the no-provider prompt's link to settings. */
internal const val TRANSLATION_SETTINGS_TAG: String = "lookup-translation-settings"

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
 * The panel is honest about the two working modes: the provider branch discloses
 * exactly what leaves the device ([PhraseTranslationUiState.context]), and
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
                TextButton(onClick = actions.onDismiss) { Text("Close") }
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
            TranslationDisclosure(translation)
            StreamingResult(state.text)
        }

        is PhraseTranslationState.Done -> {
            TranslationDisclosure(translation)
            DoneResult(state.text)
        }

        is PhraseTranslationState.Failed -> {
            // The phrase was still sent, so keep disclosing what left the device.
            TranslationDisclosure(translation)
            FailureResult(state.message)
        }
    }
}

/** The non-blocking prompt shown with no usable provider, with a link to settings beside the shortcut. */
@Composable
private fun NoProviderPrompt(onOpenSettings: () -> Unit) {
    Text(
        "Connect an LLM provider in settings to translate phrases here.",
        style = MaterialTheme.typography.bodyLarge,
    )
    TextButton(
        onClick = onOpenSettings,
        modifier = Modifier.testTag(TRANSLATION_SETTINGS_TAG),
    ) {
        Text("Open settings")
    }
}

/** The in-progress translation, or a placeholder until the first delta arrives. */
@Composable
private fun StreamingResult(text: String) {
    if (text.isEmpty()) {
        Text("Translating…", style = MaterialTheme.typography.bodyLarge)
    } else {
        TranslationResult(text)
    }
}

/** The finished translation, or an honest message when the provider streamed an empty answer. */
@Composable
private fun DoneResult(text: String) {
    if (text.isBlank()) {
        TranslationResult("The provider returned no translation.")
    } else {
        TranslationResult(text)
    }
}

/** The inline failure, tagged so a test can read it apart from the phrase title. */
@Composable
private fun FailureResult(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.testTag(TRANSLATION_RESULT_TAG),
    )
}

/** The line that says who receives the phrase and the exact text that leaves the device. */
@Composable
private fun TranslationDisclosure(translation: PhraseTranslationUiState) {
    Text(
        "Sends your selection and its sentence to ${translation.provider ?: "the provider"}.",
        style = MaterialTheme.typography.labelMedium,
    )
    if (translation.context.isNotBlank()) {
        Text(translation.context, style = MaterialTheme.typography.bodySmall)
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
    if (shortcut == null) {
        Text(
            "No translation service is available for this phrase.",
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        Text("Translate online", style = MaterialTheme.typography.labelLarge)
        TextButton(
            onClick = { actions.onOpenShortcut(shortcut) },
            modifier = Modifier.testTag(TRANSLATION_SHORTCUT_TAG),
        ) {
            Text(shortcut.source.label)
        }
    }
}
