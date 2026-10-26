package app.lekto.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.contentColorOn
import app.lekto.theme.hoverWash
import app.lekto.ui.handCursor

/** The padding every mastery chip shares, so the lookup selector and the list filter stay identical. */
private val ChipPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/** The visible frame of a compact chip, inside its 48 dp touch target. */
private val CompactChipWidth = 32.dp
private val CompactChipHeight = 32.dp

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

/**
 * The numbered chip (issue #210's mockup): every level keeps its colour as a
 * filled frame, and the selected one adds an ink ring, so the ramp reads at once
 * and "which one is the word's level" is a second, separate signal. The hover is
 * the theme's ink at 5 % over the whole 48 dp target, so the pointer's row reads
 * as one control.
 */
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
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .sizeIn(minWidth = LektoSpacing.TouchTarget, minHeight = LektoSpacing.TouchTarget)
            .background(if (hovered) hoverWash(theme.onSurface) else Color.Transparent, LektoRadii.Control)
            .handCursor()
            .hoverable(interaction)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(CompactChipWidth)
                .height(CompactChipHeight)
                .background(colour, LektoRadii.Control)
                .then(
                    if (selected) Modifier.border(SelectedRing, theme.onBackground, LektoRadii.Control) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = contentColorOn(colour),
            )
        }
    }
}

/** The ring that marks the word's current level, in the theme's ink. */
private val SelectedRing = 2.dp
