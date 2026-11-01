package app.lekto.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lekto.core.theme.AppTheme
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.theme.appThemeScheme
import app.lekto.ui.ScreenHeader
import app.lekto.ui.handCursor
import app.lekto.ui.rowHover

/** The test tag on the way back to Settings, so a UI test can click exactly it. */
internal const val APPEARANCE_BACK_TAG: String = "appearance-back"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar. */
internal const val APPEARANCE_TITLE_TAG: String = "appearance-title"

/** The test tag on one **App theme** entry, so a UI test can choose exactly it. */
internal fun appearanceOptionTag(theme: AppTheme): String = "appearance-${theme.name.lowercase()}"

/**
 * The actions the **Appearance** screen raises: choose one of the four entries
 * (ADR-0036), or go back to Settings.
 */
data class AppearanceActions(val onSelect: (AppTheme) -> Unit = {}, val onBack: () -> Unit = {})

/**
 * The **Appearance** screen (ADR-0036; docs/design.md §7.6): the four entries —
 * **System**, **Light**, **Dark**, **Sepia** — each *shown on its own paper*, so a
 * paper is chosen by looking at it. Each entry is a 48 × 34 preview with two lines
 * of ink; **System** is split down the middle, the platform's light paper beside
 * its dark one, because that is what it means.
 *
 * The current choice carries both the selectable state a screen reader reports and
 * a visible check, so it is marked without relying on colour. The check is
 * decorative — the selectable state is what announces it — so it carries no
 * description of its own. The screen owns no state and raises [actions], so the
 * whole surface is proved through semantics.
 */
@Composable
fun AppearanceScreen(
    current: AppTheme,
    actions: AppearanceActions = AppearanceActions(),
    modifier: Modifier = Modifier,
) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows),
    ) {
        ScreenHeader(
            title = texts[TextKey.SECTION_APPEARANCE],
            backTag = APPEARANCE_BACK_TAG,
            titleTag = APPEARANCE_TITLE_TAG,
            onBack = actions.onBack,
        )
        Column(
            Modifier.selectableGroup().padding(horizontal = LektoSpacing.ScreenEdge),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        ) {
            AppTheme.entries.forEach { theme ->
                ThemeEntry(
                    theme = theme,
                    selected = theme == current,
                    onClick = { actions.onSelect(theme) },
                )
            }
        }
    }
}

/** One entry: its preview, its name and one-line description, and the current mark. */
@Composable
private fun ThemeEntry(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val texts = LocalAppTexts.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
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
            .testTag(appearanceOptionTag(theme)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.InsidePanel),
    ) {
        ThemePreview(theme)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
            Text(
                texts.themeName(theme),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
            Text(
                texts.themeDescription(theme),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) ChoiceCheck()
    }
}

/**
 * The 48 × 34 preview of [theme]: its own paper and two lines of its ink.
 * **System** is split down the middle — the light paper beside the dark one —
 * because it is two papers chosen by the device, never Sepia (ADR-0036).
 */
@Composable
private fun ThemePreview(theme: AppTheme) {
    val previewModifier = Modifier.size(width = PREVIEW_WIDTH, height = PREVIEW_HEIGHT).previewShape()
    if (theme == AppTheme.SYSTEM) {
        Row(previewModifier) {
            PaperSwatch(AppTheme.LIGHT, lines = 2, modifier = Modifier.weight(1f).fillMaxHeight())
            PaperSwatch(AppTheme.DARK, lines = 2, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        PaperSwatch(theme, lines = 2, modifier = previewModifier)
    }
}

/** One paper with [lines] strokes of its own ink, sized to the slot it is given. */
@Composable
private fun PaperSwatch(theme: AppTheme, lines: Int, modifier: Modifier = Modifier) {
    val scheme = appThemeScheme(theme, systemInDark = false)
    Column(
        modifier.background(scheme.background).padding(horizontal = PREVIEW_INK_PADDING, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(lines) { line ->
            Box(
                Modifier.fillMaxWidth(if (line == 0) FIRST_LINE_WIDTH else SECOND_LINE_WIDTH)
                    .height(2.dp)
                    .background(scheme.onBackground),
            )
        }
    }
}

/** The one hairline and corner every preview shares, in the screen's own theme. */
@Composable
private fun Modifier.previewShape(): Modifier {
    val shape = MaterialTheme.shapes.extraSmall
    return clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
}

/** The preview's size, the one `docs/design.md` §7.6 names. */
private val PREVIEW_WIDTH = 48.dp
private val PREVIEW_HEIGHT = 34.dp

/** The inset around the preview's two ink lines; narrow, because System halves the width. */
private val PREVIEW_INK_PADDING = 6.dp

/** How much of the preview's width each of the two ink lines takes. */
private const val FIRST_LINE_WIDTH = 0.9f
private const val SECOND_LINE_WIDTH = 0.55f
