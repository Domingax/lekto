package app.lekto.vocabulary

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasterySelector
import app.lekto.theme.LektoSpacing
import app.lekto.theme.WIDE_WINDOW_BREAKPOINT
import app.lekto.ui.DismissScrim
import app.lekto.ui.LookupCloseButton
import app.lekto.ui.LookupHeader
import app.lekto.ui.LookupSheet
import app.lekto.ui.SheetDivider

/** The test tag on the level sheet, so a test can tell it from the list behind it. */
internal const val VOCABULARY_LEVEL_SHEET_TAG: String = "vocabulary-level-sheet"

/** The test tag on the scrim behind the sheet, so a test can dismiss it the way a reader does. */
internal const val VOCABULARY_LEVEL_SCRIM_TAG: String = "vocabulary-level-scrim"

/** The test tag on the sheet's close action. */
internal const val VOCABULARY_LEVEL_CLOSE_TAG: String = "vocabulary-level-close"

/** The test tag on the current level's name, so a test can read it apart from the chips. */
internal const val VOCABULARY_LEVEL_NAME_TAG: String = "vocabulary-level-name"

/** The test tag on one level chip, so a test can tap exactly one. */
internal fun vocabularyLevelTag(level: MasteryLevel): String = "vocabulary-level-${level.name}"

/**
 * How much of the screen the level sheet may take: taller than the reader's
 * sheets, because no page has to stay visible behind it. The sheet wraps its
 * content, so this is a ceiling, not a height.
 */
internal const val VOCABULARY_LEVEL_SHEET_MAX_HEIGHT_FRACTION: Float = 0.8f

/**
 * The width the sheet keeps once the window has room to spare: a sheet stretching
 * the full width of a desktop window reads as a bar, not as something raised for
 * one word.
 */
internal val WideSheetWidth = 560.dp

/**
 * The level sheet (issue #178): the saved word, the translation and the sentence
 * the list shows, and the reader's own five-level selector. It is how a reader
 * promotes a word outside the book — the action a vocabulary list exists for,
 * since the **Mastery level** is what colours the text (ADR-0030).
 *
 * It carries no dictionary senses, no pronunciation and no references: a saved
 * entry already holds everything here, so opening it never needs a **Dictionary
 * pack** and never reports one missing. That is why it is not the reader's word
 * panel, whose body *is* the dictionary's answer.
 *
 * Raising a level does not close the sheet: its marker moves, the row behind
 * changes, and a level the active filter excludes simply leaves the list — the
 * departure is the confirmation, and there is no toast. The write itself is the
 * composition root's; this screen only raises the level.
 */
@Composable
fun VocabularyLevelSheet(
    entry: VocabularyEntry,
    onSave: (MasteryLevel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        DismissScrim(onDismiss, Modifier.testTag(VOCABULARY_LEVEL_SCRIM_TAG))
        val wide = maxWidth >= WIDE_WINDOW_BREAKPOINT
        LookupSheet(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (wide) Modifier.widthIn(max = WideSheetWidth) else Modifier),
            sheetModifier = Modifier.testTag(VOCABULARY_LEVEL_SHEET_TAG),
            maxHeightFraction = VOCABULARY_LEVEL_SHEET_MAX_HEIGHT_FRACTION,
        ) {
            Header(entry.surface, onDismiss)
            Meaning(entry)
            SheetDivider()
            MasterySelector(
                level = entry.mastery,
                onSelect = onSave,
                chipTag = ::vocabularyLevelTag,
                levelTag = VOCABULARY_LEVEL_NAME_TAG,
                modifier = Modifier.padding(
                    horizontal = LektoSpacing.InsidePanel,
                    vertical = LektoSpacing.BetweenRows,
                ),
            )
        }
    }
}

/** The word in the serif, as the book printed it, and the close action every panel carries. */
@Composable
private fun Header(surface: String, onDismiss: () -> Unit) {
    LookupHeader {
        Text(
            text = surface,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(bottom = LektoSpacing.BetweenRelatedLines),
        )
        LookupCloseButton(onDismiss = onDismiss, tag = VOCABULARY_LEVEL_CLOSE_TAG)
    }
}

/**
 * What the row says about the word: its translation, then the sentence it came
 * from in the muted ink. Both are the list's own values, so the sheet never
 * disagrees with the row it was opened from — and with neither, the sheet is only
 * the word and its levels.
 *
 * It scrolls rather than clipping: a saved sentence can run long, and the sheet's
 * cap is a ceiling, not a promise of room (the reader's sheets scroll their body
 * the same way).
 */
@Composable
private fun ColumnScope.Meaning(entry: VocabularyEntry) {
    val translation = entry.translation
    val sentence = entry.contextSentence
    if (translation == null && sentence == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.InsidePanel)
            .padding(bottom = LektoSpacing.BetweenRows),
    ) {
        translation?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
        sentence?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = LektoSpacing.InsideLabelPair),
            )
        }
    }
}
