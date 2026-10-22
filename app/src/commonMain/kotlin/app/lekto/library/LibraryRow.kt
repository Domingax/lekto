package app.lekto.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.lekto.core.text.baseLanguage
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LanguageHues
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.contentColorOn
import app.lekto.theme.hoverWash
import app.lekto.ui.handCursor
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Trash
import kotlinx.coroutines.launch

/** The test tag on a book's delete action, so a UI test can click exactly one. */
internal fun libraryDeleteTag(bookId: String): String = "library-delete-$bookId"

/** The test tag on a row's progress rule, so a UI test can read exactly one book's percentage. */
internal fun libraryProgressTag(bookId: String): String = "library-progress-$bookId"

/** The test tag on a row's frame, so a UI test can swipe or measure exactly one row. */
internal fun libraryRowTag(bookId: String): String = "library-row-$bookId"

/** The rule's thickness: 3 dp (docs/design.md §7.1). */
internal val PROGRESS_RULE_HEIGHT = 3.dp

/** The language vignette's frame: 44 × 60 dp, the place a cover would go (§7.1). */
internal val LANGUAGE_VIGNETTE_WIDTH = 44.dp
internal val LANGUAGE_VIGNETTE_HEIGHT = 60.dp

/** What a row can do: open its book, or delete it. Bundled so a row is two values, not a parameter list. */
internal data class BookRowActions(val onOpen: (Book) -> Unit, val onDelete: (Book) -> Unit)

/** The delete action's icon size: 18 dp, the size the identity uses inside a control (§5). */
private val DELETE_ICON_SIZE = 18.dp

/**
 * A click with no Material indication. The identity's §8 is explicit: every
 * clickable surface shows the pointing hand on desktop, and **hover changes
 * nothing else** — so a row does not wash grey under the mouse, and a whole row
 * does not flash a ripple. The hand cursor is the affordance.
 */
internal fun Modifier.plainClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, enabled = enabled, role = role, onClick = onClick)

/**
 * One library row: the frame §7.1 gives it (issue #208) — the **Reading
 * language**'s vignette, the title beside it, its **Reading progress** line and
 * rule — the tap that opens it and the way to delete it (issue #196).
 *
 * There are no covers: a **Book** has no cover image in its record, and a shelf
 * with covers for some books and none for others reads as broken. The vignette is
 * where a cover would go if one ever exists, so its colour is decoration and its
 * two-letter code is what carries the language.
 *
 * **Delete never sits on the shelf as a red word.** A destructive action on
 * every row is noise, and the identity reserves red for the moment it is used.
 * Each platform gets its own idiom: a swipe from the trailing edge on a touch
 * screen (Material's list pattern, and the one a thumb finds), and a trash that
 * appears when the row is hovered for a mouse. Both open the same confirmation,
 * because there is no undo (§8) — no full swipe deletes anything by itself. The
 * row also carries the action as a `customActions` entry, so a screen reader can
 * reach it without the gesture.
 *
 * The file name is deliberately gone from the row: it identifies a file, not a
 * book, so it survives only in import errors and the delete confirmation.
 */
@Composable
internal fun BookRow(book: Book, progress: BookProgress, actions: BookRowActions) {
    // A drag is the touch idiom and only that: with a pointer, the delete waits
    // for the row's hover instead (issue #208 review).
    if (!swipeToDelete) {
        BookRowFrame(book, progress, actions)
        return
    }
    val dismiss = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = dismiss,
        modifier = Modifier.fillMaxWidth(),
        // A swipe only ever asks: deleting is irreversible, so the gesture opens
        // the confirmation and the row goes back where it was.
        enableDismissFromStartToEnd = false,
        onDismiss = {
            actions.onDelete(book)
            scope.launch { dismiss.reset() }
        },
        backgroundContent = { SwipeDeleteBackground() },
    ) {
        BookRowFrame(book, progress, actions)
    }
}

/** What a swipe uncovers: the theme's red, the trash and the word it stands for. */
@Composable
private fun SwipeDeleteBackground() {
    val theme = MaterialTheme.colorScheme
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.error, LektoRadii.Control)
            .padding(horizontal = LektoSpacing.InsidePanel),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LektoIcons.Trash,
            contentDescription = null,
            tint = contentColorOn(theme.error),
            modifier = Modifier.size(DELETE_ICON_SIZE),
        )
        Spacer(Modifier.width(LektoSpacing.BetweenRelatedLines))
        Text(texts[TextKey.DELETE], color = contentColorOn(theme.error), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The row itself: the vignette, the title and its progress line, and the delete the mouse uncovers. */
@Composable
private fun BookRowFrame(book: Book, progress: BookProgress, actions: BookRowActions) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = MaterialTheme.colorScheme
    // The row paints the paper over the swipe's red, so the action stays hidden
    // until the reader uncovers it. The hover wash is drawn over the paper, not
    // under it, so its rounded corners cannot reveal that red.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background)
            .background(
                if (hovered) hoverWash(theme.onBackground) else Color.Transparent,
                LektoRadii.Control,
            )
            .padding(vertical = LektoSpacing.BetweenRows)
            .hoverable(interaction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookFrame(book, progress, actions, modifier = Modifier.weight(1f))
        // The action's place is reserved, so revealing it never shifts the title.
        Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
            if (hovered) DeleteIcon(book, actions.onDelete)
        }
    }
}

/**
 * The tappable frame: the vignette, the title and its two lines. It carries the
 * delete as an accessibility action too, because a screen reader cannot swipe a
 * row the way a thumb does.
 */
@Composable
private fun BookFrame(book: Book, progress: BookProgress, actions: BookRowActions, modifier: Modifier) {
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
            ProgressLine(book, progress)
        }
    }
}

/** The trash a mouse uncovers at the row's end: the desktop idiom for a row action. */
@Composable
private fun DeleteIcon(book: Book, onDelete: (Book) -> Unit) {
    Box(
        modifier = Modifier
            .size(LektoSpacing.TouchTarget)
            .handCursor()
            .testTag(libraryDeleteTag(book.id))
            .plainClickable(role = Role.Button) { onDelete(book) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = LektoIcons.Trash,
            contentDescription = LocalAppTexts.current[TextKey.DELETE],
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(DELETE_ICON_SIZE),
        )
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
