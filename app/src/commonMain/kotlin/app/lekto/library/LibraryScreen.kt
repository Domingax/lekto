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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book

/**
 * The library screen: the books in the vault, an Import action, and the import's
 * progress and failure inline so neither blocks the list (issue #15; UX spec,
 * "Import in progress — inline progress bar", "AI/import error — inline message").
 *
 * The screen is deliberately stateless: it renders [state] and raises the
 * [actions]. The composition root (App) owns the state and the file picker,
 * which keeps this testable through semantics alone.
 */
@Composable
fun LibraryScreen(state: LibraryUiState, actions: LibraryActions = LibraryActions(), modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = actions.onOpenSettings) { Text("Settings") }
        }

        state.error?.let { message -> ImportError(message, actions.onDismissError) }
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
                    BookRow(book, actions.onOpen)
                }
            }
        }

        Button(onClick = actions.onImport, enabled = !state.importing, modifier = Modifier.fillMaxWidth()) {
            Text("Import")
        }
    }
}

/** One library row: the title, the format, and the tap that opens it. */
@Composable
private fun BookRow(book: Book, onOpen: (Book) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(book) }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(book.title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${book.format.name} · ${book.fileName}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The inline import failure: the reason and a way to dismiss it. */
@Composable
private fun ImportError(message: String, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = "Import failed: $message" },
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
