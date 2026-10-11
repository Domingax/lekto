package app.lekto.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        LibraryHeader(actions)

        state.error?.let { message -> LibraryError(message, actions.onDismissError) }
        if (state.importing) Importing(state.progress)

        if (state.books.isEmpty() && !state.importing) {
            Text(
                text = "No books yet — tap Import to add your first book",
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
            Text("Import")
        }
    }

    pendingDelete?.let { target ->
        ConfirmationDialog(
            title = "Delete « ${target.title} »?",
            message = "This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDelete = null
                actions.onDelete(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** The library's chrome: its title and the way to the vocabulary list and settings. */
@Composable
private fun LibraryHeader(actions: LibraryActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = actions.onOpenVocabulary) { Text("Vocabulary") }
            TextButton(onClick = actions.onOpenSettings) { Text("Settings") }
        }
    }
}

/** One library row: the title, the format, the tap that opens it and the delete action. */
@Composable
private fun BookRow(book: Book, onOpen: (Book) -> Unit, onDelete: (Book) -> Unit) {
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
        ) { Text("Delete") }
    }
}

/** The inline library failure — an import or a delete — with a way to dismiss it. */
@Composable
private fun LibraryError(message: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = "Error: $message" },
        )
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

/** The inline import progress, determinate so it reports how far along it is. */
@Composable
private fun Importing(fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = "Importing…",
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .semantics { contentDescription = "Import progress" },
        )
    }
}
