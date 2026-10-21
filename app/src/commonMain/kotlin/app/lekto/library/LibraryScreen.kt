package app.lekto.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookProgress
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.ConfirmationDialog

/** The test tag on a book's delete action, so a UI test can click exactly one. */
internal fun libraryDeleteTag(bookId: String): String = "library-delete-$bookId"

/** The test tag on a row's progress rule, so a UI test can read exactly one book's percentage. */
internal fun libraryProgressTag(bookId: String): String = "library-progress-$bookId"

/** The rule's thickness: 3 dp (docs/design.md §7.1). */
private val PROGRESS_RULE_HEIGHT = 3.dp

/** The test tag on the Library's search field (issue #173). */
internal const val LIBRARY_SEARCH_TAG: String = "library-search"

/** The test tag on the action that empties the search field (issue #173). */
internal const val LIBRARY_SEARCH_CLEAR_TAG: String = "library-search-clear"

/** The lazy item key of the "Continue reading" heading, which is not a book. */
private const val CONTINUE_READING_KEY: String = "continue-reading"

/** The test tag on the Library's title, so a layout test can find it apart from the shell's bar. */
internal const val LIBRARY_TITLE_TAG: String = "library-title"

/**
 * The library screen: the books in the vault, a title search, an Import action, a
 * per-book delete behind a destructive confirmation, and the import's progress
 * and failure inline so neither blocks the list (issues #15, #131, #173; UX spec,
 * "Import in progress — inline progress bar", "AI/import error — inline message",
 * "Dialog").
 *
 * The screen renders [state] and raises the [actions]; the only state it owns is
 * the pending confirmation, which is view-local, so the vault is untouched until
 * the user confirms. The composition root (App) owns the rest and the file
 * picker, which keeps every behaviour testable through semantics alone.
 *
 * **Import** is the Library's action and never a section (issue #185;
 * docs/design.md §6, §7.1): a word beside the title once there is a shelf to add
 * to, and the filled action in the empty state, where it is the one thing to do.
 * The way to another section is the shell's bar, not this screen's header.
 */
@Composable
fun LibraryScreen(state: LibraryUiState, actions: LibraryActions = LibraryActions(), modifier: Modifier = Modifier) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    val texts = LocalAppTexts.current

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        LibraryHeader(vaultEmpty = state.vaultEmpty, importing = state.importing, onImport = actions.onImport)

        // A field over an empty vault would search nothing; the empty state keeps
        // its one sentence and its action (issue #173).
        if (!state.vaultEmpty) LibrarySearch(state.query, actions.onSearch, actions.onClearSearch)

        state.error?.let { message -> LibraryError(texts.message(message), actions.onDismissError) }
        if (state.importing) Importing(state.progress)

        if (state.vaultEmpty) {
            // Importing the first book shows the progress bar as the state; the
            // empty sentence would claim there is nothing to wait for.
            if (!state.importing) {
                Text(
                    text = texts[TextKey.LIBRARY_EMPTY],
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            Button(
                onClick = actions.onImport,
                enabled = !state.importing,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(texts[TextKey.IMPORT]) }
        } else if (state.visibleBooks.isEmpty()) {
            LibraryNoMatches(state.query, actions.onClearSearch)
        } else {
            LibraryList(state, onOpen = actions.onOpen, onDelete = { target -> pendingDelete = target })
        }
    }

    pendingDelete?.let { target ->
        ConfirmationDialog(
            title = texts.message(AppMessage.of(TextKey.DELETE_BOOK_TITLE, target.title)),
            // The file name identifies the imported file, which is not on the row
            // any more (docs/design.md §7.1).
            message = texts.message(AppMessage.of(TextKey.DELETE_BOOK_MESSAGE, target.fileName)),
            confirmLabel = texts[TextKey.DELETE],
            onConfirm = {
                pendingDelete = null
                actions.onDelete(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * The title search (issue #173): a labelled field under the title, and a clear
 * action once there is something to clear. The label is the field's accessible
 * name, so no placeholder carries it.
 */
@Composable
private fun LibrarySearch(query: String, onSearch: (String) -> Unit, onClearSearch: () -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            label = { Text(texts[TextKey.SEARCH]) },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag(LIBRARY_SEARCH_TAG),
        )
        if (query.isNotEmpty()) {
            // A cross rather than the words, so the field keeps its width in
            // French; the description is what a screen reader announces.
            TextButton(
                onClick = onClearSearch,
                modifier = Modifier
                    .testTag(LIBRARY_SEARCH_CLEAR_TAG)
                    .semantics { contentDescription = texts[TextKey.CLEAR_SEARCH] },
            ) { Text("×") }
        }
    }
}

/**
 * Why the search found nothing: the query is named back, and the reader can
 * clear it (issue #173). `Show all books` joins it with the reading-state filter
 * (#200).
 */
@Composable
private fun LibraryNoMatches(query: String, onClearSearch: () -> Unit) {
    val texts = LocalAppTexts.current
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            text = texts.message(AppMessage.of(TextKey.NO_BOOKS_MATCH_QUERY, query.trim())),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onClearSearch) { Text(texts[TextKey.CLEAR_SEARCH]) }
    }
}

/**
 * The scrollable book list: the recently read books under their heading, then
 * the rest (issue #188). A search flattens the two groups, so the heading only
 * shows while the reader is browsing (issue #173).
 */
@Composable
private fun ColumnScope.LibraryList(state: LibraryUiState, onOpen: (Book) -> Unit, onDelete: (Book) -> Unit) {
    val texts = LocalAppTexts.current
    LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
        if (!state.searching && state.listing.lastRead.isNotEmpty()) {
            item(key = CONTINUE_READING_KEY) {
                Text(
                    text = texts[TextKey.CONTINUE_READING],
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
        items(state.visibleBooks, key = { book -> book.id }) { book ->
            BookRow(book, state.listing.progressOf(book), onOpen = onOpen, onDelete = onDelete)
        }
    }
}

/**
 * The library's chrome: its title, and Import as a word beside it once there is a
 * shelf to add to (docs/design.md §6, §7.1). A **FlowRow**, not a **Row**: the
 * French title ("Bibliothèque") is wider than the English one, so on a phone the
 * action wraps to its own line rather than being squeezed.
 */
@OptIn(ExperimentalLayoutApi::class) // `FlowRow` wraps an action below a title too wide to share the line.
@Composable
private fun LibraryHeader(vaultEmpty: Boolean, importing: Boolean, onImport: () -> Unit) {
    val texts = LocalAppTexts.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = texts[TextKey.LIBRARY],
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(LIBRARY_TITLE_TAG),
        )
        // An empty shelf's action is the filled button in the empty state, so the
        // header does not repeat it; while a transfer runs, the word is disabled
        // rather than making a second one.
        if (!vaultEmpty) {
            TextButton(onClick = onImport, enabled = !importing) { Text(texts[TextKey.IMPORT]) }
        }
    }
}

/**
 * One library row: the title, its **Reading progress** line and rule, the tap that
 * opens it and the delete action (docs/design.md §7.1; issue #196).
 *
 * The file name is deliberately gone from the row: it identifies a file, not a
 * book, so it survives only in import errors and the delete confirmation.
 */
@Composable
private fun BookRow(book: Book, progress: BookProgress, onOpen: (Book) -> Unit, onDelete: (Book) -> Unit) {
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
            Text(book.title, style = MaterialTheme.typography.titleSmall)
            ProgressLine(book, progress)
        }
        TextButton(
            onClick = { onDelete(book) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag(libraryDeleteTag(book.id)),
        ) { Text(texts[TextKey.DELETE]) }
    }
}

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
private fun ProgressRule(bookId: String, percent: Int) {
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
