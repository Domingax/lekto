package app.lekto.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState

/**
 * The **Languages** destination (issue #146): it starts the controller once, so
 * a first launch fetches the manifest while a later one reads the cached copy,
 * and otherwise renders the catalogue the controller reports. It is reached from
 * the library and from Settings until the navigation rework lands.
 */
@Composable
internal fun LanguagesDestination(language: LanguageController?, onBack: () -> Unit) {
    val state = language?.state?.collectAsState()?.value ?: LanguagesUiState()
    LaunchedEffect(language) { language?.start() }
    LanguagesScreen(
        state = state,
        actions = LanguagesActions(onReload = { language?.reload() }, onBack = onBack),
    )
}
