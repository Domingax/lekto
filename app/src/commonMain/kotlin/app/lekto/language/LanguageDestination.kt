package app.lekto.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import app.lekto.difficulty.DifficultyController
import app.lekto.reader.MasteryController

/**
 * The **Languages** destination (issues #146, #147, #148): it starts the
 * controller once, so a first launch fetches the manifest while a later one reads
 * the cached copy, renders the catalogue it reports together with the reader's
 * declared **Reader level** per language — read and written through the mastery
 * composition, the single source of the level (ADR-0030) — and carries each
 * language's **Difficulty table** state and download. It is reached from the
 * library and from Settings until the navigation rework lands.
 *
 * When the offered languages change it re-reads each one's table state, so a
 * table already installed on this device shows as installed, and a download
 * recolours the open book through the mastery composition with no relaunch
 * (ADR-0030).
 */
@Composable
internal fun LanguagesDestination(
    language: LanguageController?,
    difficulty: DifficultyController?,
    mastery: MasteryController?,
    onBack: () -> Unit,
) {
    val state = language?.state?.collectAsState()?.value ?: LanguagesUiState()
    val difficultyStates = difficulty?.states?.collectAsState()?.value.orEmpty()
    LaunchedEffect(language) { language?.start() }
    LaunchedEffect(difficulty, state.languages) {
        difficulty?.refresh(state.languages.map { offered -> offered.language })
    }
    LanguagesScreen(
        state = state,
        levels = mastery?.levels().orEmpty(),
        difficulty = difficultyStates,
        actions = LanguagesActions(
            onReload = { language?.reload() },
            onSetLevel = { code, level -> mastery?.setLevel(code, level) },
            onClearLevel = { code -> mastery?.clearLevel(code) },
            onDownloadDifficulty = { code, artifact -> difficulty?.install(code, artifact) { mastery?.invalidate() } },
            onDismissDifficultyError = { code -> difficulty?.dismissError(code) },
            onBack = onBack,
        ),
    )
}
