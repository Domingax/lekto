package app.lekto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.lekto.theme.LektoSpacing

/**
 * A text action on a row: the identity's quiet alternative to a filled button
 * (docs/design.md §7.5, §8). The **Languages** surface uses it for **Reload
 * list**, each derived asset's download and the **Reader level** picker, so a row
 * carries its own action without a column of full-width buttons.
 *
 * It keeps the 48 dp touch target §9 asks of anything tappable, and the pointer
 * gets the row's own wash while it rests on the action — the same hint a list row
 * gives, never a Material ripple. [onClickLabel] names the action to a screen
 * reader when the visible words are short: `Download` says which derived asset
 * only in the line beside it.
 */
@Composable
@Suppress("LongParameterList") // A row action's word, its state, its accessible name and its tap are independent.
internal fun TextAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .heightIn(min = LektoSpacing.TouchTarget)
            .rowHover(hovered, MaterialTheme.colorScheme.onBackground)
            .handCursor(enabled)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        content = content,
    )
}
