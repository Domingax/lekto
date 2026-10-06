package app.lekto.settings

import app.lekto.core.llm.LlmProvider

/**
 * The actions the settings screen raises: download the dictionary pack, open
 * attributions, dismiss a download failure, export or import the vault, dismiss
 * the vault's last outcome, and go back. The LLM provider's actions (issue
 * #88) carry the API key the field owns, so the secret never enters the screen's
 * state; the rest come from the screen's own controls. Bundled so the screen's
 * signature stays small as more settings land.
 */
data class SettingsActions(
    val onDownload: () -> Unit = {},
    val onOpenAttribution: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onExportVault: () -> Unit = {},
    val onImportVault: () -> Unit = {},
    val onDismissVaultMessage: () -> Unit = {},
    val onSelectProvider: (LlmProvider) -> Unit = {},
    val onModelChange: (String) -> Unit = {},
    val onBaseUrlChange: (String) -> Unit = {},
    val onSaveProvider: (String) -> Unit = {},
    val onTestProvider: (String) -> Unit = {},
    val onRemoveProviderKey: () -> Unit = {},
    val onDismissProviderResult: () -> Unit = {},
)
