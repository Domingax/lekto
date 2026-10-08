package app.lekto.settings

import app.lekto.core.llm.LlmProvider

/**
 * The actions the settings screen raises: download the dictionary pack, open
 * attributions, dismiss a download failure, export or import the vault, dismiss
 * the vault's last outcome, go back, and the two sections that carry a secret —
 * the LLM provider (issue #88) and sync (issue #28). The provider and sync
 * actions carry the secret the field owns, so a key or a password never enters
 * the screen's state; the rest come from the screen's own controls. Bundled so
 * the screen's signature stays small as more settings land.
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
    val onSyncHostChange: (String) -> Unit = {},
    val onSyncUsernameChange: (String) -> Unit = {},
    val onSaveSync: (String) -> Unit = {},
    val onTestSync: (String) -> Unit = {},
    val onEnableSync: () -> Unit = {},
    val onDisableSync: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onDisconnectSync: () -> Unit = {},
    val onDismissSyncResult: () -> Unit = {},
)
