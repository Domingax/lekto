package app.lekto.settings

/**
 * The actions the settings screen raises: download the dictionary pack, open
 * attributions, dismiss a download failure, export or import the vault, dismiss
 * the vault's last outcome, and go back. Bundled so the screen's signature stays
 * small as more settings land.
 */
data class SettingsActions(
    val onDownload: () -> Unit = {},
    val onOpenAttribution: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onExportVault: () -> Unit = {},
    val onImportVault: () -> Unit = {},
    val onDismissVaultMessage: () -> Unit = {},
)
