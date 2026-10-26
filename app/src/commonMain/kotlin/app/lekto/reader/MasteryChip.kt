package app.lekto.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.contentColorOn
import app.lekto.ui.handCursor

/** The padding every mastery chip shares, so the lookup selector and the list filter stay identical. */
private val ChipPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/** The visible frame of a compact chip, inside its 48 dp touch target. */
private val CompactChipWidth = 32.dp
private val CompactChipHeight = 28.dp

/**
 * One mastery chip: the level's colour behind [label], filled when [selected]
 * and ringed otherwise, raising [onClick]. Shared by the lookup panel's level
 * selector (issue #22) and the vocabulary list's mastery filter (issue #66), so
 * the two controls cannot drift apart.
 *
 * [level] is `null` for the filter's all-levels chip, which takes the outline
 * colour instead of a palette one. The [label] carries the meaning and the
 * colour is supplementary, taken from the active paper's measured ramp
 * (`docs/design.md` §2.2); the selected chip also carries `selected` semantics,
 * so the active choice is announced, not shown by fill alone.
 *
 * [compact] draws the lookup panel's numbered chip (issue #210): the same colour
 * and selection, in a 32 × 28 frame inside a full 48 dp touch target, so all five
 * fit one row beside the level's name without shrinking the target.
 */
@Suppress("LongParameterList") // The chip's inputs are independent; a bundle would only hide that.
@Composable
fun MasteryChip(
    level: MasteryLevel?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val theme = MaterialTheme.colorScheme
    val ramp = MasteryPalette.of(theme)
    val colour = level?.readerColorOr(theme.outline, ramp) ?: theme.outline
    val marked = modifier.semantics { this.selected = selected }
    if (compact) {
        CompactChip(label, colour, selected, onClick, marked)
    } else if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = colour, contentColor = contentColorOn(colour)),
            contentPadding = ChipPadding,
            modifier = marked.handCursor(),
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(2.dp, colour),
            contentPadding = ChipPadding,
            modifier = marked.handCursor(),
        ) { Text(label) }
    }
}

/** The numbered chip: a small filled or ringed frame, centred in its 48 dp target. */
@Suppress("LongParameterList") // The chip's frame, its colour, its state and its tap are independent.
@Composable
private fun CompactChip(
    label: String,
    colour: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .sizeIn(minWidth = LektoSpacing.TouchTarget, minHeight = LektoSpacing.TouchTarget)
            .handCursor()
            .clickable(role = Role.Button, onClick = onClick)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(CompactChipWidth)
                .height(CompactChipHeight)
                .background(if (selected) colour else theme.background, LektoRadii.Control)
                .border(2.dp, colour, LektoRadii.Control),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (selected) contentColorOn(colour) else theme.onSurface,
            )
        }
    }
}
