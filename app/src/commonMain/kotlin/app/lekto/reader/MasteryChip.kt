package app.lekto.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel

/** The padding every mastery chip shares, so the lookup selector and the list filter stay identical. */
private val ChipPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/**
 * One mastery chip: the level's colour behind [label], filled when [selected]
 * and ringed otherwise, raising [onClick]. Shared by the lookup panel's level
 * selector (issue #22) and the vocabulary list's mastery filter (issue #66), so
 * the two controls cannot drift apart.
 *
 * [level] is `null` for the filter's all-levels chip, which takes the outline
 * colour instead of a palette one. The [label] carries the meaning and the
 * colour is supplementary — the palette's light levels have too little contrast
 * to be read as text — and the selected chip also carries `selected` semantics,
 * so the active choice is announced, not shown by fill alone.
 */
@Suppress("LongParameterList") // The chip's inputs are independent; a bundle would only hide that.
@Composable
fun MasteryChip(
    level: MasteryLevel?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colour = level?.readerColorOr(MaterialTheme.colorScheme.outline) ?: MaterialTheme.colorScheme.outline
    val marked = modifier.semantics { this.selected = selected }
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = colour, contentColor = contentColorOn(colour)),
            contentPadding = ChipPadding,
            modifier = marked,
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(2.dp, colour),
            contentPadding = ChipPadding,
            modifier = marked,
        ) { Text(label) }
    }
}
