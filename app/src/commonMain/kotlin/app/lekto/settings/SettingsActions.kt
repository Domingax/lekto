package app.lekto.settings

import app.lekto.core.llm.LlmProvider

/**
 * The actions the settings screen raises: choose the app's own language (ADR-0034),
 * export or import the vault, dismiss the vault's last outcome, go back, the two
 * sections that carry a secret — the LLM provider (issue #88) and sync (issue #28)
 * — and the way to the **Languages** surface. The provider and sync actions carry
 * the secret the field owns, so a key or a password never enters the screen's
 * state; the rest come from the screen's own controls. Bundled so the screen's
 * signature stays small as more settings land.
 *
 * The **Reader level** picker, the **Difficulty table**'s download and the
 * **Dictionary pack** control are no longer here: all moved to the **Languages**
 * surface (issues #147, #148, #149), which also carries the way to attribution.
 */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onOpenLanguages: () -> Unit = {},
    val onSelectAppLanguage: (String) -> Unit = {},
    val onExportVault: () -> Unit = {},
    val onImportVault: () -> Unit = {},
    val onDismissVaultMessage: () -> Unit = {},
    val onSelectProvider: (LlmProvider) -> Unit = {},
    val onModelChange: (String) -> Unit = {},
    val onBaseUrlChange: (String) -> Unit = {},
    val onLoadModels: (String) -> Unit = {},
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
