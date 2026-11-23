package app.lekto.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.core.book.Book
import app.lekto.core.book.SampleBookRow
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing

/** The test tag on the first-run sentence: the vault holds no book and the sample leads the shelf (issue #278). */
internal const val LIBRARY_FIRST_RUN_TAG: String = "library-first-run"

/** The test tag on the one line a failed sample materialisation leaves (issue #278). */
internal const val LIBRARY_SAMPLE_FAILED_TAG: String = "library-sample-failed"

/** The space the first-run sentence keeps from the sample row under it. */
private val FIRST_RUN_GAP = LektoSpacing.BetweenRelatedLines

/**
 * The first-run sentence over the sample (issue #278; docs/design.md §7.1): what
 * to do when the vault holds no book and the **Sample book** is the shelf's first
 * row. It is the only sentence of that state — the sample is a row, not an empty
 * shelf, so §7.1's empty words never stand beside it.
 */
@Composable
internal fun FirstRunShelf() {
    Text(
        text = LocalAppTexts.current[TextKey.LIBRARY_FIRST_RUN],
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.testTag(LIBRARY_FIRST_RUN_TAG).padding(bottom = FIRST_RUN_GAP),
    )
}

/**
 * The **Sample book**'s row (issue #278): the shared book frame with its mark, no
 * cover lookup — a sample has none, and its identity is not a vault record id —
 * and the removal that asks once before anything is deleted.
 */
@Composable
internal fun SampleShelfRow(row: SampleBookRow, onOpen: (Book) -> Unit, onDelete: () -> Unit) {
    BookRow(
        book = row.book,
        progress = row.progress,
        state = row.state,
        actions = BookRowActions(onOpen = onOpen, onDelete = { onDelete() }),
        covers = BookCoverLoader.None,
        sample = true,
    )
}

/**
 * The one line a failed sample materialisation leaves (issue #278): the shelf
 * stays in its empty state under it, never a row that points at files which were
 * not written. It is a plain line, not a bordered error: there is nothing for the
 * reader to dismiss or retry from here.
 */
@Composable
internal fun SampleFailure(line: String) {
    Text(
        text = line,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = LektoSpacing.InsidePanel).testTag(LIBRARY_SAMPLE_FAILED_TAG),
    )
}
