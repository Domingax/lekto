package app.lekto.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor
import app.lekto.ui.icons.Check
import app.lekto.ui.icons.LektoIcons

/** The test tag on the end-of-book state, so a UI test can tell it is open (issue #200). */
internal const val READER_END_PANEL_TAG = "reader-end-panel"

/** The test tag on the end panel's `Mark as finished` action. */
internal const val READER_MARK_FINISHED_TAG = "reader-mark-finished"

/** The test tag on the end panel's `Mark as unfinished` action. */
internal const val READER_MARK_UNFINISHED_TAG = "reader-mark-unfinished"

/** The test tag on the end panel's `Back to Library` action. */
internal const val READER_BACK_TO_LIBRARY_TAG = "reader-back-to-library"

/**
 * The end-of-book state (docs/design.md §7.2; issue #200; ADR-0038): a
 * full-screen reader state, not a page, raised by an explicit next on the last
 * page. `THE END`, the book's title in the reading serif, then the declaration
 * as the filled action and `Back to Library` as the text action. Once declared,
 * the check and `Finished` appear, `Back to Library` becomes the filled action
 * and `Mark as unfinished` takes the text action. Marking never moves the
 * position, and this panel never declares anything by itself.
 *
 * The marks are reported through [actions]: [ReaderActions.onMarkFinished] and
 * [ReaderActions.onMarkUnfinished] write or undo the vault record, while
 * [onShowFinished] and [onShowUnfinished] only flip the panel's face, so the
 * screen stays a pure function of its state.
 */
@Suppress("LongParameterList") // The panel's text, its state, its callbacks and the reader's actions are independent.
@Composable
internal fun EndOfBookPanel(
    title: String,
    finished: Boolean,
    actions: ReaderActions,
    onShowFinished: () -> Unit,
    onShowUnfinished: () -> Unit,
) {
    val texts = LocalAppTexts.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(READER_END_PANEL_TAG)
            .padding(LektoSpacing.ScreenEdge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = texts[TextKey.END_OF_BOOK],
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = END_OF_BOOK_LETTER_SPACING,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = LektoSpacing.InsidePanel),
        )
        if (finished) {
            FinishedMark()
            FinishedActions(actions, onShowUnfinished)
        } else {
            UnfinishedActions(actions, onShowFinished)
        }
    }
}

/** The marked face's actions: leave for the Library, or undo the declaration. */
@Composable
private fun FinishedActions(actions: ReaderActions, onShowUnfinished: () -> Unit) {
    val texts = LocalAppTexts.current
    Button(
        onClick = { actions.onBack?.invoke() },
        modifier = Modifier
            .handCursor()
            .testTag(READER_BACK_TO_LIBRARY_TAG)
            .padding(top = LektoSpacing.BetweenSections),
    ) {
        Text(texts[TextKey.BACK_TO_LIBRARY])
    }
    TextButton(
        onClick = {
            onShowUnfinished()
            actions.onMarkUnfinished()
        },
        modifier = Modifier.handCursor().testTag(READER_MARK_UNFINISHED_TAG),
    ) {
        Text(texts[TextKey.MARK_AS_UNFINISHED])
    }
}

/** The unmarked face's actions: declare the book finished, or leave it as it is. */
@Composable
private fun UnfinishedActions(actions: ReaderActions, onShowFinished: () -> Unit) {
    val texts = LocalAppTexts.current
    Button(
        onClick = {
            onShowFinished()
            actions.onMarkFinished()
        },
        modifier = Modifier
            .handCursor()
            .testTag(READER_MARK_FINISHED_TAG)
            .padding(top = LektoSpacing.BetweenSections),
    ) {
        Text(texts[TextKey.MARK_AS_FINISHED])
    }
    TextButton(
        onClick = { actions.onBack?.invoke() },
        modifier = Modifier.handCursor().testTag(READER_BACK_TO_LIBRARY_TAG),
    ) {
        Text(texts[TextKey.BACK_TO_LIBRARY])
    }
}

/** The marked face's check and the word `Finished`, the declaration's visible receipt. */
@Composable
private fun FinishedMark() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        modifier = Modifier.padding(top = LektoSpacing.BetweenSections),
    ) {
        Icon(
            imageVector = LektoIcons.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FINISHED_CHECK_SIZE),
        )
        Text(
            text = LocalAppTexts.current[TextKey.READING_STATE_FINISHED],
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** `THE END` is the interface's small-caps line: its letter spacing is what makes it read as one. */
private val END_OF_BOOK_LETTER_SPACING = 2.sp

/** The check on the end state, the same 16 dp mark the Library row carries. */
private val FINISHED_CHECK_SIZE = 16.dp
