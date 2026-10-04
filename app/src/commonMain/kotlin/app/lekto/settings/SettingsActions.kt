package app.lekto.settings

/**
 * The actions the settings screen raises: download the pack, open attributions,
 * dismiss a download failure, and go back. Bundled so the screen's signature
 * stays small as more settings land.
 */
data class SettingsActions(
    val onDownload: () -> Unit = {},
    val onOpenAttribution: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)
