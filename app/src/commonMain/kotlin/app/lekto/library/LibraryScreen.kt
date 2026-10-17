package app.lekto.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.ConfirmationDialog

/** The test tag on a book's delete action, so a UI test can click exactly one. */
internal fun libraryDeleteTag(bookId: String): String = "library-delete-$bookId"

/**
 * The library screen: the books in the vault, an Import action, a per-book delete
 * behind a destructive confirmation, and the import's progress and failure inline
 * so neither blocks the list (issues #15, #131; UX spec, "Import in progress —
 * inline progress bar", "AI/import error — inline message", "Dialog").
 *
 * The screen renders [state] and raises the [actions]; the only state it owns is
 * the pending confirmation, which is view-local, so the vault is untouched until
 * the user confirms. The composition root (App) owns the rest and the file
 * picker, which keeps every behaviour testable through semantics alone.
 */
@Composable
fun LibraryScreen(state: LibraryUiState, actions: LibraryActions = LibraryActions(), modifier: Modifier = Modifier) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    val texts = LocalAppTexts.current

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        LibraryHeader(actions)

        state.error?.let { message -> LibraryError(texts.message(message), actions.onDismissError) }
        if (state.importing) Importing(state.progress)

        if (state.books.isEmpty() && !state.importing) {
            Text(
                text = texts[TextKey.LIBRARY_EMPTY],
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                items(state.books, key = { book -> book.id }) { book ->
                    BookRow(book, onOpen = actions.onOpen, onDelete = { target -> pendingDelete = target })
                }
            }
        }

        Button(onClick = actions.onImport, enabled = !state.importing, modifier = Modifier.fillMaxWidth()) {
            Text(texts[TextKey.IMPORT])
        }
    }

    pendingDelete?.let { target ->
        ConfirmationDialog(
            title = texts.message(AppMessage.of(TextKey.DELETE_BOOK_TITLE, target.title)),
            message = texts[TextKey.DELETE_BOOK_MESSAGE],
            confirmLabel = texts[TextKey.DELETE],
            onConfirm = {
                pendingDelete = null
                actions.onDelete(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** The library's chrome: its title and the way to the vocabulary list and settings. */
@OptIn(ExperimentalLayoutApi::class) // `FlowRow` wraps the navigation below a title too wide to share the line.
@Composable
private fun LibraryHeader(actions: LibraryActions) {
    val texts = LocalAppTexts.current
    // A **FlowRow**, not a **Row**: the French title ("Bibliothèque") is wider than
    // the English one, so on a phone the navigation cannot share its line. It wraps
    // to its own line rather than being squeezed into a one-character column.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texts[TextKey.LIBRARY], style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = actions.onOpenLanguages) { Text(texts[TextKey.LANGUAGES]) }
            TextButton(onClick = actions.onOpenVocabulary) { Text(texts[TextKey.VOCABULARY]) }
            TextButton(onClick = actions.onOpenSettings) { Text(texts[TextKey.SETTINGS]) }
        }
    }
}

/** One library row: the title, the format, the tap that opens it and the delete action. */
@Composable
private fun BookRow(book: Book, onOpen: (Book) -> Unit, onDelete: (Book) -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable { onOpen(book) },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${book.format.name} · ${book.fileName}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(
            onClick = { onDelete(book) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag(libraryDeleteTag(book.id)),
        ) { Text(texts[TextKey.DELETE]) }
    }
}

/** The inline library failure — an import or a delete — with a way to dismiss it. */
@Composable
private fun LibraryError(message: String, onDismiss: () -> Unit) {
    val texts = LocalAppTexts.current
    val described = texts.message(AppMessage.of(TextKey.ERROR_DETAIL, message))
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = described },
        )
        TextButton(onClick = onDismiss) { Text(texts[TextKey.DISMISS]) }
    }
}

/** The inline import progress, determinate so it reports how far along it is. */
@Composable
private fun Importing(fraction: Float) {
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = texts[TextKey.IMPORTING],
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .semantics { contentDescription = texts[TextKey.IMPORT_PROGRESS] },
        )
    }
}
