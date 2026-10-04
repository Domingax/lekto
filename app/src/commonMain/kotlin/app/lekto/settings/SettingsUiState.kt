package app.lekto.settings

import app.lekto.dictionary.DictionaryUiState

/**
 * What the settings screen renders: one state bundle per section. A later
 * section — AI, sync, reading preferences — is a new field here plus a new
 * section composable, not a redesign (issue #20).
 */
data class SettingsUiState(
    val dictionary: DictionaryUiState = DictionaryUiState(),
    val vault: VaultUiState = VaultUiState(),
)
