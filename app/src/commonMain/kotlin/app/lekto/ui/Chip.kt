package app.lekto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing

/**
 * The one choice chip of the identity (`docs/design.md` §7.2, §7.4): the **Aa**
 * panel's value chips and the Vocabulary list's level filters are the same
 * control, so the two screens cannot drift into two looks.
 *
 * The visible shape stays compact while the touch target keeps the 48 dp floor
 * (§9), and it announces the choice rather than only drawing it. [leading] is a
 * mark before the word — the Vocabulary list's mastery dot; the panel's chips
 * have none, so the frame around the words is the same in both places.
 *
 * It has no indication of its own: a ripple is an animation, and the identity's
 * feedback is the ink moving (§8).
 */
@Suppress("LongParameterList") // The chip's word, its state, its tap and its optional mark are independent.
@Composable
internal fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .heightIn(min = LektoSpacing.TouchTarget)
            .handCursor()
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ChipFrame(selected = selected) {
            ChipLabel(label, selected, leading)
        }
    }
}

/**
 * The same chip as a **multi-select** control (issue #200): the Library's
 * **Reading state** filters are a set, so the chip toggles instead of choosing
 * one of a group. Its shape, touch target and ink are [ChoiceChip]'s; only the
 * announced role differs — a checkbox, not a radio button — so a screen reader
 * hears "checked" rather than "selected".
 */
@Suppress("LongParameterList") // The chip's word, its state, its tap and its optional mark are independent.
@Composable
internal fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .heightIn(min = LektoSpacing.TouchTarget)
            .handCursor()
            .toggleable(
                value = selected,
                interactionSource = null,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        ChipFrame(selected = selected) {
            ChipLabel(label, selected, leading)
        }
    }
}

/** A chip's visible words: the optional mark, then the label in the ink its state calls for. */
@Composable
private fun ChipLabel(label: String, selected: Boolean, leading: (@Composable () -> Unit)?) {
    val theme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) theme.onPrimary else theme.onSurface,
        )
    }
}

/**
 * A chip's visible shape inside its 48 dp touch target: ink when selected, and
 * otherwise the theme's paper lifted against the surface behind it — a chip has
 * to read as a field on the panel, not as a hole in it (the sepia paper makes
 * this plainest) (§7.2).
 */
@Composable
internal fun ChipFrame(selected: Boolean, content: @Composable () -> Unit) {
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .background(if (selected) theme.primary else theme.background, LektoRadii.Control)
            .border(HAIRLINE_THICKNESS, if (selected) theme.primary else theme.outline, LektoRadii.Control)
            .padding(horizontal = LektoSpacing.BetweenRows, vertical = LektoSpacing.InsideLabelPair),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
