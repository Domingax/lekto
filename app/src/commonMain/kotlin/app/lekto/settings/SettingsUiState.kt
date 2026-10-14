package app.lekto.settings

import app.lekto.dictionary.DictionaryUiState

/**
 * What the settings screen renders: one state bundle per section. A later
 * section — reading preferences, say — is a new field here plus a new section
 * composable, not a redesign (issues #20, #28, #88).
 *
 * The **Reader level** and the **Difficulty table**'s state and download left for
 * the **Languages** surface (issues #147, #148); this screen holds only the app's
 * own configuration and the **Dictionary pack** (issue #149 moves that too).
 */
data class SettingsUiState(
    val dictionary: DictionaryUiState = DictionaryUiState(),
    val vault: VaultUiState = VaultUiState(),
    val provider: ProviderUiState = ProviderUiState(),
    val sync: SyncUiState = SyncUiState(),
)
