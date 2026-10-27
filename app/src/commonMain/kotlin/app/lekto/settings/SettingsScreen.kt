package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.core.theme.AppTheme
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor

/** The test tag on the way to the **Languages** surface, so a UI test can click exactly it. */
internal const val LANGUAGES_LINK_TAG: String = "settings-languages"

/** The test tag on the way to the **Appearance** screen, so a UI test can click exactly it. */
internal const val APPEARANCE_LINK_TAG: String = "settings-appearance"

/** The test tag on the way to the **LLM provider** screen, so a UI test can click exactly it. */
internal const val PROVIDER_LINK_TAG: String = "settings-provider"

/** The test tag on the way to the **Vault and sync** screen, so a UI test can click exactly it. */
internal const val VAULT_LINK_TAG: String = "settings-vault"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar (issue #185). */
internal const val SETTINGS_TITLE_TAG: String = "settings-title"

/**
 * The settings screen: the app's own appearance (ADR-0036), the app's own language
 * (ADR-0034), the way to the **Languages** surface (issue #146), and the doorways
 * to the two forms that now have screens of their own — **LLM provider** (issues
 * #88, #213) and **Vault and sync** (issues #20, #28, #213).
 *
 * The forms themselves are not here: an API key and a WebDAV server are screens
 * of their own (docs/design.md §7.5). The full index of rows, each with its
 * current value under it, is the next ticket; this screen only holds the way in,
 * so the index has somewhere to point.
 *
 * The data the reader manages per language — the **Reader level**, the
 * **Difficulty table** and the **Dictionary pack** — lives on the **Languages**
 * surface, not here (issues #147, #148, #149), and the theme's four papers live on
 * the **Appearance** screen, so this screen names the current one and nothing more.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions = SettingsActions(),
    modifier: Modifier = Modifier,
) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenBlocks),
    ) {
        Header()
        Section(texts[TextKey.SECTION_APPEARANCE]) { AppearanceSection(state.appTheme, actions) }
        Section(texts[TextKey.SECTION_APP_LANGUAGE]) {
            AppLanguageSection(state.appLanguage, actions.onSelectAppLanguage)
        }
        Section(texts[TextKey.SECTION_LANGUAGES]) { LanguagesSection(actions) }
        Section(texts[TextKey.SECTION_LLM_PROVIDER]) {
            Doorway(tag = PROVIDER_LINK_TAG, onClick = actions.onOpenProvider)
        }
        Section(texts[TextKey.VAULT_AND_SYNC]) {
            Doorway(tag = VAULT_LINK_TAG, onClick = actions.onOpenVault)
        }
    }
}

/** One settings section: a title and its content, so every section is spaced alike. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

/**
 * The **Appearance** doorway (ADR-0036; docs/design.md §7.6): Settings names the
 * app's current paper and offers the screen that changes it. The four entries are
 * each shown on their own paper there, so a theme is chosen by looking at it.
 */
@Composable
private fun AppearanceSection(theme: AppTheme, actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.APPEARANCE_DESCRIPTION],
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(texts.themeName(theme), style = MaterialTheme.typography.bodyMedium)
    TextButton(
        onClick = actions.onOpenAppearance,
        modifier = Modifier.handCursor().testTag(APPEARANCE_LINK_TAG),
    ) {
        Text(texts[TextKey.SECTION_APPEARANCE])
    }
}

/**
 * The **Languages** doorway (issue #146): a first-class destination, so it is
 * reachable from Settings as well as from the library's own header until the
 * navigation rework lands. It carries the reader's **Reader level**, each
 * language's **Difficulty table** and each published **Dictionary pack** state and
 * download (issues #147, #148, #149), so Settings holds only the app's own
 * configuration.
 */
@Composable
private fun LanguagesSection(actions: SettingsActions) {
    val texts = LocalAppTexts.current
    Text(
        texts[TextKey.LANGUAGES_SECTION_DESCRIPTION],
        style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(
        onClick = actions.onOpenLanguages,
        modifier = Modifier.handCursor().testTag(LANGUAGES_LINK_TAG),
    ) {
        Text(texts[TextKey.LANGUAGES])
    }
}

/**
 * One doorway: the word that opens a screen. The screen it opens owns its
 * description, fields and actions; Settings keeps only the way in. The full
 * index of rows with their current values is the next ticket (issue #213).
 */
@Composable
private fun Doorway(tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.handCursor().testTag(tag)) {
        Text(LocalAppTexts.current[TextKey.OPEN])
    }
}

/** The screen's title alone: the shell's bar and the back stack replace its old way back (issue #185). */
@Composable
private fun Header() {
    Text(
        text = LocalAppTexts.current[TextKey.SETTINGS],
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag(SETTINGS_TITLE_TAG),
    )
}
