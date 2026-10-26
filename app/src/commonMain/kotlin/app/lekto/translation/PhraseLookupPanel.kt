package app.lekto.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import app.lekto.core.dictionary.TranslationShortcut
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.LookupCloseButton
import app.lekto.ui.LookupHeader
import app.lekto.ui.LookupSheet
import app.lekto.ui.SheetDivider
import app.lekto.ui.handCursor

/** The test tag on the phrase sheet itself, so a layout test can read how much of the screen it takes. */
internal const val PHRASE_PANEL_TAG: String = "phrase-lookup-panel"

/** The test tag on the phrase sheet's close action. */
internal const val PHRASE_CLOSE_TAG: String = "phrase-lookup-close"

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
 * The lookup panel in **phrase mode** (issues #87, #89, #211): it opens on the
 * phrase the reader selected, streams a translation from the connected **LLM
 * provider** into the panel, and always offers the zero-configuration
 * **Translation shortcut** — the service's prefilled page, handed to the platform
 * browser.
 *
 * It is the word panel's sibling on the same sheet shell (docs/design.md §7.3):
 * the phrase in the serif with the close icon, then the answer in the body size.
 * The sheet is capped at [app.lekto.ui.LOOKUP_SHEET_MAX_HEIGHT_FRACTION] of the
 * screen and its body scrolls, so a long phrase or a long translation never
 * pushes the shortcut past the edge.
 *
 * The panel is honest about the two working modes: the provider branch keeps the
 * translation the focus and discloses exactly what leaves the device
 * ([PhraseTranslationUiState.context]) behind a control the reader can open, and
 * without a provider it shows a non-blocking prompt beside the shortcut, never a
 * dead action. A provider failure is rendered inline, so reading is never
 * interrupted (ADR-0022). The sheet swallows taps that land on it, so only a tap
 * truly outside it — which its [app.lekto.ui.DismissScrim] handles — dismisses it;
 * a tap on the reader behind it then never reaches a word. Like a Dictionary
 * shortcut, Lekto never embeds or scrapes the service.
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
    LookupSheet(
        modifier = modifier,
        sheetModifier = Modifier.testTag(PHRASE_PANEL_TAG),
    ) {
        // The phrase and the answer share one scroll and sit under the sheet's
        // cap; the shortcut stays pinned below it, so the one answer that needs
        // nothing configured is always reachable.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            PhraseHeader(phrase, actions.onDismiss)
            SheetDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.BetweenRows),
                verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
            ) {
                TranslationSection(translation, onOpenSettings)
            }
        }
        ShortcutSection(shortcut, actions)
    }
}

/**
 * The phrase in the serif with the close as an icon (docs/design.md §3, §7.3):
 * the same header grammar as the word panel, so the two sheets cannot drift
 * apart. A long phrase wraps instead of clipping.
 */
@Composable
private fun PhraseHeader(phrase: String, onDismiss: () -> Unit) {
    LookupHeader {
        Text(
            text = phrase,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f).padding(bottom = LektoSpacing.BetweenRelatedLines),
        )
        LookupCloseButton(onDismiss = onDismiss, tag = PHRASE_CLOSE_TAG)
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
        modifier = Modifier.handCursor().testTag(TRANSLATION_SETTINGS_TAG),
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
        modifier = Modifier.handCursor().testTag(TRANSLATION_DISCLOSURE_TAG),
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

/**
 * The pinned footer: the zero-configuration Translation shortcut, or an honest
 * message when the phrase cannot be addressed. It sits outside the body's scroll,
 * so a long translation never hides the answer that needs nothing configured.
 */
@Composable
private fun ShortcutSection(shortcut: TranslationShortcut?, actions: PhraseLookupActions) {
    val texts = LocalAppTexts.current
    SheetDivider()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.BetweenRows),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
    ) {
        if (shortcut == null) {
            Text(
                texts[TextKey.NO_TRANSLATION_SERVICE],
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                texts[TextKey.TRANSLATE_ONLINE],
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { actions.onOpenShortcut(shortcut) },
                modifier = Modifier.handCursor().testTag(TRANSLATION_SHORTCUT_TAG),
            ) {
                Text(shortcut.source.label)
            }
        }
    }
}
