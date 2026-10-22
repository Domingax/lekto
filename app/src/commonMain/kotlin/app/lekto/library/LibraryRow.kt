package app.lekto.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookProgress
import app.lekto.core.text.baseLanguage
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LanguageHues
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.contentColorOn
import app.lekto.ui.handCursor

/** The test tag on a book's delete action, so a UI test can click exactly one. */
internal fun libraryDeleteTag(bookId: String): String = "library-delete-$bookId"

/** The test tag on a row's progress rule, so a UI test can read exactly one book's percentage. */
internal fun libraryProgressTag(bookId: String): String = "library-progress-$bookId"

/** The test tag on a row's frame, so a UI test can measure its touch target. */
internal fun libraryRowTag(bookId: String): String = "library-row-$bookId"

/** The rule's thickness: 3 dp (docs/design.md §7.1). */
internal val PROGRESS_RULE_HEIGHT = 3.dp

/** The language vignette's frame: 44 × 60 dp, the place a cover would go (§7.1). */
internal val LANGUAGE_VIGNETTE_WIDTH = 44.dp
internal val LANGUAGE_VIGNETTE_HEIGHT = 60.dp

/** The hairline that parts two rows, and the skeleton's own answer to a row (§4: no shadows). */
internal val HAIRLINE_THICKNESS = 1.dp

/**
 * One library row: the frame §7.1 gives it (issue #208) — the **Reading
 * language**'s vignette, the title beside it, its **Reading progress** line and
 * rule — the tap that opens it and the delete action (issue #196).
 *
 * There are no covers: a **Book** has no cover image in its record, and a shelf
 * with covers for some books and none for others reads as broken. The vignette is
 * where a cover would go if one ever exists, so its colour is decoration and its
 * two-letter code is what carries the language.
 *
 * The file name is deliberately gone from the row: it identifies a file, not a
 * book, so it survives only in import errors and the delete confirmation.
 */
@Composable
internal fun BookRow(book: Book, progress: BookProgress, onOpen: (Book) -> Unit, onDelete: (Book) -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LektoSpacing.BetweenRows),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = LektoSpacing.TouchTarget)
                .handCursor()
                .testTag(libraryRowTag(book.id))
                .clickable { onOpen(book) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageVignette(book.language)
            Spacer(Modifier.width(LektoSpacing.BetweenRows))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
            ) {
                Text(book.title, style = MaterialTheme.typography.titleSmall)
                ProgressLine(book, progress)
            }
        }
        TextButton(
            onClick = { onDelete(book) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.handCursor().testTag(libraryDeleteTag(book.id)),
        ) { Text(texts[TextKey.DELETE]) }
    }
}

/** The hairline that separates two rows: the theme's separator, one dp, no padding of its own. */
@Composable
internal fun RowHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HAIRLINE_THICKNESS)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
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

/** The row's second line: a measured percentage with its rule, or one of the two unmeasured states. */
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
