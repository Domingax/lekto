package app.lekto.vocabulary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasteryPalette
import app.lekto.reader.readerColorOr
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.DeleteAction
import app.lekto.ui.DeletingRow
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.handCursor

/** The test tag on an entry's delete button, so a UI test can click exactly one. */
internal fun vocabularyDeleteTag(key: WordKey): String = "vocabulary-delete-${key.language.orEmpty()}-${key.key}"

/** The test tag on an entry's frame, so a UI test can swipe exactly one row. */
internal fun vocabularyRowTag(key: WordKey): String = "vocabulary-row-${key.language.orEmpty()}-${key.key}"

/** The test tag on an entry's mastery label, so a UI test can read it apart from the filter chips. */
internal fun vocabularyMasteryTag(key: WordKey): String = "vocabulary-mastery-${key.language.orEmpty()}-${key.key}"

/** The dot that stands for a **Mastery level** on a row and in a filter (§2.2, §7.4). */
internal val MASTERY_DOT_SIZE = 10.dp

/** The gap between that dot and the level's name, on a row and in a filter chip (§4: 6–8 between related lines). */
internal val MASTERY_DOT_GAP = 6.dp

/**
 * One entry (§7.4; issue #212): the word in the book's serif — the word as the
 * book printed it — with its colour-dotted level beside it, then the translation,
 * then the sentence it came from in the muted ink. The row is one tap target that
 * opens the word's level sheet (#178), and the delete is the shared row gesture
 * ([DeletingRow]): a swipe on Android, a trash on hover with a pointer, and the
 * confirmation either way.
 *
 * The row also carries the delete as an accessibility action, because a screen
 * reader cannot swipe a row the way a thumb does, and the tap names itself so it
 * is not announced as an anonymous click.
 */
@Composable
internal fun VocabularyRow(
    entry: VocabularyEntry,
    onDelete: (VocabularyEntry) -> Unit,
    onOpen: (VocabularyEntry) -> Unit,
) {
    val texts = LocalAppTexts.current
    Column {
        DeletingRow(
            delete = DeleteAction(tag = vocabularyDeleteTag(entry.key), onDelete = { onDelete(entry) }),
            verticalAlignment = Alignment.Top,
        ) { content ->
            Column(
                modifier = content
                    .testTag(vocabularyRowTag(entry.key))
                    .handCursor()
                    .clickable(role = Role.Button, onClickLabel = texts[TextKey.CHANGE_LEVEL]) { onOpen(entry) }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(texts[TextKey.DELETE]) {
                                onDelete(entry)
                                true
                            },
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
            ) {
                WordLine(entry)
                entry.translation?.let { translation ->
                    Text(translation, style = MaterialTheme.typography.bodyMedium)
                }
                entry.contextSentence?.let { sentence ->
                    Text(
                        text = sentence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(thickness = HAIRLINE_THICKNESS, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The row's first line: the word in the serif, and its level right after it. */
@Composable
private fun WordLine(entry: VocabularyEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.surface,
            style = MaterialTheme.typography.titleMedium,
            // The level keeps its room on the line; a long word wraps rather than
            // pushing it out of the row.
            modifier = Modifier.weight(1f, fill = false),
        )
        Mastery(entry.mastery, entry.key)
    }
}

/**
 * The entry's mastery: its level named in ordinary ink, with the reader's colour
 * as a dot beside it. The name is what carries the meaning, so the level is
 * legible even where the palette's light colours have too little contrast to be
 * read as text; the dot ties the entry to the colour the reader paints it.
 */
@Composable
private fun Mastery(level: MasteryLevel, key: WordKey) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MASTERY_DOT_GAP),
    ) {
        LevelDot(level.readerColorOr(MaterialTheme.colorScheme.outline, MasteryPalette.of(MaterialTheme.colorScheme)))
        Text(
            LocalAppTexts.current.masteryName(level),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag(vocabularyMasteryTag(key)),
        )
    }
}

/** The level's colour, 10 dp across: the one non-text mark that ties a row to the reader's ramp. */
@Composable
internal fun LevelDot(colour: Color) {
    Box(Modifier.size(MASTERY_DOT_SIZE).background(colour, CircleShape))
}

/**
 * The Bundle-safe key for a word's row (issue #69). Compose saves a lazy item's
 * state through a saveable-state holder, and Android only accepts a key it can
 * put in a `Bundle`; a [WordKey] is a data class and is not one, so keying a
 * `LazyColumn` on it crashed the vocabulary list on Android. This projects the
 * key onto the `String` Android needs, joined the same way the vault's own
 * record id joins its fields, so distinct words get distinct rows.
 */
internal fun WordKey.rowKey(): String = "${language.orEmpty()}\u0000$key"
