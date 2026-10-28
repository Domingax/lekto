package app.lekto.settings

import app.lekto.core.llm.LlmProvider

/**
 * The actions the settings surface raises: choose the app's own language (ADR-0034),
 * export or import the vault, dismiss the vault's last outcome, the two screens
 * that carry a secret — the LLM provider (issues #88, #213) and the vault and sync
 * (issues #28, #213) — and the ways to the **Appearance** (ADR-0036) and
 * **Languages** surfaces. Each secret screen has one **Connection** action that
 * carries the secret the field owns, so a key or a password never enters a
 * screen's state; the rest come from the screen's own controls. Bundled so every
 * screen's signature stays small.
 *
 * Settings is a section of the shell, so it has no back action: the bar and the
 * back stack replace it (issue #185).
 *
 * The **Reader level** picker, the **Difficulty table**'s download and the
 * **Dictionary pack** control are no longer here: all moved to the **Languages**
 * surface (issues #147, #148, #149), which also carries the way to attribution.
 */
data class SettingsActions(
    val onOpenAppearance: () -> Unit = {},
    val onOpenLanguages: () -> Unit = {},
    val onOpenProvider: () -> Unit = {},
    val onOpenVault: () -> Unit = {},
    val onSelectAppLanguage: (String) -> Unit = {},
    val onExportVault: () -> Unit = {},
    val onImportVault: () -> Unit = {},
    val onDismissVaultMessage: () -> Unit = {},
    val onSelectProvider: (LlmProvider) -> Unit = {},
    val onModelChange: (String) -> Unit = {},
    val onBaseUrlChange: (String) -> Unit = {},
    val onLoadModels: (String) -> Unit = {},
    val onConnectProvider: (String) -> Unit = {},
    val onRemoveProviderKey: () -> Unit = {},
    val onSyncHostChange: (String) -> Unit = {},
    val onSyncUsernameChange: (String) -> Unit = {},
    val onConnectSync: (String) -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onDisconnectSync: () -> Unit = {},
)
