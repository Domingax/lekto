package app.lekto.settings

import app.lekto.core.language.AppLanguages

/**
 * What the settings screen renders: one state bundle per section. A later
 * section — reading preferences, say — is a new field here plus a new section
 * composable, not a redesign (issues #20, #28, #88).
 *
 * [appLanguage] is the device's own language (ADR-0034): the language of the
 * app's texts and of every translation it makes. The **Reader level**, the
 * **Difficulty table** and the **Dictionary pack** left for the **Languages**
 * surface (issues #147, #148, #149); this screen holds only the app's own
 * configuration.
 */
data class SettingsUiState(
    val appLanguage: String = AppLanguages.ENGLISH,
    val vault: VaultUiState = VaultUiState(),
    val provider: ProviderUiState = ProviderUiState(),
    val sync: SyncUiState = SyncUiState(),
)
