package app.lekto.settings

import app.lekto.dictionary.DictionaryUiState

/**
 * What the settings screen renders: one state bundle per section. A later
 * section — reading preferences, say — is a new field here plus a new section
 * composable, not a redesign (issues #20, #28 and #88).
 */
data class SettingsUiState(
    val dictionary: DictionaryUiState = DictionaryUiState(),
    val vault: VaultUiState = VaultUiState(),
    val provider: ProviderUiState = ProviderUiState(),
    val sync: SyncUiState = SyncUiState(),
)
