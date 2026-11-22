package app.lekto.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.core.MasteryLevel
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing

/**
 * The five-point **Mastery level** selector, as one control (issue #178): the
 * name the call site gives it, then the five chips, the current one filled and
 * the others ringed. The name stands beside the chips so their numbers never
 * need a legend, and one tap on a chip raises that level.
 *
 * It is the reader's lookup footer (issue #22, docs/design.md §7.3) and the
 * Vocabulary list's level sheet (#178), in one place so the two cannot drift into
 * two selectors. The chips are compact, a number, so all five fit one row on a
 * phone; each still carries a full 48 dp target.
 *
 * [level] is the level the word carries, or `null` when it carries none — a word
 * that is not saved yet (issue #279). With no level no chip is marked or ringed,
 * and `selected` is announced for no chip; the caller states the suggestion in
 * [name] instead, so a suggestion never reads as a fact.
 *
 * [stacked] puts the name above the chips instead of beside them: the reader's
 * desktop context column has no room for both on one line (issue #199). The same
 * two blocks, stacked, rather than a name broken mid-word.
 *
 * [chipTag] and [levelTag] are the call site's test tags: the two surfaces prove
 * the same control from different screens. [name] replaces the default level
 * name; the reader's footer states its own line there.
 */
@Suppress("LongParameterList") // The level, its action, the two tags, the call site's name, and the shape.
@Composable
fun MasterySelector(
    level: MasteryLevel?,
    onSelect: (MasteryLevel) -> Unit,
    chipTag: (MasteryLevel) -> String,
    levelTag: String,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    name: @Composable () -> Unit = { level?.let { current -> LevelName(current, levelTag) } },
) {
    if (stacked) {
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Name(levelTag, name, Modifier.padding(bottom = LektoSpacing.InsideLabelPair))
            Chips(level, onSelect, chipTag)
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Name(levelTag, name, Modifier.weight(1f))
            Chips(level, onSelect, chipTag)
        }
    }
}

/** The call site's name, in whichever arrangement it asked for, carrying its test tag. */
@Composable
private fun Name(levelTag: String, name: @Composable () -> Unit, modifier: Modifier) {
    Box(modifier.testTag(levelTag), contentAlignment = Alignment.CenterStart) { name() }
}

/** The five chips, emitted into whichever arrangement the call site asked for. */
@Composable
private fun Chips(level: MasteryLevel?, onSelect: (MasteryLevel) -> Unit, chipTag: (MasteryLevel) -> String) {
    // Each chip's 48 dp target already leaves 8 dp on each side of its 32 dp
    // frame; a gap between targets would wrap the row on a 360 dp phone.
    Row {
        MasteryLevel.entries.forEach { entry ->
            MasteryChip(
                level = entry,
                label = entry.shortLabel(),
                selected = entry == level,
                onClick = { onSelect(entry) },
                modifier = Modifier.testTag(chipTag(entry)),
                compact = true,
            )
        }
    }
}

/** The current level's name, in ordinary ink, so a chip's number is never the only signal. */
@Composable
private fun LevelName(level: MasteryLevel, levelTag: String) {
    val texts = LocalAppTexts.current
    Column(
        modifier = Modifier.heightIn(min = LektoSpacing.TouchTarget),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = texts[TextKey.MASTERY],
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(texts.masteryName(level), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A selector chip's compact label: its number, and a check for the known level. */
private fun MasteryLevel.shortLabel(): String = when (this) {
    MasteryLevel.UNKNOWN -> "0"
    MasteryLevel.RECOGNIZED -> "1"
    MasteryLevel.FAMILIAR -> "2"
    MasteryLevel.LEARNED -> "3"
    MasteryLevel.KNOWN -> "4"
}
