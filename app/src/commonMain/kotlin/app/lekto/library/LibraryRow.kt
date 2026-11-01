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
import app.lekto.core.text.baseLanguage
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LanguageHues
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.contentColorOn
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

/** The rule's thickness: 3 dp (docs/design.md §7.1). */
internal val PROGRESS_RULE_HEIGHT = 3.dp

/** The finished line's check: the row's own small mark, not a control (docs/design.md §7.1). */
private val FINISHED_CHECK_SIZE = 16.dp

/** The language vignette's frame: 44 × 60 dp, the place a cover would go (§7.1). */
internal val LANGUAGE_VIGNETTE_WIDTH = 44.dp
internal val LANGUAGE_VIGNETTE_HEIGHT = 60.dp

/** What a row can do: open its book, or delete it. Bundled so a row is two values, not a parameter list. */
internal data class BookRowActions(val onOpen: (Book) -> Unit, val onDelete: (Book) -> Unit)

/**
 * One library row: the frame §7.1 gives it (issue #208) — the **Reading
 * language**'s vignette, the title beside it, its **Reading state** line (the
 * progress and rule, or `Finished` with a check) — the tap that opens it and the
 * way to delete it (issues #196, #200).
 *
 * There are no covers: a **Book** has no cover image in its record, and a shelf
 * with covers for some books and none for others reads as broken. The vignette is
 * where a cover would go if one ever exists, so its colour is decoration and its
 * two-letter code is what carries the language.
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
 */
@Composable
internal fun BookRow(book: Book, progress: BookProgress, state: ReadingState, actions: BookRowActions) {
    DeletingRow(DeleteAction(tag = libraryDeleteTag(book.id), onDelete = { actions.onDelete(book) })) { content ->
        BookFrame(book, progress, state, actions, modifier = content)
    }
}

/**
 * The tappable frame: the vignette, the title and its two lines. It carries the
 * delete as an accessibility action too, because a screen reader cannot swipe a
 * row the way a thumb does.
 */
@Suppress("LongParameterList") // The row's book, its two facts, its actions and the frame's modifier are independent.
@Composable
private fun BookFrame(
    book: Book,
    progress: BookProgress,
    state: ReadingState,
    actions: BookRowActions,
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
        LanguageVignette(book.language)
        Spacer(Modifier.width(LektoSpacing.BetweenRows))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        ) {
            Text(book.title, style = MaterialTheme.typography.titleSmall)
            StateLine(book, progress, state)
        }
    }
}

/**
 * The language vignette (docs/design.md §2.4, §7.1; issue #208): a 44 × 60 dp
 * control-shaped tile in the **Reading language**'s hue, carrying its two-letter
 * code. The code is the signal — a reader who cannot tell the hues apart reads
 * the letters — so the hue never has to carry a meaning on its own.
 */
@Composable
private fun LanguageVignette(language: String?) {
    val fill = LanguageHues.fill(language, MaterialTheme.colorScheme)
    Box(
        modifier = Modifier
            .width(LANGUAGE_VIGNETTE_WIDTH)
            .height(LANGUAGE_VIGNETTE_HEIGHT)
            .background(fill, LektoRadii.Control),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = languageCode(language),
            style = MaterialTheme.typography.labelMedium,
            // The hue is dark on the daylight papers and lifted on night's, so the
            // code reads whichever way the theme went.
            color = contentColorOn(fill),
        )
    }
}

/** A **Reading language**'s two-letter code: `en-GB` reads `EN`. */
internal fun languageCode(language: String?): String = baseLanguage(language.orEmpty()).take(2).uppercase()

/**
 * The row's second line: `FORMAT · Finished` with a check and no rule, or a
 * measured percentage with its rule, or one of the two unmeasured states
 * (docs/design.md §7.1; issues #196, #200). A declaration is not a percentage,
 * so a finished book never shows the progress rule (ADR-0038).
 */
@Composable
private fun StateLine(book: Book, progress: BookProgress, state: ReadingState) {
    if (state == ReadingState.FINISHED) {
        FinishedLine(book)
    } else {
        ProgressLine(book, progress)
    }
}

/** The finished book's line: the check marks the declaration, and there is no rule under it. */
@Composable
private fun FinishedLine(book: Book) {
    val texts = LocalAppTexts.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = LektoIcons.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FINISHED_CHECK_SIZE),
        )
        Spacer(Modifier.width(LektoSpacing.InsideLabelPair))
        Text(
            text = texts.message(AppMessage.of(TextKey.LIBRARY_ROW_FINISHED, book.format.name)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The row's second line when the book is not finished: a percentage with its rule, or an unmeasured state. */
@Composable
private fun ProgressLine(book: Book, progress: BookProgress) {
    val texts = LocalAppTexts.current
    val style = MaterialTheme.typography.bodySmall
    when (progress) {
        BookProgress.NotStarted -> Text(
            text = texts.message(AppMessage.of(TextKey.LIBRARY_ROW_NOT_STARTED, book.format.name)),
            style = style,
        )

        BookProgress.Unmeasured -> Text(
            text = texts.message(AppMessage.of(TextKey.LIBRARY_ROW_MEASURING, book.format.name)),
            style = style,
            // `…` alone is not something a screen reader can read; the row says
            // what the data is waiting for until the background measurement lands.
            modifier = Modifier.semantics {
                contentDescription = texts[TextKey.LIBRARY_ROW_MEASURING_DESCRIPTION]
            },
        )

        is BookProgress.Measured -> {
            Text(
                text = texts.message(
                    AppMessage.of(TextKey.LIBRARY_ROW_PROGRESS, book.format.name, progress.percent),
                ),
                style = style,
            )
            ProgressRule(book.id, progress.percent)
        }
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
