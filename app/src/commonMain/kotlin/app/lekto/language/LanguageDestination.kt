package app.lekto.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import app.lekto.reader.MasteryController

/**
 * The **Languages** destination (issues #146, #147): it starts the controller
 * once, so a first launch fetches the manifest while a later one reads the cached
 * copy, and otherwise renders the catalogue the controller reports together with
 * the reader's declared **Reader level** per language — read and written through
 * the mastery composition, the single source of the level (ADR-0030). It is
 * reached from the library and from Settings until the navigation rework lands.
 */
@Composable
internal fun LanguagesDestination(language: LanguageController?, mastery: MasteryController?, onBack: () -> Unit) {
    val state = language?.state?.collectAsState()?.value ?: LanguagesUiState()
    LaunchedEffect(language) { language?.start() }
    LanguagesScreen(
        state = state,
        levels = mastery?.levels().orEmpty(),
        actions = LanguagesActions(
            onReload = { language?.reload() },
            onSetLevel = { code, level -> mastery?.setLevel(code, level) },
            onClearLevel = { code -> mastery?.clearLevel(code) },
            onBack = onBack,
        ),
    )
}
