package app.lekto.library

import androidx.compose.runtime.Composable
import app.lekto.core.book.Book
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.ConfirmationDialog

/**
 * The delete confirmation, in the reader's app language (docs/design.md §8;
 * issue #131). The file name identifies the imported file, which is not on the
 * row any more (§7.1); it is the one place besides an import error where the
 * file is named.
 */
@Composable
internal fun BookDeleteDialog(target: Book, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val texts = LocalAppTexts.current
    ConfirmationDialog(
        title = texts.message(AppMessage.of(TextKey.DELETE_BOOK_TITLE, target.title)),
        message = texts.message(AppMessage.of(TextKey.DELETE_BOOK_MESSAGE, target.fileName)),
        confirmLabel = texts[TextKey.DELETE],
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * The confirmation that removes the **Sample book** (issue #278; docs/design.md
 * §7.1, §8): it names the sample, says its files go, and promises the reader's
 * own books and words stay. Like the book's, it only asks until it is confirmed.
 */
@Composable
internal fun SampleDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val texts = LocalAppTexts.current
    ConfirmationDialog(
        title = texts[TextKey.SAMPLE_DELETE_TITLE],
        message = texts[TextKey.SAMPLE_DELETE_MESSAGE],
        confirmLabel = texts[TextKey.REMOVE],
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
