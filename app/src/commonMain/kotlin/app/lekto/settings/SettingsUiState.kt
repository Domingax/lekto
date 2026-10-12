package app.lekto.settings

import app.lekto.core.CefrLevel
import app.lekto.core.difficulty.DifficultyState
import app.lekto.dictionary.DictionaryUiState

/**
 * What the settings screen renders: one state bundle per section. A later
 * section — reading preferences, say — is a new field here plus a new section
 * composable, not a redesign (issues #20, #28, #88 and #64).
 */
data class SettingsUiState(
    val dictionary: DictionaryUiState = DictionaryUiState(),
    val vault: VaultUiState = VaultUiState(),
    val provider: ProviderUiState = ProviderUiState(),
    val sync: SyncUiState = SyncUiState(),
    val readerLevel: ReaderLevelUiState = ReaderLevelUiState(),
)

/**
 * The reader-level section (issue #64; ADR-0030): the languages the reader has
 * a book in, the level declared in each, and the difficulty table's state.
 *
 * [languages] is what the screen offers a picker for — every language present in
 * the library, plus every language a level is already set for — so a reader can
 * declare a level for each language they read.
 */
data class ReaderLevelUiState(
    val languages: List<String> = emptyList(),
    val levels: Map<String, CefrLevel> = emptyMap(),
    val difficulty: DifficultyState = DifficultyState.NotInstalled,
    val installing: Boolean = false,
    val error: String? = null,
)
