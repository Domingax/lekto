package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import app.lekto.core.llm.LlmProvider
import app.lekto.core.theme.AppTheme
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.handCursor

/** The test tags the LLM provider section's controls carry, so a UI test can click exactly one. */
internal const val PROVIDER_PICKER_TAG: String = "settings-provider-picker"
internal const val MODEL_FIELD_TAG: String = "settings-provider-model"
internal const val BASE_URL_FIELD_TAG: String = "settings-provider-base-url"
internal const val API_KEY_FIELD_TAG: String = "settings-provider-api-key"
internal const val TEST_CONNECTION_TAG: String = "settings-provider-test"
internal const val PROVIDER_DISMISS_TAG: String = "settings-provider-dismiss"

/** The test tag on the way to the **Languages** surface, so a UI test can click exactly it. */
internal const val LANGUAGES_LINK_TAG: String = "settings-languages"

/** The test tag on the way to the **Appearance** screen, so a UI test can click exactly it. */
internal const val APPEARANCE_LINK_TAG: String = "settings-appearance"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar (issue #185). */
internal const val SETTINGS_TITLE_TAG: String = "settings-title"

/**
 * The settings screen: the app's own appearance (ADR-0036), the app's own language
 * (ADR-0034), the way to the **Languages** surface (issue #146), the vault's
 * export/import (issue #20), sync (issue #28) and the LLM provider (issue #88).
 * Each is its own section so later settings land without disturbing the ones
 * already here, and nothing on the screen gates the reader — the vault actions
 * exist only where a file picker is wired, sync only where a driver is, and the
 * model only where a provider is.
 *
 * The data the reader manages per language — the **Reader level**, the
 * **Difficulty table** and the **Dictionary pack** — lives on the **Languages**
 * surface, not here (issues #147, #148, #149), and the theme's four papers live on
 * the **Appearance** screen, so this screen names the current one and nothing more.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions = SettingsActions(),
    modifier: Modifier = Modifier,
) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Header()
        Section(texts[TextKey.SECTION_APPEARANCE]) { AppearanceSection(state.appTheme, actions) }
        Section(texts[TextKey.SECTION_APP_LANGUAGE]) {
            AppLanguageSection(state.appLanguage, actions.onSelectAppLanguage)
        }
        Section(texts[TextKey.SECTION_LANGUAGES]) { LanguagesSection(actions) }
        Section(texts[TextKey.SECTION_VAULT]) { VaultSection(state.vault, actions) }
        Section(texts[TextKey.SECTION_SYNC]) { SyncSection(state.sync, actions) }
        Section(texts[TextKey.SECTION_LLM_PROVIDER]) { ProviderSection(state.provider, actions) }
    }
}

/** One settings section: a title and its content, so every section is spaced alike. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

/**
 * The **Appearance** doorway (ADR-0036; docs/design.md §7.6): Settings names the
 * app's current paper and offers the screen that changes it. The four entries are
 * each shown on their own paper there, so a theme is chosen by looking at it.
 */
@Composable
private fun AppearanceSection(theme: AppTheme, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.APPEARANCE_DESCRIPTION],
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(texts.themeName(theme), style = MaterialTheme.typography.bodyMedium)
    TextButton(
        onClick = actions.onOpenAppearance,
        modifier = Modifier.handCursor().testTag(APPEARANCE_LINK_TAG),
    ) {
        Text(texts[TextKey.SECTION_APPEARANCE])
    }
}

/**
 * The **Languages** doorway (issue #146): a first-class destination, so it is
 * reachable from Settings as well as from the library's own header until the
 * navigation rework lands. It carries the reader's **Reader level**, each
 * language's **Difficulty table** and each published **Dictionary pack** state and
 * download (issues #147, #148, #149), so Settings holds only the app's own
 * configuration.
 */
@Composable
private fun LanguagesSection(actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.LANGUAGES_SECTION_DESCRIPTION],
        style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(
        onClick = actions.onOpenLanguages,
        modifier = Modifier.handCursor().testTag(LANGUAGES_LINK_TAG),
    ) {
        Text(texts[TextKey.LANGUAGES])
    }
}

@Composable
private fun VaultSection(state: VaultUiState, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.VAULT_SECTION_DESCRIPTION],
        style = MaterialTheme.typography.bodyMedium,
    )

    if (!state.available) {
        Text(texts[TextKey.VAULT_UNAVAILABLE], style = MaterialTheme.typography.bodyMedium)
    } else {
        state.message?.let { message -> Notice(texts.message(message), actions.onDismissVaultMessage) }
        state.error?.let { message -> Error(texts.message(message), actions.onDismissVaultMessage) }
        if (state.transferring) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    Button(
        onClick = actions.onExportVault,
        enabled = state.available && !state.transferring,
        modifier = Modifier.fillMaxWidth().handCursor(state.available && !state.transferring),
    ) { Text(texts[TextKey.EXPORT_VAULT]) }
    Button(
        onClick = actions.onImportVault,
        enabled = state.available && !state.transferring,
        modifier = Modifier.fillMaxWidth().handCursor(state.available && !state.transferring),
    ) { Text(texts[TextKey.IMPORT_VAULT]) }
}

/**
 * The **LLM provider** section (issues #88, #93; ADR-0022): choose one provider
 * preset — the picker starts empty, reading "Choose a provider" — or a custom base
 * URL, enter the **API key** in a masked field, and pick the model from the list
 * the provider offers. Choosing a provider loads that list automatically, and
 * opening the model field refreshes it with the key now typed, so there is no
 * separate action to press. The key is handed to the controller only to be stored
 * or tested — it never enters this screen's state. Save persists the app-private
 * configuration and the key; Test reports the provider's answer inline.
 */
@Composable
private fun ProviderSection(state: ProviderUiState, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.PROVIDER_SECTION_DESCRIPTION],
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!state.available) {
        Text(
            texts[TextKey.PROVIDER_UNAVAILABLE],
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    ProviderPicker(state.providers, state.selectedProvider, actions.onSelectProvider)
    val chosen = state.selectedProvider ?: return

    if (chosen == LlmProvider.CUSTOM) {
        ProviderField(
            value = state.config.customBaseUrl,
            onValueChange = actions.onBaseUrlChange,
            label = texts[TextKey.BASE_URL],
            tag = BASE_URL_FIELD_TAG,
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
    ProviderFooter(state, apiKey, actions)
}

/**
 * The model, as an editable exposed dropdown (issue #93): the provider's list
 * drops straight out of the field, and manual typing stays for a provider that
 * does not implement `/models`. Opening it refreshes the list with the key now
 * typed, so entering a key and then picking a model needs no separate action; a
 * failure or an unsupported list is reported inline through the shared status and
 * never blocks the reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelField(state: ProviderUiState, apiKey: String, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { open ->
                expanded = open
                if (open) actions.onLoadModels(apiKey)
            },
        ) {
            OutlinedTextField(
                value = state.config.model,
                onValueChange = actions.onModelChange,
                label = { Text(texts[TextKey.MODEL]) },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .testTag(MODEL_FIELD_TAG),
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

/** The stored-key control, the inline result and the section's two actions. */
@Composable
private fun ProviderFooter(state: ProviderUiState, apiKey: String, actions: SettingsActions) {
    StoredKey(state, actions)
    ProviderStatus(state, actions)
    ProviderActions(state, apiKey, actions)
}

/** The active provider's preset picker: the current choice, and the presets this platform can reach. */
@Composable
private fun ProviderPicker(providers: List<LlmProvider>, provider: LlmProvider?, onSelect: (LlmProvider) -> Unit) {
    val texts = LocalAppTexts.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.handCursor().testTag(PROVIDER_PICKER_TAG),
        ) {
            Text(provider?.label ?: texts[TextKey.CHOOSE_A_PROVIDER])
        }
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

/** One labelled single-line field (the model, or a custom provider's base URL). */
@Composable
private fun ProviderField(value: String, onValueChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** Whether a key is already stored for the active provider, and the way to remove it. */
@Composable
private fun StoredKey(state: ProviderUiState, actions: SettingsActions) {
    if (!state.hasStoredKey) return
    val texts = LocalAppTexts.current
    Text(texts[TextKey.KEY_STORED], style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = actions.onRemoveProviderKey, modifier = Modifier.handCursor()) {
        Text(texts[TextKey.REMOVE_KEY])
    }
}

/** The in-progress indicator and the last inline result, success or failure. */
@Composable
private fun ProviderStatus(state: ProviderUiState, actions: SettingsActions) {
    if (state.testing) LinearProgressIndicator(Modifier.fillMaxWidth())
    val result = state.result ?: return
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/** The section's two actions: persist the configuration and key, or test the connection. */
@Composable
private fun ProviderActions(state: ProviderUiState, apiKey: String, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { actions.onSaveProvider(apiKey) },
            enabled = !state.testing,
            modifier = Modifier.handCursor(!state.testing),
        ) { Text(texts[TextKey.SAVE]) }
        Button(
            onClick = { actions.onTestProvider(apiKey) },
            enabled = !state.testing,
            modifier = Modifier.handCursor(!state.testing).testTag(TEST_CONNECTION_TAG),
        ) { Text(texts[TextKey.TEST_CONNECTION]) }
    }
}

/** The screen's title alone: the shell's bar and the back stack replace its old way back (issue #185). */
@Composable
private fun Header() {
    Text(
        text = LocalAppTexts.current[TextKey.SETTINGS],
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag(SETTINGS_TITLE_TAG),
    )
}

@Composable
private fun Error(message: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
            Text(LocalAppTexts.current[TextKey.DISMISS])
        }
    }
}

@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
            Text(LocalAppTexts.current[TextKey.DISMISS])
        }
    }
}
