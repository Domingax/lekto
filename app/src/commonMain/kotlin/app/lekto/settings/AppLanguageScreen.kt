package app.lekto.settings

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import app.lekto.core.language.AppLanguages
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor
import app.lekto.ui.rowHover

/** The test tag on the way back to Settings, so a UI test can click exactly it. */
internal const val APP_LANGUAGE_BACK_TAG: String = "app-language-back"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar. */
internal const val APP_LANGUAGE_TITLE_TAG: String = "app-language-title"

/** The test tag on one **App language** entry, so a UI test can choose exactly it. */
internal fun appLanguageOptionTag(language: String): String = "app-language-$language"

/**
 * The actions the **App language** screen raises: choose one of the languages the
 * app ships, or go back to Settings.
 */
data class AppLanguageActions(val onSelect: (String) -> Unit = {}, val onBack: () -> Unit = {})

/**
 * The **App language** screen (ADR-0034; issue #214): the languages the app ships
 * — English and French — offered by name. The chosen one carries both the
 * selectable state a screen reader reports and a visible check, so it is marked
 * without relying on colour. The check is decorative, because the selectable state
 * is what announces the choice.
 *
 * The screen owns no state and raises [actions], so the whole surface is proved
 * through semantics. It exists so the Settings index can be an index: the choice
 * is a screen away rather than a control on the list (docs/design.md §7.5).
 */
@Composable
fun AppLanguageScreen(
    current: String,
    actions: AppLanguageActions = AppLanguageActions(),
    modifier: Modifier = Modifier,
) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.ScreenEdge),
    ) {
        SettingsFormHeader(
            title = texts[TextKey.SECTION_APP_LANGUAGE],
            backTag = APP_LANGUAGE_BACK_TAG,
            titleTag = APP_LANGUAGE_TITLE_TAG,
            onBack = actions.onBack,
        )
        Text(
            texts[TextKey.APP_LANGUAGE_DESCRIPTION],
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Column(Modifier.selectableGroup()) {
            AppLanguages.SUPPORTED.forEach { language ->
                LanguageEntry(
                    language = language,
                    selected = language == current,
                    onClick = { actions.onSelect(language) },
                )
            }
        }
    }
}

/** One language: its name, and the check that marks the chosen one. */
@Composable
private fun LanguageEntry(language: String, selected: Boolean, onClick: () -> Unit) {
    val texts = LocalAppTexts.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .rowHover(hovered, MaterialTheme.colorScheme.onBackground)
            .handCursor()
            .hoverable(interaction)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = LektoSpacing.BetweenRows)
            .testTag(appLanguageOptionTag(language)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Text(
            text = texts.languageName(language),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) ChoiceCheck()
    }
}
