package app.lekto.settings

import app.lekto.dictionary.DictionaryUiState
import app.lekto.difficulty.DifficultyUiState

/**
 * What the settings screen renders: one state bundle per section. A later
 * section — reading preferences, say — is a new field here plus a new section
 * composable, not a redesign (issues #20, #28, #88).
 *
 * The **Reader level** and its per-language picker left for the **Languages**
 * surface (issue #147); the difficulty table's own state and download stay here
 * until they move there too (issue #148).
 */
data class SettingsUiState(
    val dictionary: DictionaryUiState = DictionaryUiState(),
    val vault: VaultUiState = VaultUiState(),
    val provider: ProviderUiState = ProviderUiState(),
    val sync: SyncUiState = SyncUiState(),
    val difficulty: DifficultyUiState = DifficultyUiState(),
)
