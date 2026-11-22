package app.lekto.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookProgress
import app.lekto.core.book.ReadingState
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.DeleteAction
import app.lekto.ui.DeletingRow
import app.lekto.ui.handCursor
import app.lekto.ui.icons.Check
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.plainClickable

/** The test tag on a book's delete action, so a UI test can click exactly one. */
internal fun libraryDeleteTag(bookId: String): String = "library-delete-$bookId"

/** The test tag on a row's progress rule, so a UI test can read exactly one book's percentage. */
internal fun libraryProgressTag(bookId: String): String = "library-progress-$bookId"

/** The test tag on a row's frame, so a UI test can swipe or measure exactly one row. */
internal fun libraryRowTag(bookId: String): String = "library-row-$bookId"

/** The test tag on a book's cover, so a UI test can tell art from the language plaque. */
internal fun libraryCoverTag(bookId: String): String = "library-cover-$bookId"

/** The rule's thickness: 3 dp (docs/design.md §7.1). */
internal val PROGRESS_RULE_HEIGHT = 3.dp

/** The finished line's check: the row's own small mark, not a control (docs/design.md §7.1). */
private val FINISHED_CHECK_SIZE = 16.dp

/** What a row can do: open its book, or delete it. Bundled so a row is two values, not a parameter list. */
internal data class BookRowActions(val onOpen: (Book) -> Unit, val onDelete: (Book) -> Unit)

/**
 * One library row: the frame §7.1 gives it (issue #208) — the **Reading
 * language**'s vignette, the title beside it, its second line (the **Author**
 * then the Reading state: the progress and rule, or `Finished` with a check) —
 * the tap that opens it and the way to delete it (issues #196, #200, #287).
 *
 * A book's art is shown here (issue #259): an EPUB's cover is extracted at import
 * as a **Derived asset**, decoded off the main thread and painted in the
 * vignette's place. The typographic plaque is the **mandatory fallback** — a TXT
 * never carries art, and an EPUB with no usable cover keeps its language code —
 * so a row is never a hole. A covered book still announces its **Reading
 * language** to a screen reader, because the cover carries the code the plaque
 * used to show.
 *
 * **Delete never sits on the shelf as a red word.** The row's delete is the
 * shared gesture every list row uses ([DeletingRow], issue #212): a swipe from
 * the trailing edge on a touch screen, a trash in the row's wash on hover with a
 * mouse. Both open the confirmation, because there is no undo (§8) — no full
 * swipe deletes anything by itself. The row also carries the action as a
 * `customActions` entry, so a screen reader can reach it without the gesture.
 *
 * The file name is deliberately gone from the row: it identifies a file, not a
 * book, so it survives only in import errors and the delete confirmation.
 *
 * The **Sample book** is the same frame with one addition (issue #278): a small
 * `Sample` mark beside its title, its language plaque (never a cover it cannot
 * have) and a delete that asks once before the sample is removed.
 *
 * @param covers where this row's cover comes from; the sample row passes
 *   [BookCoverLoader.None], because a sample has no cover and its identity is not
 *   a vault record id a cover could be keyed by.
 * @param sample whether this is the **Sample book**, which earns the mark.
 */
@Composable
@Suppress("LongParameterList") // The row's facts, its actions, where its cover comes from and its mark are independent.
internal fun BookRow(
    book: Book,
    progress: BookProgress,
    state: ReadingState,
    actions: BookRowActions,
    covers: BookCoverLoader = LocalBookCovers.current,
    sample: Boolean = false,
) {
    DeletingRow(DeleteAction(tag = libraryDeleteTag(book.id), onDelete = { actions.onDelete(book) })) { content ->
        BookFrame(book, progress, state, actions, covers, sample, modifier = content)
    }
}

/**
 * The tappable frame: the vignette, the title and its two lines. It carries the
 * delete as an accessibility action too, because a screen reader cannot swipe a
 * row the way a thumb does.
 */
@Suppress("LongParameterList") // The row's book, its facts, its actions, its cover source and its mark are independent.
@Composable
private fun BookFrame(
    book: Book,
    progress: BookProgress,
    state: ReadingState,
    actions: BookRowActions,
    covers: BookCoverLoader,
    sample: Boolean,
    modifier: Modifier,
) {
    val texts = LocalAppTexts.current
    Row(
        modifier = modifier
            .heightIn(min = LektoSpacing.TouchTarget)
            .handCursor()
            .testTag(libraryRowTag(book.id))
            .plainClickable(role = Role.Button) { actions.onOpen(book) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(texts[TextKey.DELETE]) {
                        actions.onDelete(book)
                        true
                    },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = rememberCover(book.id, covers)
        BookPlate(
            language = book.language,
            cover = cover,
            modifier = if (cover != null) Modifier.testTag(libraryCoverTag(book.id)) else Modifier,
        )
        Spacer(Modifier.width(LektoSpacing.BetweenRows))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        ) {
            BookTitle(book, sample)
            StateLine(book, progress, state)
        }
    }
}

/** The row's title, with the **Sample book**'s mark beside it when this is the sample (issue #278). */
@Composable
private fun BookTitle(book: Book, sample: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (sample) {
            Spacer(Modifier.width(LektoSpacing.BetweenRelatedLines))
            SampleMark(book.id)
        }
    }
}

/**
 * The row's second line: the **Author** then the Reading state —
 * `Author · Finished` with a check and no rule, `Author · 28 %` with its rule, or
 * one of the two unmeasured states (docs/design.md §7.1; issues #196, #200,
 * #287). A book that names no author reads the state alone, with no leading
 * separator and no placeholder word. A declaration is not a percentage, so a
 * finished book never shows the progress rule (ADR-0038).
 */
@Composable
private fun StateLine(book: Book, progress: BookProgress, state: ReadingState) {
    if (state == ReadingState.FINISHED) {
        FinishedLine(book)
    } else {
        ProgressLine(book, progress)
    }
}

/** The pair of texts a row line can read: prefixed by the **Author**, and the state alone. */
private data class RowLineKeys(val alone: TextKey, val withAuthor: TextKey)

private val PROGRESS_LINE = RowLineKeys(TextKey.LIBRARY_ROW_PROGRESS, TextKey.LIBRARY_ROW_PROGRESS_WITH_AUTHOR)
private val NOT_STARTED_LINE = RowLineKeys(TextKey.LIBRARY_ROW_NOT_STARTED, TextKey.LIBRARY_ROW_NOT_STARTED_WITH_AUTHOR)
private val MEASURING_LINE = RowLineKeys(TextKey.LIBRARY_ROW_MEASURING, TextKey.LIBRARY_ROW_MEASURING_WITH_AUTHOR)
private val FINISHED_LINE = RowLineKeys(TextKey.LIBRARY_ROW_FINISHED, TextKey.LIBRARY_ROW_FINISHED_WITH_AUTHOR)

/** The finished book's line: the check marks the declaration, and there is no rule under it. */
@Composable
private fun FinishedLine(book: Book) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = LektoIcons.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FINISHED_CHECK_SIZE),
        )
        Spacer(Modifier.width(LektoSpacing.InsideLabelPair))
        RowLineText(book, FINISHED_LINE)
    }
}

/** The row's second line when the book is not finished: a percentage with its rule, or an unmeasured state. */
@Composable
private fun ProgressLine(book: Book, progress: BookProgress) {
    when (progress) {
        BookProgress.NotStarted -> RowLineText(book, NOT_STARTED_LINE)

        BookProgress.Unmeasured -> {
            val measuring = LocalAppTexts.current[TextKey.LIBRARY_ROW_MEASURING_DESCRIPTION]
            RowLineText(
                book,
                MEASURING_LINE,
                // `…` alone is not something a screen reader can read; the row says
                // what the data is waiting for until the background measurement lands.
                modifier = Modifier.semantics { contentDescription = measuring },
            )
        }

        is BookProgress.Measured -> {
            RowLineText(book, PROGRESS_LINE, progress.percent)
            ProgressRule(book.id, progress.percent)
        }
    }
}

/** One line of ink in the row's second line: the **Author** then the state, or the state alone (issue #287). */
@Composable
private fun RowLineText(book: Book, keys: RowLineKeys, vararg values: Any?, modifier: Modifier = Modifier) {
    Text(
        text = LocalAppTexts.current.message(rowLineMessage(book, keys, *values)),
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/**
 * The line's message: the **Author** when the book names one, then the state
 * ([RowLineKeys.withAuthor]); the state alone ([RowLineKeys.alone]) otherwise, so
 * no separator leads a line that has nothing to separate (issue #287).
 */
private fun rowLineMessage(book: Book, keys: RowLineKeys, vararg values: Any?): AppMessage {
    val author = book.author?.takeIf { name -> name.isNotBlank() }
    return if (author == null) {
        AppMessage.of(keys.alone, *values)
    } else {
        AppMessage.of(keys.withAuthor, author, *values)
    }
}

/**
 * The 3 dp rule under a measured line: it fills to the same whole percentage as
 * the text beside it (docs/design.md §7.1). It carries a [ProgressBarRangeInfo],
 * so a screen reader announces the percentage and a semantics test can read it.
 */
@Composable
internal fun ProgressRule(bookId: String, percent: Int) {
    val fraction = percent.coerceIn(0, 100) / 100f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.InsideLabelPair)
            .height(PROGRESS_RULE_HEIGHT)
            .testTag(libraryProgressTag(bookId))
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(PROGRESS_RULE_HEIGHT)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
