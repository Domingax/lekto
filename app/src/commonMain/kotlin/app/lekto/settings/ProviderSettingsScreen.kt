package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.core.llm.LlmProvider
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons

/** The test tags the LLM provider screen's controls carry, so a UI test can click exactly one. */
internal const val PROVIDER_PICKER_TAG: String = "settings-provider-picker"
internal const val MODEL_FIELD_TAG: String = "settings-provider-model"
internal const val BASE_URL_FIELD_TAG: String = "settings-provider-base-url"
internal const val API_KEY_FIELD_TAG: String = "settings-provider-api-key"
internal const val TEST_CONNECTION_TAG: String = "settings-provider-test"
internal const val PROVIDER_DISMISS_TAG: String = "settings-provider-dismiss"

/** The test tag on the way back to Settings, so a UI test can click exactly it. */
internal const val PROVIDER_BACK_TAG: String = "provider-settings-back"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar. */
internal const val PROVIDER_SETTINGS_TITLE_TAG: String = "provider-settings-title"

/**
 * The **LLM provider** screen (issues #88, #93, #213; ADR-0022;
 * docs/design.md §7.5, §7.6): the provider preset, the **API key**, the model and
 * the actions, on a screen of their own rather than buried in Settings.
 *
 * Choosing a provider loads that provider's model list automatically, and
 * opening the model field refreshes it with the key now typed, so there is no
 * separate action to press. The model and a custom base URL apply on change; the
 * key keeps an explicit **Save**, because writing it is a secret write, and
 * **Test connection** is the screen's one filled action. The key is handed to
 * the controller only to be stored or tested — it never enters this screen's
 * state. The screen says where the key lives: the device's keychain, never the
 * vault.
 */
@Composable
@Suppress("LongMethod") // The screen's fields read top to bottom in the order docs/design.md §7.6 names them.
fun ProviderSettingsScreen(
    state: ProviderUiState,
    actions: SettingsActions = SettingsActions(),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenBlocks),
    ) {
        SettingsFormHeader(
            title = texts[TextKey.SECTION_LLM_PROVIDER],
            backTag = PROVIDER_BACK_TAG,
            titleTag = PROVIDER_SETTINGS_TITLE_TAG,
            onBack = onBack,
        )
        Text(texts[TextKey.PROVIDER_SECTION_DESCRIPTION], style = MaterialTheme.typography.bodyMedium)
        if (!state.available) {
            Text(texts[TextKey.PROVIDER_UNAVAILABLE], style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        ProviderPicker(state.providers, state.selectedProvider, actions.onSelectProvider)
        val chosen = state.selectedProvider ?: return@Column

        if (chosen == LlmProvider.CUSTOM) {
            FormField(
                value = state.config.customBaseUrl,
                onValueChange = actions.onBaseUrlChange,
                label = texts[TextKey.BASE_URL],
                modifier = Modifier.fillMaxWidth().testTag(BASE_URL_FIELD_TAG),
            )
        }
        // A key typed for one provider must not survive a switch to another, so the
        // field resets with the provider; a keyless Ollama shows no field at all.
        var apiKey by remember(state.config.provider) { mutableStateOf("") }
        if (chosen.requiresKey) {
            SecretField(
                value = apiKey,
                onValueChange = { typed -> apiKey = typed },
                label = texts[TextKey.API_KEY],
                modifier = Modifier.fillMaxWidth().testTag(API_KEY_FIELD_TAG),
            )
        }

        ModelField(state, apiKey, actions)
        StoredKey(state, actions)
        ProviderStatus(state, actions)
        ProviderActions(state, apiKey, actions)
    }
}

/**
 * The model, as an editable exposed dropdown (issue #93): the provider's list
 * drops straight out of the form row, and manual typing stays for a provider that
 * does not implement `/models`. Opening it refreshes the list with the key now
 * typed, so entering a key and then picking a model needs no separate action; a
 * failure or an unsupported list is reported inline through the shared status and
 * never blocks the reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // The model field, its menu, its progress and its hint are one control.
private fun ModelField(state: ProviderUiState, apiKey: String, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { open ->
                expanded = open
                if (open) actions.onLoadModels(apiKey)
            },
        ) {
            FormField(
                value = state.config.model,
                onValueChange = actions.onModelChange,
                label = texts[TextKey.MODEL],
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .testTag(MODEL_FIELD_TAG),
                trailing = {
                    Icon(
                        imageVector = LektoIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.models.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            actions.onModelChange(option)
                        },
                        modifier = Modifier.handCursor(),
                    )
                }
            }
        }
        if (state.loadingModels) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.modelsHint?.let { hint -> Text(texts.message(hint), style = MaterialTheme.typography.bodyMedium) }
    }
}

/** The active provider's preset picker: the current choice, and the presets this platform can reach. */
@Composable
private fun ProviderPicker(providers: List<LlmProvider>, provider: LlmProvider?, onSelect: (LlmProvider) -> Unit) {
    val texts = LocalAppTexts.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        FormRow(
            label = texts[TextKey.PROVIDER],
            value = provider?.label ?: texts[TextKey.CHOOSE_A_PROVIDER],
            tag = PROVIDER_PICKER_TAG,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.handCursor(),
                )
            }
        }
    }
}

/** Whether a key is already stored for the active provider, and the way to remove it. */
@Composable
private fun StoredKey(state: ProviderUiState, actions: SettingsActions) {
    if (!state.hasStoredKey) return
    val texts = LocalAppTexts.current
    Text(texts[TextKey.KEY_STORED], style = MaterialTheme.typography.bodyMedium)
    IrreversibleAction(text = texts[TextKey.REMOVE_KEY], onClick = actions.onRemoveProviderKey)
}

/** The in-progress indicator and the last inline result, success or failure. */
@Composable
private fun ProviderStatus(state: ProviderUiState, actions: SettingsActions) {
    if (state.testing) LinearProgressIndicator(Modifier.fillMaxWidth())
    val result = state.result ?: return
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
        Text(
            texts.message(result.message),
            color = when (result) {
                is ProviderResult.Success -> MaterialTheme.colorScheme.primary
                is ProviderResult.Failure -> MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = actions.onDismissProviderResult,
            modifier = Modifier.handCursor().testTag(PROVIDER_DISMISS_TAG),
        ) {
            Text(texts[TextKey.DISMISS])
        }
    }
}

/**
 * The screen's actions: **Test connection** is the one filled button, and the
 * key's explicit **Save** is its outlined sibling. Both are disabled while a
 * test is in flight.
 */
@Composable
private fun ProviderActions(state: ProviderUiState, apiKey: String, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Button(
        onClick = { actions.onTestProvider(apiKey) },
        enabled = !state.testing,
        shape = LektoRadii.Control,
        modifier = Modifier.fillMaxWidth().handCursor(!state.testing).testTag(TEST_CONNECTION_TAG),
    ) { Text(texts[TextKey.TEST_CONNECTION]) }
    OutlinedButton(
        onClick = { actions.onSaveProvider(apiKey) },
        enabled = !state.testing,
        shape = LektoRadii.Control,
        modifier = Modifier.fillMaxWidth().handCursor(!state.testing),
    ) { Text(texts[TextKey.SAVE]) }
}
